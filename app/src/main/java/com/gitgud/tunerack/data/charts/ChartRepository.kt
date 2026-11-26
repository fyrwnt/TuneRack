package com.gitgud.tunerack.data.charts

import android.content.Context
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.library.ChartMatchEntity
import com.gitgud.tunerack.data.library.LibraryDao
import com.gitgud.tunerack.data.youtube.YouTubeMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Spotify and YouTube charts from kworb.net. Pages are fetched at most every [MAX_AGE_MS] (kworb
 * updates daily) and kept on disk, so the Charts tab also works offline with the last copy.
 */
class ChartRepository(
    context: Context,
    private val http: OkHttpClient,
    private val dao: LibraryDao,
) {
    private val cacheDir = File(context.filesDir, "charts").apply { mkdirs() }

    /** Spotify track id -> matched YouTube Music song. */
    val matches: Flow<Map<String, Track>> = dao.chartMatches().map { list ->
        list.associate { it.spotifyTrackId to Track(it.videoId, it.title, it.artist, it.durationSeconds, it.artworkUrl) }
    }

    suspend fun chart(source: ChartSource, refresh: Boolean = false): Chart = withContext(Dispatchers.IO) {
        KworbParser.parse(source, cached("${source.cacheName}.html", source.path, MAX_AGE_MS, refresh))
    }

    /** Countries with Spotify charts; the list barely changes, so it is refreshed weekly. */
    suspend fun countries(): List<ChartCountry> = withContext(Dispatchers.IO) {
        KworbParser.parseCountries(cached("countries.html", "spotify/", 7 * 24 * MAX_AGE_MS / 12, refresh = false))
    }

    /** Page from disk if younger than [maxAge], else from kworb (falling back to a stale copy). */
    private fun cached(name: String, path: String, maxAge: Long, refresh: Boolean): String {
        val file = File(cacheDir, name)
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < maxAge
        if (fresh && !refresh) return file.readText()
        return runCatching { download(path) }
            .onSuccess { file.writeText(it) }
            .getOrElse { error -> if (file.exists()) file.readText() else throw error }
    }

    /** YouTube Music songs to choose from when a chart match is wrong. */
    suspend fun candidates(entry: ChartEntry): List<Track> =
        YouTubeMusic.searchSongs("${entry.artist} ${entry.title}").take(8)

    /** Replaces the saved match for a Spotify chart entry with the user's choice. */
    suspend fun setMatch(entry: ChartEntry, track: Track) {
        val spotifyId = entry.spotifyTrackId ?: return
        dao.upsertChartMatch(
            ChartMatchEntity(spotifyId, track.id, track.title, track.artist, track.durationSeconds, track.artworkUrl, System.currentTimeMillis()),
        )
    }

    private fun download(path: String): String {
        val request = Request.Builder()
            .url("https://kworb.net/$path")
            .header("User-Agent", "TuneRack/0.1 (Android music player)")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("kworb returned HTTP ${response.code}")
            response.body.string()
        }
    }

    /** The playable song for an entry: YouTube entries directly, Spotify ones via a saved or new match. */
    suspend fun trackFor(entry: ChartEntry): Track? {
        entry.videoId?.let { return youTubeTrack(entry, it) }
        val spotifyId = entry.spotifyTrackId ?: return null
        dao.chartMatch(spotifyId)?.let { return Track(it.videoId, it.title, it.artist, it.durationSeconds, it.artworkUrl) }
        return match(entry)
    }

    /** Matches the given Spotify entries in the background, a few searches at a time. */
    suspend fun matchAll(entries: List<ChartEntry>) = coroutineScope {
        val gate = Semaphore(4)
        entries.filter { it.spotifyTrackId != null }
            .map { entry ->
                async {
                    gate.withPermit {
                        if (dao.chartMatch(entry.spotifyTrackId!!) == null) runCatching { match(entry) }
                    }
                }
            }
            .awaitAll()
    }

    private suspend fun match(entry: ChartEntry): Track? {
        val results = YouTubeMusic.searchSongs("${entry.artist} ${entry.title}")
        val best = ChartMatcher.best(entry, results) ?: return null
        dao.upsertChartMatch(
            ChartMatchEntity(entry.spotifyTrackId!!, best.id, best.title, best.artist, best.durationSeconds, best.artworkUrl, System.currentTimeMillis()),
        )
        return best
    }

    private fun youTubeTrack(entry: ChartEntry, videoId: String) =
        Track(videoId, entry.title, entry.artist, 0, "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")

    private companion object {
        const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
    }
}

/** Picks the YouTube Music search result that best fits a Spotify chart entry. */
object ChartMatcher {

    fun best(entry: ChartEntry, results: List<Track>): Track? {
        if (results.isEmpty()) return null
        val wantTitle = normalize(entry.title)
        val wantArtist = normalize(entry.artist.substringBefore(','))
        return results.take(5)
            .withIndex()
            .maxWithOrNull(compareBy<IndexedValue<Track>> { score(it.value, wantTitle, wantArtist) }.thenByDescending { it.index })
            ?.value
    }

    private fun score(candidate: Track, wantTitle: String, wantArtist: String): Int {
        val title = normalize(candidate.title)
        val artist = normalize(candidate.artist)
        val titleScore = when {
            title == wantTitle -> 3
            title.contains(wantTitle) || wantTitle.contains(title) -> 2
            else -> 0
        }
        val artistScore = if (wantArtist.isNotEmpty() && artist.contains(wantArtist)) 2 else 0
        return titleScore + artistScore
    }

    /** Lowercase, without "(feat. ...)", "[Remix]"-style brackets or punctuation. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
            .replace(Regex("\\b(feat|ft)\\.?\\s.*$"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
