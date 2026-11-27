package com.gitgud.tunerack.data.settings

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Streaming quality: the highest bitrate to use. YouTube offers Opus 160/70/50 and AAC 128/48 kbps. */
enum class StreamQuality(val maxKbps: Int, val label: String, val detail: String) {
    High(Int.MAX_VALUE, "High", "Up to 160 kbps · about 72 MB per hour"),
    Normal(128, "Normal", "128 kbps · about 58 MB per hour"),
    Low(70, "Data Saver", "50–70 kbps · about 30 MB per hour"),
}

/** Download quality. Downloads stay AAC in M4A so they keep their tags and play in any music app. */
enum class DownloadQuality(val maxKbps: Int, val label: String, val detail: String) {
    High(128, "High", "AAC 128 kbps · about 4 MB per song"),
    Low(48, "Data Saver", "AAC 48 kbps · about 1.5 MB per song"),
}

class AudioSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("audio", Context.MODE_PRIVATE)

    private val _wifi = MutableStateFlow(read(KEY_WIFI, StreamQuality.High))
    private val _mobile = MutableStateFlow(read(KEY_MOBILE, StreamQuality.Normal))
    private val _download = MutableStateFlow(
        DownloadQuality.entries.firstOrNull { it.name == prefs.getString(KEY_DOWNLOAD, null) } ?: DownloadQuality.High,
    )

    val wifi: StateFlow<StreamQuality> = _wifi.asStateFlow()
    val mobile: StateFlow<StreamQuality> = _mobile.asStateFlow()
    val download: StateFlow<DownloadQuality> = _download.asStateFlow()

    fun setWifi(quality: StreamQuality) = save(KEY_WIFI, quality.name) { _wifi.value = quality }
    fun setMobile(quality: StreamQuality) = save(KEY_MOBILE, quality.name) { _mobile.value = quality }
    fun setDownload(quality: DownloadQuality) = save(KEY_DOWNLOAD, quality.name) { _download.value = quality }

    /** Bitrate cap for the next song: the mobile-data setting on metered connections, else Wi-Fi. */
    fun streamCapKbps(): Int {
        val metered = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
        return (if (metered) _mobile.value else _wifi.value).maxKbps
    }

    private fun read(key: String, default: StreamQuality) =
        StreamQuality.entries.firstOrNull { it.name == prefs.getString(key, null) } ?: default

    private fun save(key: String, value: String, apply: () -> Unit) {
        prefs.edit { putString(key, value) }
        apply()
    }

    private companion object {
        const val KEY_WIFI = "streamWifi"
        const val KEY_MOBILE = "streamMobile"
        const val KEY_DOWNLOAD = "download"
    }
}

/**
 * Picks the best stream at or under [maxKbps]; if every stream is above the cap, the smallest one.
 * At equal bitrate Opus wins (better quality per bit). Pure, so it can be unit tested.
 */
fun <T> pickByBitrate(streams: List<T>, maxKbps: Int, kbps: (T) -> Int, isOpus: (T) -> Boolean): T? {
    val order = compareBy<T> { kbps(it) }.thenBy { isOpus(it) }
    return streams.filter { kbps(it) in 1..maxKbps }.maxWithOrNull(order)
        ?: streams.filter { kbps(it) > 0 }.minWithOrNull(compareBy(kbps))
        ?: streams.firstOrNull()
}
