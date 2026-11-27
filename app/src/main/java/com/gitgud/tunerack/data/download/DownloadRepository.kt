package com.gitgud.tunerack.data.download

import com.gitgud.tunerack.data.settings.AudioSettings
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.library.DownloadEntity
import com.gitgud.tunerack.data.library.DownloadedSong
import com.gitgud.tunerack.data.library.LibraryDao
import com.gitgud.tunerack.data.library.SongEntity
import com.gitgud.tunerack.data.library.toEntity
import com.gitgud.tunerack.data.youtube.YouTubeMusic
import com.gitgud.tunerack.data.youtube.toPlaybackException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unlimited offline downloads. Each song is one WorkManager job, so downloads continue in the
 * background and retry on flaky connections. Files are tagged M4A saved to a folder the user
 * picked, so they stay usable outside the app.
 */
class DownloadRepository(
    private val context: Context,
    private val dao: LibraryDao,
    private val http: OkHttpClient,
    private val audioSettings: AudioSettings,
) {
    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val _folder = MutableStateFlow(prefs.getString(KEY_FOLDER, null)?.toUri())

    /** The user's chosen download folder (a Storage Access Framework tree), or null if not chosen yet. */
    val folder: StateFlow<Uri?> = _folder.asStateFlow()

    val states: Flow<Map<String, DownloadEntity>> = dao.downloads().map { list -> list.associateBy { it.songId } }
    val songs: Flow<List<DownloadedSong>> = dao.downloadedSongs()

    fun setFolder(tree: Uri) {
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit { putString(KEY_FOLDER, tree.toString()) }
        _folder.value = tree
    }

    fun folderName(tree: Uri): String = DocumentFile.fromTreeUri(context, tree)?.name ?: "Chosen folder"

    suspend fun enqueue(tracks: List<Track>) {
        val workManager = WorkManager.getInstance(context)
        for (track in tracks) {
            val existing = dao.download(track.id)
            if (existing?.state == DownloadEntity.DONE || existing?.state == DownloadEntity.RUNNING) continue
            dao.upsertSong(track.toEntity())
            dao.upsertDownload(DownloadEntity(track.id, DownloadEntity.QUEUED, updatedAt = now()))
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_ID to track.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(DownloadWorker.TAG)
                .build()
            workManager.enqueueUniqueWork(workName(track.id), ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Cancels a pending download or deletes a finished one, including its file. */
    suspend fun remove(songId: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(songId))
        dao.download(songId)?.uri?.let { uri ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri.toUri()) }
        }
        dao.deleteDownload(songId)
    }

    /** Local file for [songId] if it is downloaded and still exists. Blocking (player loader thread). */
    fun localUriBlocking(songId: String): Uri? {
        val uri = dao.downloadedUriBlocking(songId)?.toUri() ?: return null
        return uri.takeIf { DocumentFile.fromSingleUri(context, it)?.exists() == true }
    }

    class RescanResult(val linked: Int, val unknown: Int, val removed: Int)

    /**
     * Reconnects audio files in the download folder to the library, e.g. after reinstalling or
     * moving to a new phone. Files are identified by the song link TuneRack embeds in the tags,
     * or by their "Artist - Title" name. Downloads whose files have disappeared are forgotten.
     */
    suspend fun rescan(): RescanResult = withContext(Dispatchers.IO) {
        val tree = folder.value ?: throw IllegalStateException("No download folder chosen")
        val root = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Download folder is unavailable")
        val byName = dao.allSongs().associateBy { fileName(it.artist, it.title).lowercase() }
        val found = mutableSetOf<String>()
        var linked = 0
        var unknown = 0

        for (file in audioFiles(root, depth = 2)) {
            val name = file.name ?: continue
            val baseName = name.substringBeforeLast('.')
            val tags = if (name.endsWith(".m4a", true) || name.endsWith(".mp4", true)) {
                runCatching { context.contentResolver.openInputStream(file.uri)?.use(Mp4Tagger::read) }.getOrNull()
            } else {
                null
            }
            val id = tags?.comment?.let(::songIdFromLink) ?: byName[baseName.lowercase()]?.id
            if (id == null) {
                unknown++
                continue
            }
            if (dao.song(id) == null) {
                dao.upsertSong(
                    SongEntity(
                        id = id,
                        title = tags?.title ?: baseName.substringAfter(" - "),
                        artist = tags?.artist ?: baseName.substringBefore(" - ", ""),
                        durationSeconds = 0,
                        artworkUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                    ),
                )
            }
            val uri = file.uri.toString()
            val current = dao.download(id)
            if (current?.state != DownloadEntity.DONE || current.uri != uri) {
                dao.upsertDownload(DownloadEntity(id, DownloadEntity.DONE, 1f, uri, file.length(), now()))
            }
            found += uri
            linked++
        }

        var removed = 0
        dao.allDownloads()
            .filter { it.state == DownloadEntity.DONE && it.uri !in found }
            .filter { download -> download.uri?.let { DocumentFile.fromSingleUri(context, it.toUri())?.exists() } != true }
            .forEach {
                dao.deleteDownload(it.songId)
                removed++
            }
        RescanResult(linked, unknown, removed)
    }

    private fun audioFiles(directory: DocumentFile, depth: Int): List<DocumentFile> =
        directory.listFiles().flatMap { file ->
            when {
                file.isDirectory && depth > 0 -> audioFiles(file, depth - 1)
                file.isFile && file.name?.substringAfterLast('.', "")?.lowercase() in AUDIO_EXTENSIONS -> listOf(file)
                else -> emptyList()
            }
        }

    // ----- the download itself (runs inside DownloadWorker) -----

    private val parallel = Semaphore(3)

    /** [onProgress] gets the song title, 0..1 progress and how many other downloads are waiting. */
    suspend fun performDownload(songId: String, onProgress: suspend (String, Float, Int) -> Unit = { _, _, _ -> }) =
        parallel.withPermit {
            withContext(Dispatchers.IO) { download(songId, onProgress) }
        }

    private suspend fun download(songId: String, onProgress: suspend (String, Float, Int) -> Unit) {
        val song = dao.song(songId) ?: return dao.deleteDownload(songId)
        onProgress(song.title, 0f, (dao.activeDownloadCount() - 1).coerceAtLeast(0))
        val tree = folder.value ?: throw IllegalStateException("No download folder chosen")
        dao.setDownloadState(songId, DownloadEntity.RUNNING, 0f, now())

        val source = try {
            YouTubeMusic.resolveDownload(songId, audioSettings.download.value.maxKbps)
        } catch (e: ExtractionException) {
            throw e.toPlaybackException()
        }
        val work = File(context.cacheDir, "downloads").apply { mkdirs() }
        val raw = File(work, "$songId.part")
        val tagged = File(work, "$songId.tagged")
        var created: Uri? = null
        try {
            var lastReport = 0L
            fetch(source.url, raw) { fraction ->
                val time = System.currentTimeMillis()
                if (time - lastReport > 700) {
                    lastReport = time
                    dao.setDownloadState(songId, DownloadEntity.RUNNING, fraction * 0.95f, now())
                    onProgress(song.title, fraction, (dao.activeDownloadCount() - 1).coerceAtLeast(0))
                }
            }

            val output = if (source.extension == "m4a") {
                val cover = song.artworkUrl?.let(::fetchArtwork)
                runCatching {
                    tagged.outputStream().use {
                        Mp4Tagger.tag(raw, it, Mp4Tagger.Tags(song.title, song.artist, cover?.first, cover?.second == true, comment = songLink(songId)))
                    }
                    tagged
                }.getOrElse { raw } // Tagging is cosmetic; keep the untagged audio rather than fail.
            } else {
                raw
            }

            val directory = DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Download folder is unavailable")
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension) ?: source.mimeType
            val file = directory.createFile(mime, "${fileName(song.artist, song.title)}.${source.extension}")
                ?: throw IOException("Could not create a file in the download folder")
            created = file.uri
            context.contentResolver.openOutputStream(file.uri)?.use { out -> output.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not write to the download folder")

            dao.upsertDownload(DownloadEntity(songId, DownloadEntity.DONE, 1f, file.uri.toString(), output.length(), now()))
            created = null
        } finally {
            created?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            raw.delete()
            tagged.delete()
        }
    }

    suspend fun markFailed(songId: String, reason: String) = dao.setDownloadState(songId, DownloadEntity.FAILED, 0f, now(), reason)

    suspend fun markQueued(songId: String) = dao.setDownloadState(songId, DownloadEntity.QUEUED, 0f, now())

    /** Downloads in ranged chunks; googlevideo throttles or cuts off long single requests. */
    private suspend fun fetch(url: String, target: File, onProgress: suspend (Float) -> Unit) {
        target.outputStream().use { out ->
            var position = 0L
            var total = -1L
            while (total < 0 || position < total) {
                val end = position + CHUNK - 1
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", YouTubeMusic.streamUserAgent)
                    .header("Range", "bytes=$position-$end")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) throw IOException("HTTP ${response.code}")
                    total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: response.body.contentLength().takeIf { response.code == 200 }
                        ?: throw IOException("Unknown download size")
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            position += read
                        }
                    }
                }
                onProgress(position.toFloat() / total)
                if (position == 0L) throw IOException("Empty download")
            }
        }
    }

    /** Cover art bytes, and whether they are PNG. Only JPEG/PNG can be embedded in M4A. */
    private fun fetchArtwork(url: String): Pair<ByteArray, Boolean>? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body.bytes()
            val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
            val jpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            if (png || jpeg) bytes to png else null
        }
    }.getOrNull()

    private fun fileName(artist: String, title: String): String =
        listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ")
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .take(120)
            .ifEmpty { "Untitled" }

    private fun workName(songId: String) = "download-$songId"

    private fun songLink(songId: String) = "https://music.youtube.com/watch?v=$songId"

    private fun songIdFromLink(link: String): String? = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(link)?.groupValues?.get(1)

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val KEY_FOLDER = "folder"
        const val CHUNK = 4L * 1024 * 1024
        val AUDIO_EXTENSIONS = setOf("m4a", "mp4", "webm", "opus")
    }
}
