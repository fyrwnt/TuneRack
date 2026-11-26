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

    private val spotifyEntries = MutableStateFlow<List<com.gitgud.tunerack.data.charts.ChartEntry>?>(null)

    /** Top of today's global Spotify chart, as far as it has been matched to YouTube Music. */
    val spotifyTop: StateFlow<List<Track>?> = combine(spotifyEntries, app.charts.matches) { entries, matches ->
        entries?.mapNotNull { it.spotifyTrackId?.let(matches::get) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _youTubeTop = MutableStateFlow<List<Track>?>(null)
    val youTubeTop: StateFlow<List<Track>?> = _youTubeTop

    init {
        viewModelScope.launch {
            // Picks follow the most recently played song.
            app.library.recentlyPlayed(1).map { it.firstOrNull()?.id }.distinctUntilChanged().collect { seed ->
                _topPicks.value = if (seed == null) emptyList() else runCatching { YouTubeMusic.related(seed).take(12) }.getOrDefault(emptyList())
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
