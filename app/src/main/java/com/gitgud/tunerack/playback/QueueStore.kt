package com.gitgud.tunerack.playback

import com.gitgud.tunerack.data.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The last queue, so TuneRack reopens where it left off (paused) after being closed or killed. */
class QueueStore(private val file: File) {

    class Saved(
        val tracks: List<Track>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
        /** Exact play order while shuffled, so a restored queue continues the same shuffle. */
        val shuffleOrder: List<Int>? = null,
    )

    fun save(saved: Saved) {
        val json = JSONObject()
            .put("index", saved.index)
            .put("positionMs", saved.positionMs)
            .put("shuffle", saved.shuffle)
            .put("repeatMode", saved.repeatMode)
            .put("tracks", JSONArray(saved.tracks.map { it.toJson() }))
            .putOpt("shuffleOrder", saved.shuffleOrder?.let { JSONArray(it) })
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString())
        temp.renameTo(file) // Atomic replace: a crash mid-write never leaves a broken queue file.
    }

    fun load(): Saved? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val array = json.getJSONArray("tracks")
        val tracks = (0 until array.length()).map { array.getJSONObject(it).toTrack() }
        if (tracks.isEmpty()) return null
        Saved(
            tracks = tracks,
            index = json.optInt("index").coerceIn(tracks.indices),
            positionMs = json.optLong("positionMs").coerceAtLeast(0),
            shuffle = json.optBoolean("shuffle"),
            repeatMode = json.optInt("repeatMode"),
            shuffleOrder = json.optJSONArray("shuffleOrder")
                ?.let { order -> (0 until order.length()).map(order::getInt) }
                ?.takeIf { order -> order.sorted() == tracks.indices.toList() },
        )
    }.getOrNull()

    fun clear() {
        file.delete()
    }

    private fun Track.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("durationSeconds", durationSeconds)
        .putOpt("artworkUrl", artworkUrl)

    private fun JSONObject.toTrack() = Track(
        id = getString("id"),
        title = optString("title"),
        artist = optString("artist"),
        durationSeconds = optLong("durationSeconds"),
        artworkUrl = optString("artworkUrl").ifEmpty { null },
    )
}
