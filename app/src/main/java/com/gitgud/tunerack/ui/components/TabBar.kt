package com.gitgud.tunerack.ui.components

import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import com.gitgud.tunerack.ui.tutorial.tutorialTarget
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme

private val BarHeight = 62.dp
private val MiniPlayerHeight = 56.dp
private val ChromeGap = 8.dp

/** Space the floating chrome covers above the navigation bar; scroll content pads by this. */
val BottomChromeHeight = BarHeight + ChromeGap + MiniPlayerHeight + ChromeGap * 2

data class TabItem(val label: String, val icon: ImageVector)

/**
 * Floating bottom chrome in the style of iOS 26 Apple Music: a mini player pill above a glass
 * tab capsule, with Search split off into its own round button.
 */
@Composable
fun BottomChrome(
    tabs: List<TabItem>,
    selectedIndex: Int,
    searchSelected: Boolean,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    onOpenPlayer: () -> Unit,
    playerOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = ChromeGap),
        verticalArrangement = Arrangement.spacedBy(ChromeGap),
    ) {
        MiniPlayer(onOpenPlayer, playerOpen)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TabCapsule(tabs, if (searchSelected) -1 else selectedIndex, onSelect, Modifier.weight(1f))
            SearchButton(searchSelected, onSearch)
        }
    }
}

@Composable
private fun TabCapsule(tabs: List<TabItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = TuneRackTheme.colors
    Row(
        modifier
            .height(BarHeight)
            .glassSurface(CircleShape)
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val pill by animateColorAsState(if (selected) colors.fill else colors.fill.copy(alpha = 0f), label = "pill")
            val tint by animateColorAsState(if (selected) colors.label else colors.secondaryLabel, label = "tint")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .tutorialTarget("tab.${item.label.lowercase()}")
                    .pressable(role = Role.Tab, pressedScale = 0.92f) { onSelect(index) }
                    .background(pill, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TuneIcon(item.icon, tint, size = 23.dp)
                BasicText(
                    item.label,
                    style = TuneRackTheme.typography.tabLabel.copy(color = tint),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchButton(selected: Boolean, onClick: () -> Unit) {
    val colors = TuneRackTheme.colors
    val tint by animateColorAsState(if (selected) colors.label else colors.secondaryLabel, label = "tint")
    val pill by animateColorAsState(if (selected) colors.fill else colors.fill.copy(alpha = 0f), label = "pill")
    Box(
        Modifier
            .size(BarHeight)
            .tutorialTarget("tab.search")
            .glassSurface(CircleShape)
            .pressable(role = Role.Tab, pressedScale = 0.9f, onClick = onClick)
            .padding(4.dp)
            .background(pill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        TuneIcon(TuneIcons.Search, tint, size = 24.dp)
    }
}

/** Apple Music style mini player pill; shows "Not Playing" until something is loaded. */
@Composable
private fun MiniPlayer(onOpen: () -> Unit, playerOpen: Boolean) {
    val colors = TuneRackTheme.colors
    val connection = LocalPlayerConnection.current
    val state by connection.state.collectAsStateWithLifecycle()
    val track = state.nowPlaying
    Row(
        Modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .tutorialTarget("miniPlayer")
            .then(if (track != null) Modifier.pressable(pressedScale = 0.98f, onClick = onOpen) else Modifier)
            .glassSurface(RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hidden while the full player is open: its cover "is" this one, grown (shared element).
        MiniArtwork(track?.artworkUrl, visible = !playerOpen)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            BasicText(
                track?.title ?: "Not Playing",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TuneRackTheme.typography.callout.copy(color = if (track == null) colors.secondaryLabel else colors.label),
            )
            val subtitle = state.error ?: track?.artist
            if (!subtitle.isNullOrEmpty()) {
                BasicText(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TuneRackTheme.typography.footnote.copy(color = colors.secondaryLabel),
                )
            }
        }
        val canControl = track != null
        Box(
            Modifier
                .size(44.dp)
                .then(if (canControl) Modifier.pressable(pressedScale = 0.85f) { connection.togglePlayPause() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TuneIcon(
                if (state.isPlaying) TuneIcons.Pause else TuneIcons.Play,
                when {
                    !canControl -> colors.tertiaryLabel
                    state.isBuffering -> colors.secondaryLabel
                    else -> colors.label
                },
                size = 22.dp,
            )
        }
        Box(
            Modifier
                .padding(end = 2.dp)
                .size(44.dp)
                .then(if (state.hasNext) Modifier.pressable(pressedScale = 0.85f) { connection.next() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TuneIcon(TuneIcons.Forward, if (state.hasNext) colors.label else colors.tertiaryLabel, size = 24.dp)
        }
    }
}

@Composable
private fun MiniArtwork(url: String?, visible: Boolean) {
    Box(Modifier.size(40.dp)) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Artwork(url, 40.dp, corner = 8.dp, modifier = Modifier.nowPlayingArtwork(this))
        }
    }
}
