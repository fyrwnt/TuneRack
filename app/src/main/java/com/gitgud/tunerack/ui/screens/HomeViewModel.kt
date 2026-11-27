package com.gitgud.tunerack.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.charts.ChartKind
import com.gitgud.tunerack.data.charts.ChartSource
import com.gitgud.tunerack.data.youtube.YouTubeMusic
import com.gitgud.tunerack.tuneRack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home rows. Each is null while loading and empty when there is nothing to show (then hidden).
 * Data comes from listening history, YouTube's related songs, and the kworb charts.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.tuneRack

    val recent: StateFlow<List<Track>> =
        app.library.recentlyPlayed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _topPicks = MutableStateFlow<List<Track>?>(null)
    val topPicks: StateFlow<List<Track>?> = _topPicks

    /** Why Top Picks shows what it shows, e.g. "Because you played Vuelve"; null hides the row. */
    private val _picksReason = MutableStateFlow<String?>(null)
    val picksReason: StateFlow<String?> = _picksReason

    private val spotifyEntries = MutableStateFlow<List<com.gitgud.tunerack.data.charts.ChartEntry>?>(null)

    /** Top of today's global Spotify chart, as far as it has been matched to YouTube Music. */
    val spotifyTop: StateFlow<List<Track>?> = combine(spotifyEntries, app.charts.matches) { entries, matches ->
        entries?.mapNotNull { it.matchKey?.let(matches::get) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _youTubeTop = MutableStateFlow<List<Track>?>(null)
    val youTubeTop: StateFlow<List<Track>?> = _youTubeTop

    init {
        viewModelScope.launch {
            // Picks follow the most recently played song.
            // Picks follow the last song played; before any listening (e.g. right after importing
            // from YouTube), they follow the most recent like instead.
            combine(app.library.recentlyPlayed(1), app.library.likedSongs) { played, liked ->
                played.firstOrNull()?.let { it to "Because you played ${it.title}" }
                    ?: liked.firstOrNull()?.let { it to "Because you liked ${it.title}" }
            }.distinctUntilChanged { a, b -> a?.first?.id == b?.first?.id }.collect { seed ->
                _picksReason.value = seed?.second
                _topPicks.value = if (seed == null) emptyList() else null
                if (seed != null) {
                    _topPicks.value = runCatching { YouTubeMusic.related(seed.first.id).take(12) }.getOrDefault(emptyList())
                }
            }
        }
        viewModelScope.launch {
            val chart = runCatching { app.charts.chart(ChartSource(ChartKind.SpotifyDaily)) }.getOrNull()
            val top = chart?.entries.orEmpty().take(TOP)
            spotifyEntries.value = top
            app.charts.matchAll(top)
        }
        viewModelScope.launch {
            val chart = runCatching { app.charts.chart(ChartSource(ChartKind.YouTube)) }.getOrNull()
            _youTubeTop.value = chart?.entries.orEmpty().take(TOP).mapNotNull { entry ->
                entry.videoId?.let { Track(it, entry.title, entry.artist, 0, "https://i.ytimg.com/vi/$it/hqdefault.jpg") }
            }
        }
    }

    private companion object {
        const val TOP = 15
    }
}
