package com.gitgud.tunerack.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.toMediaItem
import kotlinx.coroutines.delay
import com.gitgud.tunerack.data.youtube.findUnplayable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.SessionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.guava.await

data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSeconds: Long = 0,
) {
    fun toTrack() = Track(mediaId, title, artist, durationSeconds, artworkUrl)
}

/** One queue entry, with its index in the underlying (unshuffled) playlist. */
data class QueueEntry(val index: Int, val item: NowPlaying)

enum class RepeatMode { Off, All, One }

data class PlayerUiState(
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val hasNext: Boolean = false,
    /** Songs that will play after the current one, in actual play order (shuffle-aware). */
    val upNext: List<QueueEntry> = emptyList(),
)

data class Progress(val positionMs: Long, val durationMs: Long, val bufferedMs: Long)

/** The UI's handle on [PlaybackService]: a MediaController plus flows of what to show. */
class PlayerConnection(private val context: Context) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short notices from playback, such as a skipped unplayable song. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val connected = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onCustomCommand(
                    controller: MediaController,
                    command: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (command.customAction == PlaybackService.EVENT_MESSAGE) {
                        args.getString(PlaybackService.KEY_MESSAGE)?.let { _messages.tryEmit(it) }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .buildAsync()
            .await()
        connected.addListener(listener)
        controller = connected
        publish(connected)
    }

    fun release() {
        controller?.run {
            removeListener(listener)
            release()
        }
        controller = null
    }

    /** Plays [tracks] as the queue, starting at [startIndex] (Apple Music: tap a row, play the list). */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        val player = controller ?: return
        player.setMediaItems(tracks.map(Track::toMediaItem), startIndex, 0L)
        player.prepare()
        player.play()
    }

    /** Spotify's Shuffle button: shuffle on, start from a random song. */
    fun playShuffled(tracks: List<Track>) {
        val player = controller ?: return
        if (tracks.isEmpty()) return
        player.shuffleModeEnabled = true
        player.setMediaItems(tracks.map(Track::toMediaItem), tracks.indices.random(), 0L)
        player.prepare()
        player.play()
    }

    /** Plays [track] right after the current song, also when shuffled (handled in the service). */
    fun playNext(track: Track) = enqueue(track, PlaybackService.ACTION_PLAY_NEXT)

    /** Adds [track] to the end of the queue (the end of the shuffle order when shuffled). */
    fun playLast(track: Track) = enqueue(track, PlaybackService.ACTION_PLAY_LAST)

    private fun enqueue(track: Track, action: String) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return playQueue(listOf(track), 0)
        player.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), PlaybackService.trackBundle(track))
    }

    fun togglePlayPause() {
        val player = controller ?: return
        when {
            // Errored, or a restored queue that hasn't been prepared yet.
            player.playerError != null || player.playbackState == Player.STATE_IDLE -> { player.prepare(); player.play() }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    /** Like Apple Music: restarts the song if more than a few seconds in, otherwise goes back. */
    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun playQueueEntry(index: Int) {
        val player = controller ?: return
        player.seekToDefaultPosition(index)
        player.play()
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Position ticks for the now-playing screen; only runs while something collects it. */
    fun progress(): Flow<Progress> = flow {
        while (true) {
            controller?.let { player ->
                // Until a restored queue is prepared the player doesn't know the length yet; the
                // song's metadata does.
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                    ?: player.currentMediaItem?.mediaMetadata?.durationMs
                    ?: 0L
                emit(Progress(player.currentPosition, duration, player.bufferedPosition))
            }
            delay(250)
        }
    }

    private fun publish(player: Player) {
        _state.value = PlayerUiState(
            nowPlaying = player.currentMediaItem?.toNowPlaying(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            error = player.playerError?.let(::describe),
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                else -> RepeatMode.Off
            },
            hasNext = player.hasNextMediaItem(),
            upNext = upNext(player),
        )
    }

    private fun upNext(player: Player): List<QueueEntry> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val entries = mutableListOf<QueueEntry>()
        var index = player.currentMediaItemIndex
        while (true) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            entries += QueueEntry(index, player.getMediaItemAt(index).toNowPlaying())
        }
        return entries
    }

    private fun MediaItem.toNowPlaying() = NowPlaying(
        mediaId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artworkUrl = mediaMetadata.artworkUri?.toString(),
        durationSeconds = (mediaMetadata.durationMs ?: 0L) / 1000,
    )

    private fun describe(error: PlaybackException): String = error.findUnplayable()?.reason ?: when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "No connection"
        else -> "Couldn't play this song"
    }
}

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection> { error("No PlayerConnection") }
