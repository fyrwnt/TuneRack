package com.gitgud.tunerack.ui.screens

import com.gitgud.tunerack.ui.components.AccountButton
import com.gitgud.tunerack.ui.tutorial.tutorialTarget
import com.gitgud.tunerack.ui.tutorial.Tour
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.ui.components.SongRow
import com.gitgud.tunerack.ui.components.SongCard
import com.gitgud.tunerack.ui.components.LocalSheets
import com.gitgud.tunerack.ui.components.SheetRequest
import androidx.compose.ui.platform.LocalContext
import com.gitgud.tunerack.tuneRack
import androidx.compose.ui.unit.dp
import com.gitgud.tunerack.ui.components.AlbumCard
import com.gitgud.tunerack.ui.components.ArtworkPlaceholder
import com.gitgud.tunerack.ui.components.EmptyMessage
import com.gitgud.tunerack.ui.components.LargeTitleScreen
import com.gitgud.tunerack.ui.components.SectionHeader
import com.gitgud.tunerack.ui.components.TextLine
import com.gitgud.tunerack.ui.components.TuneIcon
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme

// Layout shells for each tab. Placeholders stand in until the YouTube and library steps fill them.

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val player = LocalPlayerConnection.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val picks by viewModel.topPicks.collectAsStateWithLifecycle()
    val spotify by viewModel.spotifyTop.collectAsStateWithLifecycle()
    val youTube by viewModel.youTubeTop.collectAsStateWithLifecycle()
    val reason by viewModel.picksReason.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current

    LargeTitleScreen(
        "Home",
        tour = Tour.Welcome,
        trailing = { AccountButton { sheets.show(SheetRequest.Account) } },
    ) {
        // Top Picks needs something to go on (a play or a like); until then it is not shown.
        reason?.let { why ->
            item(key = "top-picks") {
                SectionHeader("Top Picks for You", showChevron = false, subtitle = why)
                HomeRow(picks, cardSize = 220.dp) { list, index -> player.playQueue(list, index) }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recently-played") {
                SectionHeader("Recently Played", showChevron = false)
                HomeRow(recent) { list, index -> player.playQueue(list, index) }
            }
        }
        item(key = "spotify") {
            SectionHeader("Top on Spotify Today", showChevron = false, subtitle = "Global chart")
            HomeRow(spotify) { list, index -> player.playQueue(list, index) }
        }
        item(key = "youtube") {
            SectionHeader("Trending on YouTube", showChevron = false, subtitle = "Most viewed music videos, past 24 hours")
            HomeRow(youTube) { list, index -> player.playQueue(list, index) }
        }
        if (reason == null) {
            item(key = "hint") {
                EmptyMessage(
                    title = "Your picks start here",
                    message = "Play a few songs, or sign in to bring your YouTube likes, and Top Picks will fill in.",
                )
            }
        }
    }
}

/** Horizontal row of song cards; skeleton cards while [tracks] is null, collapsed when empty. */
@Composable
private fun HomeRow(tracks: List<Track>?, cardSize: androidx.compose.ui.unit.Dp = 156.dp, onPlay: (List<Track>, Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (tracks == null) {
            items(5) { AlbumCard(cardSize) }
        } else if (tracks.isEmpty()) {
            item {
                BasicText(
                    "Nothing to show right now.",
                    style = TuneRackTheme.typography.subheadline.copy(color = TuneRackTheme.colors.secondaryLabel),
                )
            }
        } else {
            itemsIndexed(tracks, key = { _, it -> it.id }) { index, track ->
                SongCard(track, size = cardSize) { onPlay(tracks, index) }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val colors = TuneRackTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = LocalPlayerConnection.current
    val playing by player.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val sheets = LocalSheets.current
    val downloadStates by LocalContext.current.tuneRack.downloads.states.collectAsStateWithLifecycle(emptyMap())
    val field = rememberTextFieldState()

    LargeTitleScreen("Search", tour = Tour.Search) {
        item(key = "field") {
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .tutorialTarget("search.field")
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(colors.fill, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TuneIcon(TuneIcons.Search, colors.secondaryLabel, size = 18.dp)
                Box(Modifier.padding(start = 8.dp).weight(1f)) {
                    if (field.text.isEmpty()) {
                        BasicText(
                            "Artists, songs, albums",
                            style = TuneRackTheme.typography.body.copy(color = colors.secondaryLabel),
                        )
                    }
                    BasicTextField(
                        state = field,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = TuneRackTheme.typography.body.copy(color = colors.label),
                        cursorBrush = SolidColor(colors.label),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = {
                            keyboard?.hide()
                            viewModel.search(field.text.toString())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        when (val s = state) {
            SearchState.Idle -> item(key = "idle") {
                EmptyMessage(
                    title = "Find something to play",
                    message = "Search YouTube Music for songs, artists and albums.",
                    icon = TuneIcons.Search,
                )
            }
            SearchState.Loading -> items(8, key = { "loading-$it" }) { LoadingSongRow() }
            is SearchState.Failed -> item(key = "failed") {
                EmptyMessage(title = "Search failed", message = s.message)
            }
            is SearchState.Results -> {
                if (s.tracks.isEmpty()) {
                    item(key = "none") {
                        EmptyMessage(title = "No results", message = "Nothing on YouTube Music matched \u201c${s.query}\u201d.")
                    }
                } else {
                    item(key = "songs-header") { SectionHeader("Songs", showChevron = false) }
                    itemsIndexed(s.tracks, key = { _, it -> "song-${it.id}" }) { index, track ->
                        SongRow(
                            track = track,
                            isCurrent = playing.nowPlaying?.mediaId == track.id,
                            onMore = { sheets.show(SheetRequest.Song(track)) },
                            download = downloadStates[track.id],
                            moreTarget = if (index == 0) "search.more" else null,
                            onClick = { player.playQueue(s.tracks, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingSongRow() {
    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkPlaceholder(50.dp, corner = 6.dp)
        Column(Modifier.padding(start = 14.dp)) {
            TextLine(170.dp)
            Spacer(Modifier.height(8.dp))
            TextLine(100.dp, height = 8.dp)
        }
    }
}
