package com.gitgud.tunerack.data.youtube

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
    fun resolveAudioUrl(videoId: String): String {
        urlCache[videoId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.url }

        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        val stream = extractor.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            // Prefer the original track over dubbed ones, then the best bitrate; Opus beats AAC at equal rate.
            .sortedWith(
                compareByDescending<AudioStream> { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
                    .thenByDescending { it.averageBitrate }
                    .thenByDescending { it.format == MediaFormat.WEBMA_OPUS },
            )
            .firstOrNull()
            ?: error("No playable audio stream for $videoId")

        val url = stream.content
        val expireSeconds = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        val expiresAt = expireSeconds?.let { it * 1000 - 10 * 60 * 1000 } ?: (System.currentTimeMillis() + 60 * 60 * 1000)
        urlCache[videoId] = CachedUrl(url, expiresAt)
        return url
    }

    /** Songs YouTube suggests next to [videoId], for "Top Picks". Skips long mixes and live streams. */
    suspend fun related(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        extractor.relatedItems?.items.orEmpty()
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
    fun resolveDownload(videoId: String): DownloadSource {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        val candidates = extractor.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        val stream = candidates.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: candidates.maxByOrNull { it.averageBitrate }
            ?: error("No downloadable audio for $videoId")
        val format = stream.format ?: MediaFormat.M4A
        return DownloadSource(stream.content, format.mimeType, format.suffix)
    }

    fun forget(videoId: String) {
        urlCache.remove(videoId)
    }

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
