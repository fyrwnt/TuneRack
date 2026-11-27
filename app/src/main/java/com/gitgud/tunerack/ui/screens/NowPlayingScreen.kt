package com.gitgud.tunerack.ui.screens

import com.gitgud.tunerack.ui.components.nowPlayingArtwork
import com.gitgud.tunerack.ui.tutorial.tutorialTarget
import com.gitgud.tunerack.ui.tutorial.HelpButton
import com.gitgud.tunerack.ui.tutorial.AutoTour
import com.gitgud.tunerack.ui.tutorial.Tour
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.playback.Progress
import com.gitgud.tunerack.playback.RepeatMode
import com.gitgud.tunerack.ui.components.Artwork
import com.gitgud.tunerack.ui.components.LocalSheets
import com.gitgud.tunerack.ui.components.SheetRequest
import com.gitgud.tunerack.tuneRack
import androidx.compose.ui.platform.LocalContext
import com.gitgud.tunerack.ui.components.TuneIcon
import com.gitgud.tunerack.ui.components.pressable
import com.gitgud.tunerack.ui.components.softShadow
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The player always sits on dark, artwork-tinted glass, so it uses fixed light text in both themes.
private val OnArt = Color(0xFFF5F5F7)
private val OnArtSecondary = Color(0x99F5F5F7)
private val OnArtTertiary = Color(0x40F5F5F7)

/** Full-screen player that slides up from the mini player; swipe down or press back to close. */
@Composable
fun NowPlayingSheet(visible: Boolean, onDismiss: () -> Unit) {
    // Composed only while shown, so it registers after any screen's own back handling and wins.
    if (visible) BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val dismissDistance = with(density) { 160.dp.toPx() }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(tween(120)),
        exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(280)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, drag.value.roundToInt()) }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                    },
                    onDragStopped = { velocity ->
                        if (drag.value > dismissDistance || velocity > 2500f) onDismiss()
                        else drag.animateTo(0f, spring(stiffness = 500f))
                    },
                )
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            NowPlayingContent(this@AnimatedVisibility)
        }
    }
}

@Composable
private fun NowPlayingContent(visibility: androidx.compose.animation.AnimatedVisibilityScope) {
    val player = LocalPlayerConnection.current
    val state by player.state.collectAsStateWithLifecycle()
    val progress by player.progress().collectAsStateWithLifecycle(Progress(0, 0, 0))
    val track = state.nowPlaying
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val sheets = LocalSheets.current

    Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
        // Backdrop: the cover art, heavily blurred, under a dark scrim (blur needs Android 12+;
        // older versions just get the scrimmed art).
        Crossfade(track?.artworkUrl, animationSpec = tween(600), label = "backdrop") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(90.dp),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x4D000000), Color(0x26000000), Color(0x80000000))),
            ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter)
                        .size(width = 38.dp, height = 5.dp)
                        .background(OnArtTertiary, CircleShape),
                )
                HelpButton(Tour.Player, Modifier.align(Alignment.TopEnd).padding(top = 4.dp), tint = OnArtSecondary)
            }
            AutoTour(Tour.Player, delayMs = 900)

            if (showQueue) {
                QueueView(Modifier.weight(1f))
            } else {
                BigArtwork(track?.artworkUrl, state.isPlaying, Modifier.weight(1f), visibility)
                TrackTitle(track?.title.orEmpty(), state.error ?: track?.artist.orEmpty()) {
                    if (track != null) {
                        Box(Modifier.tutorialTarget("player.like")) { LikeButton(track.toTrack()) }
                        Box(Modifier.tutorialTarget("player.more")) {
                            TransportButton(TuneIcons.More, 22.dp) { sheets.show(SheetRequest.Song(track.toTrack())) }
                        }
                    }
                }
            }

            SeekBar(progress, onSeek = player::seekTo, modifier = Modifier.padding(top = 18.dp).tutorialTarget("player.seek"))

            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(TuneIcons.Previous, 36.dp, onClick = player::previous)
                TransportButton(if (state.isPlaying) TuneIcons.Pause else TuneIcons.Play, 46.dp, onClick = player::togglePlayPause)
                TransportButton(TuneIcons.Forward, 36.dp, enabled = state.hasNext, onClick = player::next)
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.tutorialTarget("player.shuffle")) {
                    ToggleButton(TuneIcons.Shuffle, active = state.shuffle, onClick = player::toggleShuffle)
                }
                Box(Modifier.tutorialTarget("player.repeat")) {
                    ToggleButton(
                        if (state.repeat == RepeatMode.One) TuneIcons.RepeatOne else TuneIcons.Repeat,
                        active = state.repeat != RepeatMode.Off,
                        onClick = player::cycleRepeat,
                    )
                }
                Box(Modifier.tutorialTarget("player.queue")) {
                    ToggleButton(TuneIcons.Queue, active = showQueue, onClick = { showQueue = !showQueue })
                }
            }
        }
    }
}

