package com.gitgud.tunerack.data.youtube

import com.gitgud.tunerack.data.settings.pickByBitrate
import android.util.Log
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.charts.KworbParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/** YouTube Music search and audio-stream lookup, both backed by NewPipe Extractor. */
object YouTubeMusic {

    /** User agent googlevideo expects for URLs issued to NewPipe's visionOS player client. */
    val streamUserAgent: String get() = YoutubeParsingHelper.getVisionOsUserAgent(null)

    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            "",
        )
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrack() }
    }

    private class CachedUrl(val url: String, val expiresAt: Long)

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()

    /**
     * Returns a direct audio URL for [videoId]. Blocking: called from ExoPlayer's loader thread.
     * URLs are cached until shortly before YouTube's `expire` time so seeks and replays are instant.
     */
    fun resolveAudioUrl(videoId: String, maxKbps: Int = Int.MAX_VALUE): String {
        // Cached per quality, so changing the setting takes effect from the next song on.
        val cacheKey = "$videoId@$maxKbps"
        urlCache[cacheKey]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.url }
        // The player and the next-song prefetch can ask at once: one lookup, the other waits for it.
        synchronized(lookupLocks.computeIfAbsent(cacheKey) { Any() }) {
            urlCache[cacheKey]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.url }
            return lookUpAudioUrl(videoId, maxKbps, cacheKey)
        }
    }

    private val lookupLocks = ConcurrentHashMap<String, Any>()

    private fun lookUpAudioUrl(videoId: String, maxKbps: Int, cacheKey: String): String {
        val started = System.currentTimeMillis()
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        val stream = pickByBitrate(
            originalAudio(extractor.audioStreams),
            maxKbps,
            kbps = { it.averageBitrate },
            isOpus = { it.format == MediaFormat.WEBMA_OPUS },
        ) ?: error("No playable audio stream for $videoId")
        Log.d(LOG_TAG, "Streaming $videoId at ${stream.averageBitrate} kbps ${stream.format?.name} (cap $maxKbps), lookup ${System.currentTimeMillis() - started} ms")

        val url = stream.content
        val expireSeconds = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        val expiresAt = expireSeconds?.let { it * 1000 - 10 * 60 * 1000 } ?: (System.currentTimeMillis() + 60 * 60 * 1000)
        urlCache[cacheKey] = CachedUrl(url, expiresAt)
        return url
    }

    /**
     * Songs like [videoId], for "Top Picks": the song's YouTube Music radio ("RDAMVM" mix), which
     * is music only. YouTube's general related videos also mix in news and vlogs.
     */
    suspend fun related(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/watch?v=$videoId&list=RDAMVM$videoId")
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 60..600 }
            .mapNotNull { it.toTrack() }
            .map { track ->
                // Related items are videos: "Artist - Song (Official Video)" -> artist + song.
                val (artist, title) = KworbParser.splitVideoTitle(track.title)
                if (artist.isEmpty()) track.copy(title = title) else track.copy(title = title, artist = artist)
            }
            .filter { it.id != videoId }
            .distinctBy { it.id }
    }

    class DownloadSource(val url: String, val mimeType: String, val extension: String)

    /**
     * Stream to save for offline use. Prefers AAC in M4A (plays in every music player and can be
     * tagged); falls back to the best other progressive audio stream. Blocking.
     */
    fun resolveDownload(videoId: String, maxKbps: Int = Int.MAX_VALUE): DownloadSource {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        val candidates = originalAudio(extractor.audioStreams)
        val stream = pickByBitrate(candidates.filter { it.format == MediaFormat.M4A }, maxKbps, { it.averageBitrate }, { false })
            ?: candidates.maxByOrNull { it.averageBitrate }
            ?: error("No downloadable audio for $videoId")
        Log.d(LOG_TAG, "Downloading $videoId at ${stream.averageBitrate} kbps ${stream.format?.name} (cap $maxKbps)")
        val format = stream.format ?: MediaFormat.M4A
        return DownloadSource(stream.content, format.mimeType, format.suffix)
    }

    fun forget(videoId: String) {
        urlCache.keys.removeAll { it.startsWith("$videoId@") }
    }

    /** Directly playable streams in the song's original language (dubbed tracks dropped if possible). */
    private fun originalAudio(streams: List<AudioStream>): List<AudioStream> {
        val playable = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        val original = playable.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        return original.ifEmpty { playable }
    }

    private const val LOG_TAG = "TuneRackAudio"

    private fun StreamInfoItem.toTrack(): Track? {
        val id = runCatching { YoutubeStreamLinkHandlerFactory.getInstance().getId(url) }.getOrNull() ?: return null
        return Track(
            id = id,
            title = name,
            artist = uploaderName.orEmpty(),
            durationSeconds = duration,
            artworkUrl = thumbnails.maxByOrNull { it.height }?.url?.let(::largeArtwork),
        )
    }

    /** YouTube Music art URLs carry a size suffix (`=w120-h120-...`); ask for a sharp square. */
    private fun largeArtwork(url: String): String =
        if (url.contains("googleusercontent.com")) url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
        else url
}
