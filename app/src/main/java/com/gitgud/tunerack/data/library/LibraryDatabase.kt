package com.gitgud.tunerack.data.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Song metadata, stored once and shared by likes, playlists and history. */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val artworkUrl: String?,
)

@Entity(tableName = "likes", indices = [Index("likedAt")])
data class LikeEntity(
    @PrimaryKey val songId: String,
    val likedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "history", indices = [Index("playedAt"), Index("songId")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
)

/** One downloaded (or downloading) song. [uri] is the saved file in the user's chosen folder. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val state: Int,
    val progress: Float = 0f,
    val uri: String? = null,
    val sizeBytes: Long = 0,
    val updatedAt: Long,
    /** Why the last attempt failed, in words for the user (only set when [state] is FAILED). */
    val error: String? = null,
) {
    companion object {
        const val QUEUED = 0
        const val RUNNING = 1
        const val DONE = 2
        const val FAILED = 3
    }
}

/** A Spotify chart track matched to a YouTube Music song, so each is searched only once. */
@Entity(tableName = "chart_matches")
data class ChartMatchEntity(
    /** ChartEntry.matchKey: a Spotify track id, or "yt:artist - title" for YouTube country charts. */
    @PrimaryKey val spotifyTrackId: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val artworkUrl: String?,
    val matchedAt: Long,
)

data class DownloadedSong(
    @androidx.room.Embedded val song: SongEntity,
    val state: Int,
    val progress: Float,
    val sizeBytes: Long,
    val error: String?,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val songCount: Int,
    val coverUrl: String?,
)

data class ArtistSummary(
    val name: String,
    val songCount: Int,
    val artworkUrl: String?,
)

@Dao
interface LibraryDao {

    @Upsert
    suspend fun upsertSong(song: SongEntity)

    // Likes ("Songs" in the library)

    @Query("SELECT s.* FROM songs s JOIN likes l ON l.songId = s.id ORDER BY l.likedAt DESC")
    fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT songId FROM likes")
    fun likedIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLike(like: LikeEntity)

    @Query("DELETE FROM likes WHERE songId = :songId")
    suspend fun deleteLike(songId: String)

    @Query(
        """
        SELECT s.artist AS name, COUNT(*) AS songCount, MAX(s.artworkUrl) AS artworkUrl
        FROM songs s JOIN likes l ON l.songId = s.id
        GROUP BY s.artist ORDER BY s.artist COLLATE NOCASE
        """,
    )
    fun artists(): Flow<List<ArtistSummary>>

    @Query(
        "SELECT s.* FROM songs s JOIN likes l ON l.songId = s.id WHERE s.artist = :artist ORDER BY s.title COLLATE NOCASE",
    )
    fun artistSongs(artist: String): Flow<List<SongEntity>>

    // Playlists

