package com.gitgud.tunerack.data.library

import com.gitgud.tunerack.data.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream

/** The user's local library: likes, playlists and listening history. Everything stays on device. */
class LibraryRepository(internal val dao: LibraryDao) {

    val likedSongs: Flow<List<Track>> = dao.likedSongs().map { it.map(SongEntity::toTrack) }
    val likedIds: Flow<Set<String>> = dao.likedIds().map { it.toSet() }
    val playlists: Flow<List<PlaylistSummary>> = dao.playlists()
    val artists: Flow<List<ArtistSummary>> = dao.artists()

    fun recentlyPlayed(limit: Int = 20): Flow<List<Track>> = dao.recentlyPlayed(limit).map { it.map(SongEntity::toTrack) }
    fun artistSongs(artist: String): Flow<List<Track>> = dao.artistSongs(artist).map { it.map(SongEntity::toTrack) }
    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.playlist(id)
    fun playlistSongs(id: Long): Flow<List<Track>> = dao.playlistSongs(id).map { it.map(SongEntity::toTrack) }
    suspend fun playlistSongsOnce(id: Long): List<Track> = dao.playlistSongs(id).first().map(SongEntity::toTrack)

    suspend fun setLiked(track: Track, liked: Boolean) {
        if (liked) {
            dao.upsertSong(track.toEntity())
            dao.insertLike(LikeEntity(track.id, now()))
        } else {
            dao.deleteLike(track.id)
        }
    }

    suspend fun createPlaylist(name: String): Long {
        val time = now()
        return dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = time, updatedAt = time))
    }

    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim(), now())

    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    /** Returns false if the song was already in the playlist. */
    suspend fun addToPlaylist(playlistId: Long, track: Track): Boolean {
        dao.upsertSong(track.toEntity())
        return dao.appendToPlaylist(playlistId, track.id, now())
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) = dao.removeFromPlaylist(playlistId, songId)

    suspend fun recordPlay(track: Track) {
        dao.upsertSong(track.toEntity())
        dao.insertHistory(HistoryEntity(songId = track.id, playedAt = now()))
    }

    suspend fun exportTo(output: OutputStream) = LibraryBackup.export(dao, output)

    suspend fun importFrom(input: InputStream): LibraryBackup.Result = dao.importBackup(LibraryBackup.parse(input))

    private fun now() = System.currentTimeMillis()
}

internal fun SongEntity.toTrack() = Track(id, title, artist, durationSeconds, artworkUrl)

internal fun Track.toEntity() = SongEntity(id, title, artist, durationSeconds, artworkUrl)
