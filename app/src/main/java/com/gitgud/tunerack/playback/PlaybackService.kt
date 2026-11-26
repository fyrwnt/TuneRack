package com.gitgud.tunerack.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.toMediaItem
import com.gitgud.tunerack.data.toTrack
import com.gitgud.tunerack.data.youtube.YouTubeMusic
import com.gitgud.tunerack.data.youtube.findUnplayable
import com.gitgud.tunerack.data.youtube.toPlaybackException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import com.gitgud.tunerack.tuneRack
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background playback. Media items carry `tunerack://yt/<videoId>`; the real googlevideo URL is
 * resolved on ExoPlayer's loader thread right before loading, so URLs are never stale in a queue.
 * Media3 posts and manages the media notification for this session.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordedCurrent = false

    /** Video ids already retried after an expired-URL error, so a dead stream can't loop forever. */
    private val retried = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()

        val http = OkHttpDataSource.Factory(tuneRack.httpClient)
            .setUserAgent(YouTubeMusic.streamUserAgent)
        // DefaultDataSource reads content:// (downloaded files) and hands http(s) to OkHttp.
        val upstream = DefaultDataSource.Factory(this, http)
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            if (spec.uri.scheme == SCHEME) {
                val id = requireNotNull(spec.uri.lastPathSegment)
                // A downloaded copy wins: plays offline and saves data.
                val local = tuneRack.downloads.localUriBlocking(id)
                val remote = if (local == null) {
                    try {
                        YouTubeMusic.resolveAudioUrl(id)
                    } catch (e: ExtractionException) {
                        throw e.toPlaybackException()
                    }
                } else {
                    null
                }
                spec.withUri(local ?: remote!!.toUri())
            } else {
                spec
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val item = player.currentMediaItem ?: return
                val id = item.mediaId
                val http = error.cause as? HttpDataSource.InvalidResponseCodeException
                // 403/410 usually means the cached stream URL expired: drop it and try once more.
                if (http != null && http.responseCode in setOf(403, 410) && retried.add(id)) {
                    YouTubeMusic.forget(id)
                    player.prepare()
                    player.play()
                    return
                }
                // A song YouTube won't serve (or a file that can't be decoded) shouldn't end the
                // queue: skip it, like Spotify does. Connection problems don't skip, since the next
                // song would fail the same way. Five failures in a row stop trying.
                val unplayable = error.findUnplayable()
                val broken = unplayable != null || error.errorCode in 3000..4999
                if (!broken || consecutiveSkips >= MAX_SKIPS || !player.hasNextMediaItem()) return
                consecutiveSkips++
                val title = item.mediaMetadata.title?.toString().orEmpty()
                announce("Skipped \u201c$title\u201d: ${unplayable?.reason ?: "couldn't be played"}")
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retried.clear()
                recordedCurrent = false
                saveQueue(player)
            }

            // History: count a song once it actually starts playing (not when merely queued or skipped past).
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveSkips = 0
                if (!isPlaying) saveQueue(player)
                val item = player.currentMediaItem
                if (!isPlaying || recordedCurrent || item == null) return
                recordedCurrent = true
                val track = item.toTrack()
                scope.launch { runCatching { tuneRack.library.recordPlay(track) } }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) applyBalancedShuffle(player, force = true)
                saveQueue(player)
            }

            override fun onRepeatModeChanged(repeatMode: Int) = saveQueue(player)

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
                if (player.shuffleModeEnabled && !insertingIntoShuffle) applyBalancedShuffle(player, force = false)
                saveQueue(player)
            }
        })

        restoreQueue(player)

        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Controllers can't send URIs, only ids: rebuild the internal URI here.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(mediaItems.map(::withStreamUri))

                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_PLAY_NEXT, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_PLAY_LAST, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    val next = when (customCommand.customAction) {
                        ACTION_PLAY_NEXT -> true
                        ACTION_PLAY_LAST -> false
                        else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }
                    enqueue(player, withStreamUri(trackFromBundle(args).toMediaItem()), next)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // Android's "resume" media controls after the app was closed: offer the saved queue.
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val saved = queueStore.load()
                        ?: return Futures.immediateFailedFuture(IllegalStateException("Nothing to resume"))
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(
                            saved.tracks.map { withStreamUri(it.toMediaItem()) },
                            saved.index,
                            saved.positionMs,
                        ),
                    )
                }
            })
            .build()
    }

    private var consecutiveSkips = 0

    /** Sends a short message to the app's UI (shown as a HUD), e.g. when a song is skipped. */
    private fun announce(message: String) {
        session?.broadcastCustomCommand(
            SessionCommand(EVENT_MESSAGE, Bundle.EMPTY),
            Bundle().apply { putString(KEY_MESSAGE, message) },
        )
    }

    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val queueStore by lazy { QueueStore(java.io.File(filesDir, "queue.json")) }

    private fun withStreamUri(item: MediaItem): MediaItem = item.buildUpon().setUri("$SCHEME://yt/${item.mediaId}").build()

    /** Reloads the last queue paused at its saved position; playback starts only when asked. */
    private fun restoreQueue(player: ExoPlayer) {
        val saved = queueStore.load() ?: return
        player.repeatMode = saved.repeatMode
        player.setMediaItems(saved.tracks.map { withStreamUri(it.toMediaItem()) }, saved.index, saved.positionMs)
        player.shuffleModeEnabled = saved.shuffle
        // Continue the exact same shuffle (including any Play Next placement) instead of a new one.
        saved.shuffleOrder?.let { order ->
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
            shuffledFor = saved.tracks.map { it.id }
        }
    }

    private fun saveQueue(player: Player) {
        val snapshot = QueueStore.Saved(
            tracks = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            shuffleOrder = if (player.shuffleModeEnabled) playOrder(player) else null,
        )
        // One writer at a time, in order, so an older snapshot can never overwrite a newer one.
        scope.launch(saveDispatcher) {
            runCatching { if (snapshot.tracks.isEmpty()) queueStore.clear() else queueStore.save(snapshot) }
        }
    }

    /** True while [enqueue] edits the playlist, so the timeline listener doesn't reshuffle. */
    private var insertingIntoShuffle = false

    /**
     * Play Next / Play Last. With shuffle on, ExoPlayer would drop the new song at a random point of
     * the shuffle order; instead it goes right after the current song (or at the very end).
     */
    private fun enqueue(player: ExoPlayer, item: MediaItem, next: Boolean) {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(item)
            player.prepare()
            player.play()
            return
        }
        val current = player.currentMediaItemIndex
        val insertAt = if (next) current + 1 else player.mediaItemCount
        val order = if (player.shuffleModeEnabled) playOrder(player) else null
        insertingIntoShuffle = true
        try {
            player.addMediaItem(insertAt, item)
            if (order != null) {
                val shifted = order.map { if (it >= insertAt) it + 1 else it }
                val position = shifted.indexOf(current)
                val reordered = if (next) {
                    shifted.take(position + 1) + insertAt + shifted.drop(position + 1)
                } else {
                    shifted + insertAt
                }
                player.setShuffleOrder(DefaultShuffleOrder(reordered.toIntArray(), System.nanoTime()))
                shuffledFor = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
            }
        } finally {
            insertingIntoShuffle = false
        }
    }

    /** Every playlist index in the order shuffle will play them. */
    private fun playOrder(player: Player): List<Int> {
        val timeline = player.currentTimeline
        val order = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return order
    }

    /** Ids of the queue the current shuffle order was built for; avoids reshuffling in a loop. */
    private var shuffledFor: List<String> = emptyList()

    /**
     * Replaces ExoPlayer's random shuffle with [BalancedShuffle], keeping the current song first so
     * turning shuffle on never interrupts what is playing.
     */
    private fun applyBalancedShuffle(player: ExoPlayer, force: Boolean) {
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val ids = items.map { it.mediaId }
        if (!force && ids == shuffledFor) return
        shuffledFor = ids
        if (items.size < 2) return
        val order = BalancedShuffle.order(
            artists = items.map { it.mediaMetadata.artist?.toString().orEmpty() },
            first = player.currentMediaItemIndex.takeIf { it in items.indices },
        )
        player.setShuffleOrder(DefaultShuffleOrder(order, System.nanoTime()))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.player?.let { player ->
            // Final save with the exact position, written synchronously before the process may die.
            runCatching {
                queueStore.save(
                    QueueStore.Saved(
                        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() },
                        player.currentMediaItemIndex,
                        player.currentPosition,
                        player.shuffleModeEnabled,
                        player.repeatMode,
                        if (player.shuffleModeEnabled) playOrder(player) else null,
                    ),
                )
            }
        }
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        private const val SCHEME = "tunerack"
        const val ACTION_PLAY_NEXT = "tunerack.PLAY_NEXT"
        const val ACTION_PLAY_LAST = "tunerack.PLAY_LAST"
        const val EVENT_MESSAGE = "tunerack.MESSAGE"
        const val KEY_MESSAGE = "message"
        private const val MAX_SKIPS = 5

        fun trackBundle(track: Track) = Bundle().apply {
            putString("id", track.id)
            putString("title", track.title)
            putString("artist", track.artist)
            putLong("durationSeconds", track.durationSeconds)
            putString("artworkUrl", track.artworkUrl)
        }

        private fun trackFromBundle(bundle: Bundle) = Track(
            id = bundle.getString("id").orEmpty(),
            title = bundle.getString("title").orEmpty(),
            artist = bundle.getString("artist").orEmpty(),
            durationSeconds = bundle.getLong("durationSeconds"),
            artworkUrl = bundle.getString("artworkUrl"),
        )
    }
}
