package com.gitgud.tunerack.data.library

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Library backup as a small JSON file: songs, likes, playlists and history. Audio files are not
 * included; they live in the download folder and are reconnected with a folder re-scan.
 */
object LibraryBackup {

    private const val FORMAT = "tunerack-library"
    private const val VERSION = 1

    class Contents(
        val songs: List<SongEntity>,
        val likes: List<LikeEntity>,
        val playlists: List<Playlist>,
        val history: List<HistoryEntity>,
    )

    /** A playlist with its songs as (songId, addedAt), in order. */
    class Playlist(val name: String, val createdAt: Long, val updatedAt: Long, val songs: List<Pair<String, Long>>)

    class Result(val songs: Int, val likes: Int, val playlists: Int)

    suspend fun export(dao: LibraryDao, output: OutputStream) {
        val playlistSongs = dao.allPlaylistSongs().groupBy { it.playlistId }
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("songs", JSONArray(dao.allSongs().map { song ->
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("durationSeconds", song.durationSeconds)
                    .putOpt("artworkUrl", song.artworkUrl)
            }))
            .put("likes", JSONArray(dao.allLikes().map { JSONObject().put("songId", it.songId).put("likedAt", it.likedAt) }))
            .put("playlists", JSONArray(dao.allPlaylists().map { playlist ->
                JSONObject()
                    .put("name", playlist.name)
                    .put("createdAt", playlist.createdAt)
                    .put("updatedAt", playlist.updatedAt)
                    .put("songs", JSONArray(playlistSongs[playlist.id].orEmpty().map {
                        JSONObject().put("songId", it.songId).put("addedAt", it.addedAt)
                    }))
            }))
            .put("history", JSONArray(dao.allHistory().map { JSONObject().put("songId", it.songId).put("playedAt", it.playedAt) }))
        output.write(json.toString(2).toByteArray(Charsets.UTF_8))
    }

    /** Parses a backup; throws IllegalArgumentException if the file isn't a TuneRack library. */
    fun parse(input: InputStream): Contents {
        val json = runCatching { JSONObject(input.readBytes().toString(Charsets.UTF_8)) }
            .getOrElse { throw IllegalArgumentException("Not a TuneRack library file") }
        require(json.optString("format") == FORMAT) { "Not a TuneRack library file" }
        require(json.optInt("version") <= VERSION) { "This backup is from a newer version of TuneRack" }

        return Contents(
            songs = json.objects("songs").map {
                SongEntity(it.getString("id"), it.getString("title"), it.optString("artist"), it.optLong("durationSeconds"), it.optString("artworkUrl").ifEmpty { null })
            },
            likes = json.objects("likes").map { LikeEntity(it.getString("songId"), it.getLong("likedAt")) },
            playlists = json.objects("playlists").map { p ->
                Playlist(
                    p.getString("name"),
                    p.getLong("createdAt"),
                    p.optLong("updatedAt", p.getLong("createdAt")),
                    p.objects("songs").map { it.getString("songId") to it.optLong("addedAt") },
                )
            },
            history = json.objects("history").map { HistoryEntity(songId = it.getString("songId"), playedAt = it.getLong("playedAt")) },
        )
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map(array::getJSONObject)
    }
}
