package com.gitgud.tunerack.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.youtube.YouTubeMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val query: String, val tracks: List<Track>) : SearchState
    data class Failed(val query: String, val message: String) : SearchState
}

class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun search(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = SearchState.Loading
            _state.value = try {
                SearchState.Results(query, YouTubeMusic.searchSongs(query))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                SearchState.Failed(query, "Check your connection and try again.")
            } catch (e: Exception) {
                SearchState.Failed(query, "YouTube Music didn't respond as expected. Try again in a moment.")
            }
        }
    }
}
