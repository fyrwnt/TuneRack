package com.gitgud.tunerack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import dev.chrisbanes.haze.hazeSource

private val InlineBarHeight = 44.dp
private val LargeTitleHeight = 48.dp

/**
 * iOS-style screen: a large title sits just under the status bar and scrolls with the content;
 * once it is gone an inline title and frosted bar fade in at the top.
 */
@Composable
fun LargeTitleScreen(
    title: String,
    listState: LazyListState = rememberLazyListState(),
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    showLargeTitle: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = TuneRackTheme.colors
    val titlePx = with(LocalDensity.current) { LargeTitleHeight.toPx() }
    val collapse by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - titlePx * 0.5f) / (titlePx * 0.5f)).coerceIn(0f, 1f)
        }
    }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = statusBar + if (onBack != null) InlineBarHeight else 8.dp, bottom = navBar + BottomChromeHeight + 24.dp),
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
        ) {
            if (showLargeTitle) item(key = "large-title") {
                Box(
                    Modifier.fillMaxWidth().height(LargeTitleHeight).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        title,
                        style = TuneRackTheme.typography.largeTitle.copy(color = colors.label),
                        modifier = Modifier.graphicsLayer { alpha = 1f - collapse },
                    )
                }
            }
            content()
        }

        Column(Modifier.fillMaxWidth().glassBar { collapse }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(InlineBarHeight),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    title,
                    maxLines = 1,
                    style = TuneRackTheme.typography.headline.copy(color = colors.label),
                    modifier = Modifier.padding(horizontal = 96.dp).graphicsLayer { alpha = collapse },
                )
                if (onBack != null) {
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .pressable(pressedScale = 0.94f, onClick = onBack)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TuneIcon(TuneIcons.ChevronLeft, colors.label, size = 22.dp)
                        BasicText(backLabel, style = TuneRackTheme.typography.body.copy(color = colors.label))
                    }
                }
                if (trailing != null) {
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { trailing() }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .graphicsLayer { alpha = collapse }
                    .background(colors.separator),
            )
        }
    }
}
