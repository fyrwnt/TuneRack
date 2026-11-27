package com.gitgud.tunerack.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val artworkUrl: String?,
)

/** Playback items carry only the video id; the service resolves the real stream URL lazily. */
fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .setDurationMs(if (durationSeconds > 0) durationSeconds * 1000 else null)
            .build(),
    )
    .build()

fun MediaItem.toTrack(): Track = Track(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationSeconds = (mediaMetadata.durationMs ?: 0L) / 1000,
    artworkUrl = mediaMetadata.artworkUri?.toString(),
)

/** Filler characters some uploaders use to make a title look empty (Hangul fillers, zero-width, braille blank). */
private val INVISIBLE = setOf('\u115F', '\u1160', '\u3164', '\uFFA0', '\u200B', '\u200C', '\u200D', '\u2800')

/** False for titles that would render as nothing. */
fun String.hasVisibleText(): Boolean = any { !it.isWhitespace() && it !in INVISIBLE }
