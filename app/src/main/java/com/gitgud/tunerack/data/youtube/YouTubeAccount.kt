package com.gitgud.tunerack.data.youtube

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.library.LibraryDao
import com.gitgud.tunerack.data.library.LikeEntity
import com.gitgud.tunerack.data.library.PlaylistEntity
import com.gitgud.tunerack.data.library.toEntity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Optional Google sign-in through the official YouTube Data API, read-only. Signing in imports
 * the account's liked music and playlists into TuneRack's local library; nothing is ever written
 * back to YouTube. Without signing in, the app works as a guest.
 */
class YouTubeAccount(
    private val context: Context,
    private val http: OkHttpClient,
    private val dao: LibraryDao,
) {
    data class Profile(
        val name: String,
        val avatarUrl: String?,
        val lastImport: Long,
        val likes: Int,
        val playlists: Int,
    )

    data class ImportResult(val likes: Int, val playlists: Int)

    /** Either a token, or a screen the user has to go through first (account picker, consent). */
    sealed interface Authorization {
        class Granted(val token: String, val accountName: String?) : Authorization
        class NeedsUser(val intent: PendingIntent) : Authorization
    }

    class SignInException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val prefs = context.getSharedPreferences("youtube_account", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(loadProfile())

    /** Signed-in account, or null for a guest. */
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val client get() = Identity.getAuthorizationClient(context)

    private fun request(): AuthorizationRequest = AuthorizationRequest.Builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .apply { prefs.getString(KEY_ACCOUNT, null)?.let { setAccount(Account(it, "com.google")) } }
        .build()

    /** Asks for read-only YouTube access; returns a token, or a screen to show the user. */
    suspend fun authorize(): Authorization {
        val result = try {
            client.authorize(request()).await()
        } catch (e: ApiException) {
            throw e.toSignInException()
        }
        if (result.hasResolution()) return Authorization.NeedsUser(requireNotNull(result.pendingIntent))
        val token = result.accessToken ?: throw SignInException("Google didn't grant access")
        return Authorization.Granted(token, result.toGoogleSignInAccount()?.account?.name)
    }

    /** Reads the outcome of the screen from [Authorization.NeedsUser]. */
    fun resultFrom(data: Intent?): Authorization.Granted {
        val result = try {
            client.getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            throw e.toSignInException()
        }
        val token = result.accessToken ?: throw SignInException("Sign-in was cancelled")
        return Authorization.Granted(token, result.toGoogleSignInAccount()?.account?.name)
    }

    /** Finishes signing in: remembers the account and loads its channel name and picture. */
    suspend fun completeSignIn(granted: Authorization.Granted) = withContext(Dispatchers.IO) {
        val channel = get(granted.token, "channels", "part" to "snippet", "mine" to "true")
            .optJSONArray("items")?.optJSONObject(0)?.optJSONObject("snippet")
        prefs.edit {
            granted.accountName?.let { putString(KEY_ACCOUNT, it) }
            putString(KEY_NAME, channel?.optString("title")?.ifEmpty { null } ?: granted.accountName ?: "YouTube account")
            putString(KEY_AVATAR, channel?.optJSONObject("thumbnails")?.let(::bestThumbnail))
            putBoolean(KEY_SIGNED_IN, true)
        }
        _profile.value = loadProfile()
    }

    /**
     * Copies liked music and playlists from YouTube into the local library. Running it again adds
     * what's new and changes nothing on YouTube. Songs removed on YouTube stay in TuneRack.
     */
    suspend fun importLibrary(token: String): ImportResult = withContext(Dispatchers.IO) {
        val likes = importLikes(token)
        val playlists = importPlaylists(token)
        prefs.edit {
            putLong(KEY_LAST_IMPORT, System.currentTimeMillis())
            putInt(KEY_LIKES, likes)
            putInt(KEY_PLAYLISTS, playlists)
        }
        _profile.value = loadProfile()
        ImportResult(likes, playlists)
    }

    /** Forgets the account and gives Google's permission back. Imported songs stay in the library. */
    suspend fun signOut() {
        val account = prefs.getString(KEY_ACCOUNT, null)
        runCatching {
            client.revokeAccess(
                RevokeAccessRequest.builder()
                    .setScopes(listOf(Scope(SCOPE)))
                    .apply { account?.let { setAccount(Account(it, "com.google")) } }
                    .build(),
            ).await()
        }
        prefs.edit { clear() }
        _profile.value = null
    }

    /** Drops a token Google rejected, so the next [authorize] fetches a fresh one. */
    suspend fun clearToken(token: String) {
        runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    // ----- import -----

    private suspend fun importLikes(token: String): Int {
        val liked = mutableListOf<Track>()
        var page: String? = null
        do {
            val json = get(
                token, "videos",
                "part" to "snippet,contentDetails", "myRating" to "like", "maxResults" to "50",
                "pageToken" to page,
            )
            json.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val snippet = item.optJSONObject("snippet") ?: continue
                    if (snippet.optString("categoryId") != MUSIC_CATEGORY) continue
                    liked += Track(
                        id = item.getString("id"),
                        title = snippet.optString("title"),
                        artist = cleanArtist(snippet.optString("channelTitle")),
                        durationSeconds = parseDuration(item.optJSONObject("contentDetails")?.optString("duration")),
                        artworkUrl = snippet.optJSONObject("thumbnails")?.let(::bestThumbnail),
                    )
                }
            }
            page = json.optString("nextPageToken").ifEmpty { null }
        } while (page != null && liked.size < MAX_ITEMS)

        // YouTube lists newest first; keep that order in the library's "recently added".
        val newest = System.currentTimeMillis()
        liked.forEachIndexed { index, track ->
            dao.upsertSong(track.toEntity())
            dao.insertLike(LikeEntity(track.id, newest - index))
        }
        return liked.size
    }

    private suspend fun importPlaylists(token: String): Int {
        var count = 0
        var page: String? = null
        do {
            val json = get(token, "playlists", "part" to "snippet", "mine" to "true", "maxResults" to "50", "pageToken" to page)
            json.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val remoteId = item.getString("id")
                    val name = item.optJSONObject("snippet")?.optString("title")?.ifEmpty { null } ?: continue
                    val songs = playlistSongs(token, remoteId)
                    if (songs.isEmpty()) continue
                    val localId = localPlaylistFor(remoteId, name)
                    songs.forEach { track ->
                        dao.upsertSong(track.toEntity())
                        dao.appendToPlaylist(localId, track.id, System.currentTimeMillis())
                    }
                    count++
                }
            }
            page = json.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return count
    }

    private fun playlistSongs(token: String, playlistId: String): List<Track> {
        val songs = mutableListOf<Track>()
        var page: String? = null
        do {
            val json = get(token, "playlistItems", "part" to "snippet", "playlistId" to playlistId, "maxResults" to "50", "pageToken" to page)
            json.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val snippet = items.getJSONObject(i).optJSONObject("snippet") ?: continue
                    val videoId = snippet.optJSONObject("resourceId")?.optString("videoId")?.ifEmpty { null } ?: continue
                    val owner = snippet.optString("videoOwnerChannelTitle")
                    // Deleted and private videos come back without an owner.
                    if (owner.isEmpty()) continue
                    songs += Track(videoId, snippet.optString("title"), cleanArtist(owner), 0, snippet.optJSONObject("thumbnails")?.let(::bestThumbnail))
                }
            }
            page = json.optString("nextPageToken").ifEmpty { null }
        } while (page != null && songs.size < MAX_ITEMS)
        return songs
    }

    /** The local playlist a YouTube playlist was imported into before, or a new one. */
    private suspend fun localPlaylistFor(remoteId: String, name: String): Long {
        val key = "playlist_$remoteId"
        val known = prefs.getLong(key, -1L)
        if (known != -1L && dao.playlistExists(known)) return known
        val time = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = time, updatedAt = time))
        prefs.edit { putLong(key, id) }
        return id
    }

    // ----- HTTP -----

    private fun get(token: String, endpoint: String, vararg params: Pair<String, String?>): JSONObject {
        val url = "$API/$endpoint".toHttpUrl().newBuilder().apply {
            params.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            when {
                response.isSuccessful -> return JSONObject(body)
                response.code == 401 -> throw TokenExpiredException()
                response.code == 403 && body.contains("quotaExceeded") ->
                    throw IOException("YouTube's daily limit for TuneRack is used up. Try again tomorrow.")
                else -> throw IOException("YouTube returned HTTP ${response.code}")
            }
        }
    }

    class TokenExpiredException : IOException("Google sign-in expired")

    // ----- helpers -----

    private fun loadProfile(): Profile? {
        if (!prefs.getBoolean(KEY_SIGNED_IN, false)) return null
        return Profile(
            name = prefs.getString(KEY_NAME, null) ?: "YouTube account",
            avatarUrl = prefs.getString(KEY_AVATAR, null),
            lastImport = prefs.getLong(KEY_LAST_IMPORT, 0L),
            likes = prefs.getInt(KEY_LIKES, 0),
            playlists = prefs.getInt(KEY_PLAYLISTS, 0),
        )
    }

    private fun ApiException.toSignInException(): SignInException = also {
        android.util.Log.w("TuneRackAuth", "Authorization failed: status=$statusCode ${status.statusMessage}", this)
    }.let { when (statusCode) {
        CommonStatusCodes.DEVELOPER_ERROR ->
            SignInException("Google sign-in isn't set up for this version of TuneRack yet.", this)
        CommonStatusCodes.NETWORK_ERROR -> SignInException("Check your connection and try again.", this)
        CommonStatusCodes.CANCELED -> SignInException("Sign-in was cancelled", this)
        else -> SignInException("Google sign-in failed (code $statusCode)", this)
    } }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        private const val API = "https://www.googleapis.com/youtube/v3"
        private const val MUSIC_CATEGORY = "10"
        private const val MAX_ITEMS = 1000
        private const val KEY_SIGNED_IN = "signedIn"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_NAME = "name"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_LAST_IMPORT = "lastImport"
        private const val KEY_LIKES = "likes"
        private const val KEY_PLAYLISTS = "playlists"

        /** "Coldplay - Topic" (YouTube's auto-generated artist channels) -> "Coldplay". */
        internal fun cleanArtist(channel: String): String = channel.removeSuffix(" - Topic").trim()

        /** ISO 8601 duration ("PT4M19S", "PT1H2M") in seconds; 0 when missing. */
        internal fun parseDuration(iso: String?): Long {
            val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(iso ?: return 0) ?: return 0
            val (h, m, s) = match.destructured
            return (h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
        }

        private fun bestThumbnail(thumbnails: JSONObject): String? =
            listOf("maxres", "standard", "high", "medium", "default")
                .firstNotNullOfOrNull { thumbnails.optJSONObject(it)?.optString("url")?.ifEmpty { null } }
    }
}