    @Query(
        """
        SELECT p.id, p.name,
            (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount,
            (SELECT s.artworkUrl FROM playlist_songs ps JOIN songs s ON s.id = ps.songId
                WHERE ps.playlistId = p.id ORDER BY ps.position LIMIT 1) AS coverUrl
        FROM playlists p ORDER BY p.updatedAt DESC
        """,
    )
    fun playlists(): Flow<List<PlaylistSummary>>

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE id = :id)")
    suspend fun playlistExists(id: Long): Boolean

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query(
        "SELECT s.* FROM songs s JOIN playlist_songs ps ON ps.songId = s.id WHERE ps.playlistId = :id ORDER BY ps.position",
    )
    fun playlistSongs(id: Long): Flow<List<SongEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touchPlaylist(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Transaction
    suspend fun deletePlaylist(id: Long) {
        clearPlaylist(id)
        deletePlaylistRow(id)
    }

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    suspend fun lastPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(entry: PlaylistSongEntity): Long

    /** Appends [songId]; returns false when it was already in the playlist. */
    @Transaction
    suspend fun appendToPlaylist(id: Long, songId: String, now: Long): Boolean {
        val added = insertPlaylistSong(PlaylistSongEntity(id, songId, lastPosition(id) + 1, now)) != -1L
        if (added) touchPlaylist(id, now)
        return added
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id AND songId = :songId")
    suspend fun removeFromPlaylist(id: Long, songId: String)

    // Downloads

    @Upsert
    suspend fun upsertDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun download(songId: String): DownloadEntity?

    /** Blocking on purpose: called from ExoPlayer's loader thread when resolving a song. */
    @Query("SELECT uri FROM downloads WHERE songId = :songId AND state = 2")
    fun downloadedUriBlocking(songId: String): String?

    @Query("SELECT * FROM downloads")
    fun downloads(): Flow<List<DownloadEntity>>

    @Query(
        """
        SELECT s.*, d.state, d.progress, d.sizeBytes, d.error FROM downloads d JOIN songs s ON s.id = d.songId
        ORDER BY d.state = 2, d.updatedAt DESC
        """,
    )
    fun downloadedSongs(): Flow<List<DownloadedSong>>

    @Query("UPDATE downloads SET state = :state, progress = :progress, updatedAt = :now, error = :error WHERE songId = :songId")
    suspend fun setDownloadState(songId: String, state: Int, progress: Float, now: Long, error: String? = null)

    @Query("SELECT COUNT(*) FROM downloads WHERE state IN (0, 1)")
    suspend fun activeDownloadCount(): Int

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun deleteDownload(songId: String)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: String): SongEntity?

    // Backup and re-scan

    @Query("SELECT * FROM songs")
    suspend fun allSongs(): List<SongEntity>

    @Query("SELECT * FROM likes")
    suspend fun allLikes(): List<LikeEntity>

    @Query("SELECT * FROM playlists")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    suspend fun allPlaylistSongs(): List<PlaylistSongEntity>

    @Query("SELECT * FROM history ORDER BY playedAt")
    suspend fun allHistory(): List<HistoryEntity>

    @Query("SELECT * FROM downloads")
    suspend fun allDownloads(): List<DownloadEntity>

    @Query("SELECT id FROM playlists WHERE name = :name AND createdAt = :createdAt LIMIT 1")
    suspend fun findPlaylist(name: String, createdAt: Long): Long?

    @Query("SELECT COUNT(*) FROM history WHERE songId = :songId AND playedAt = :playedAt")
    suspend fun historyCount(songId: String, playedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongIfMissing(song: SongEntity)

    /**
     * Merges a backup into the library in one transaction. Existing data is kept; playlists and
     * history entries already present (same name and creation time / same play time) are skipped,
     * so importing the same file twice changes nothing.
     */
    @Transaction
    suspend fun importBackup(backup: LibraryBackup.Contents): LibraryBackup.Result {
        backup.songs.forEach { insertSongIfMissing(it) }
        backup.likes.forEach { insertLike(it) }
        var playlists = 0
        backup.playlists.forEach { playlist ->
            if (findPlaylist(playlist.name, playlist.createdAt) != null) return@forEach
            val id = insertPlaylist(PlaylistEntity(name = playlist.name, createdAt = playlist.createdAt, updatedAt = playlist.updatedAt))
            playlist.songs.forEachIndexed { position, entry ->
                insertPlaylistSong(PlaylistSongEntity(id, entry.first, position, entry.second))
            }
            playlists++
        }
        backup.history.forEach { if (historyCount(it.songId, it.playedAt) == 0) insertHistory(it) }
        return LibraryBackup.Result(backup.songs.size, backup.likes.size, playlists)
    }

    // Chart matches

    @Query("SELECT * FROM chart_matches")
    fun chartMatches(): Flow<List<ChartMatchEntity>>

    @Query("SELECT * FROM chart_matches WHERE spotifyTrackId = :spotifyTrackId")
    suspend fun chartMatch(spotifyTrackId: String): ChartMatchEntity?

    @Upsert
    suspend fun upsertChartMatch(match: ChartMatchEntity)

    // History

    @Insert
    suspend fun insertHistory(entry: HistoryEntity)

    @Query(
        """
        SELECT s.* FROM songs s
        JOIN (SELECT songId, MAX(playedAt) AS lastPlayed FROM history GROUP BY songId) h ON h.songId = s.id
        ORDER BY h.lastPlayed DESC LIMIT :limit
        """,
    )
    fun recentlyPlayed(limit: Int): Flow<List<SongEntity>>
}

@Database(
    entities = [
        SongEntity::class, LikeEntity::class, PlaylistEntity::class, PlaylistSongEntity::class,
        HistoryEntity::class, DownloadEntity::class, ChartMatchEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()
    }
}