/** Apple Music detail: the cover sits full size while playing and settles back when paused. */
@Composable
private fun BigArtwork(url: String?, playing: Boolean, modifier: Modifier, visibility: androidx.compose.animation.AnimatedVisibilityScope) {
    val scale by animateFloatAsState(if (playing) 1f else 0.84f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "art")
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight - 24.dp)
        Box(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .softShadow(RoundedCornerShape(14.dp), elevation = 24.dp),
        ) {
            Artwork(url, side, corner = 14.dp, modifier = Modifier.nowPlayingArtwork(visibility))
        }
    }
}

@Composable
private fun TrackTitle(title: String, subtitle: String, actions: @Composable () -> Unit) {
    val type = TuneRackTheme.typography
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
        BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.title3.copy(color = OnArt))
        BasicText(
            subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = type.body.copy(color = OnArtSecondary),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    actions()
    }
}

/** Heart toggle for the playing song; liking adds it to the library. */
@Composable
private fun LikeButton(track: com.gitgud.tunerack.data.Track) {
    val library = LocalContext.current.tuneRack.library
    val liked by library.likedIds.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    val isLiked = track.id in liked
    val scale by animateFloatAsState(if (isLiked) 1f else 0.92f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "heart")
    Box(
        Modifier
            .size(44.dp)
            .pressable(pressedScale = 0.8f) { scope.launch { library.setLiked(track, !isLiked) } },
        contentAlignment = Alignment.Center,
    ) {
        TuneIcon(
            if (isLiked) TuneIcons.HeartFilled else TuneIcons.Heart,
            if (isLiked) OnArt else OnArtSecondary,
            size = 24.dp,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
private fun SeekBar(progress: Progress, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = progress.durationMs
    val fraction = dragFraction ?: if (duration > 0) (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffered = if (duration > 0) (progress.bufferedMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val height by animateDpAsState(if (dragFraction != null) 10.dp else 6.dp, label = "seek")

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { onSeek((it * duration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectTapGestures { onSeek(((it.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color(0x26FFFFFF))) {
                Box(Modifier.fillMaxWidth(buffered).fillMaxHeight().background(Color(0x1FFFFFFF)))
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (dragFraction != null) OnArt else Color(0xCCF5F5F7)))
            }
        }
        val shown = (fraction * duration).toLong()
        Row(Modifier.fillMaxWidth()) {
            val caption = TuneRackTheme.typography.caption.copy(color = OnArtSecondary)
            BasicText(formatTime(shown), style = caption, modifier = Modifier.weight(1f))
            BasicText(if (duration > 0) "-" + formatTime(duration - shown) else "--:--", style = caption)
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, size: androidx.compose.ui.unit.Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size + 28.dp)
            .then(if (enabled) Modifier.pressable(pressedScale = 0.82f, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        TuneIcon(icon, if (enabled) OnArt else OnArtTertiary, size = size)
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Color(0x33FFFFFF) else Color.Transparent, label = "toggle")
    Box(
        Modifier
            .size(44.dp)
            .pressable(pressedScale = 0.88f, onClick = onClick)
            .background(bg, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        TuneIcon(icon, if (active) OnArt else OnArtSecondary, size = 22.dp)
    }
}

/** "Up Next": the rest of the queue in the order it will actually play. */
@Composable
private fun QueueView(modifier: Modifier) {
    val player = LocalPlayerConnection.current
    val state by player.state.collectAsStateWithLifecycle()
    val type = TuneRackTheme.typography
    val track = state.nowPlaying

    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(track?.artworkUrl, 64.dp, corner = 8.dp)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                BasicText(track?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.headline.copy(color = OnArt))
                BasicText(track?.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.subheadline.copy(color = OnArtSecondary))
            }
        }
        Row(Modifier.padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            BasicText("Up Next", style = type.headline.copy(color = OnArt), modifier = Modifier.weight(1f))
            if (state.shuffle) BasicText("Shuffled", style = type.footnote.copy(color = OnArtSecondary))
        }
        if (state.upNext.isEmpty()) {
            BasicText(
                if (state.repeat == RepeatMode.Off) "Nothing queued after this song." else "Repeats when this song ends.",
                style = type.subheadline.copy(color = OnArtSecondary),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.upNext, key = { "q-${it.index}" }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(pressedScale = 0.98f) { player.playQueueEntry(entry.index) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(entry.item.artworkUrl, 44.dp, corner = 6.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        BasicText(entry.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.body.copy(color = OnArt))
                        BasicText(entry.item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.footnote.copy(color = OnArtSecondary))
                    }
                }
            }
            item { Spacer(Modifier.width(1.dp).height(12.dp)) }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
