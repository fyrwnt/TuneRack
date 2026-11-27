package com.gitgud.tunerack.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.gitgud.tunerack.ui.components.LocalSharedTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.Crossfade
import com.gitgud.tunerack.ui.components.rememberAccountActions
import com.gitgud.tunerack.ui.components.LocalAccount
import com.gitgud.tunerack.ui.tutorial.rememberTutorialController
import com.gitgud.tunerack.ui.tutorial.TutorialOverlay
import com.gitgud.tunerack.ui.tutorial.LocalTutorial
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.ui.components.LocalHazeState
import com.gitgud.tunerack.ui.components.LocalSheets
import com.gitgud.tunerack.ui.components.LocalDownloads
import com.gitgud.tunerack.ui.components.rememberDownloadStarter
import com.gitgud.tunerack.ui.components.LocalLibraryTools
import com.gitgud.tunerack.ui.components.rememberLibraryTools
import com.gitgud.tunerack.ui.components.SheetController
import com.gitgud.tunerack.ui.components.SheetHost
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.gitgud.tunerack.ui.components.BottomChrome
import com.gitgud.tunerack.ui.components.TabItem
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.screens.ChartsScreen
import com.gitgud.tunerack.ui.screens.HomeScreen
import com.gitgud.tunerack.ui.screens.LibraryScreen
import com.gitgud.tunerack.ui.screens.NowPlayingSheet
import com.gitgud.tunerack.ui.screens.SearchScreen
import dev.chrisbanes.haze.rememberHazeState

private val Tabs = listOf(
    TabItem("Home", TuneIcons.Home),
    TabItem("Charts", TuneIcons.Charts),
    TabItem("Library", TuneIcons.Library),
)
private const val SearchTab = 3

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TuneRackApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val hasTrack = LocalPlayerConnection.current.state.collectAsStateWithLifecycle().value.nowPlaying != null
    // Keeps each tab's scroll position when switching away and back.
    val tabStates = rememberSaveableStateHolder()

    val sheets = remember { SheetController() }
    val tutorial = rememberTutorialController()
    val downloads = rememberDownloadStarter()
    val player = LocalPlayerConnection.current
    LaunchedEffect(player) { player.messages.collect(sheets::flash) }
    CompositionLocalProvider(
        LocalHazeState provides rememberHazeState(),
        LocalSheets provides sheets,
        LocalDownloads provides downloads,
        LocalLibraryTools provides rememberLibraryTools(sheets, downloads),
        LocalTutorial provides tutorial,
        LocalAccount provides rememberAccountActions(sheets),
    ) {
        SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransition provides this) {
        Box(Modifier.fillMaxSize()) {
            // Quick crossfade between tabs instead of an instant swap.
            Crossfade(selected, animationSpec = tween(180), label = "tabs") { tab ->
                tabStates.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> HomeScreen()
                        1 -> ChartsScreen()
                        2 -> LibraryScreen()
                        else -> SearchScreen()
                    }
                }
            }
            BottomChrome(
                tabs = Tabs,
                selectedIndex = selected,
                searchSelected = selected == SearchTab,
                onSelect = { selected = it },
                onSearch = { selected = SearchTab },
                onOpenPlayer = { playerOpen = true },
                playerOpen = playerOpen && hasTrack,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            NowPlayingSheet(visible = playerOpen && hasTrack, onDismiss = { playerOpen = false })
            SheetHost(sheets)
            TutorialOverlay(tutorial)
        }
        }
        }
    }
}
