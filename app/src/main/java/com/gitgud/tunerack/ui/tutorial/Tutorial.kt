package com.gitgud.tunerack.ui.tutorial

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.gitgud.tunerack.ui.components.pressable
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import kotlinx.coroutines.delay

/** One spotlight step. [target] is a [tutorialTarget] id; null (or not on screen) shows a centered card. */
data class TourStep(val target: String?, val title: String, val text: String)

/** Every screen's tips. Shown once automatically, and again from that screen's ? button. */
enum class Tour(val steps: List<TourStep>) {
    Welcome(
        listOf(
            TourStep(null, "Welcome to TuneRack", "Music from YouTube Music, with a library that lives on your phone. No account needed. Here's a quick tour."),
            TourStep("tab.home", "Home", "Picks based on what you play, your recent songs, and what's trending today."),
            TourStep("tab.charts", "Charts", "The top songs on Spotify and YouTube. Tap any song to play the chart from there."),
            TourStep("tab.library", "Library", "Your likes, playlists, artists and downloads, stored only on this phone."),
            TourStep("tab.search", "Search", "Find any song on YouTube Music."),
            TourStep("account", "Guest or YouTube", "You're a guest, and everything stays on this phone. Sign in with Google to bring in your YouTube likes and playlists. TuneRack never changes your account."),
            TourStep("miniPlayer", "Now Playing", "Tap here to open the full player. Play, pause and skip right from here."),
            TourStep("help", "Need a hand later?", "Tap ? on any screen to see its tips again."),
        ),
    ),
    Charts(
        listOf(
            TourStep("charts.source", "Spotify or YouTube", "Switch between Spotify's song chart and YouTube's most viewed music videos."),
            TourStep("charts.period", "Daily or weekly", "Every chart comes per day or per week."),
            TourStep("charts.country", "Pick a country", "See what's popular in your country, or worldwide."),
            TourStep("charts.row", "Tap to play", "Plays the chart in order, starting from the song you tapped."),
            TourStep("charts.more", "Wrong song?", "Chart songs are matched to YouTube Music automatically. If one is off, tap ••• and choose another."),
        ),
    ),
    Search(
        listOf(
            TourStep("search.field", "Search YouTube Music", "Type an artist or song, then press search on your keyboard."),
            TourStep("search.more", "Song options", "••• on any song: add it to your Library or a playlist, download it, or play it next."),
        ),
    ),
    Library(
        listOf(
            TourStep("library.playlists", "Playlists", "Make playlists from any song's ••• menu."),
            TourStep("library.artists", "Artists", "Artists of the songs you've added, with all their songs in one place."),
            TourStep("library.songs", "Songs", "Everything you've added to your Library."),
            TourStep("library.downloaded", "Downloads", "Unlimited offline songs, saved as normal music files other apps can play too."),
            TourStep("library.more", "Move, help and feedback", "Export your library to a file and import it on a new phone. Tips and feedback are here too."),
        ),
    ),
    Downloaded(
        listOf(
            TourStep("downloaded.folder", "Download folder", "Choose where songs are saved. Re-scan picks up songs already in the folder."),
            TourStep("downloaded.buttons", "Play offline", "Play or shuffle everything you've downloaded, no connection needed."),
            TourStep(null, "If a download fails", "The reason shows under the song in red. Tap the song to try again."),
        ),
    ),
    SongList(
        listOf(
            TourStep("playlist.buttons", "Play or shuffle", "Shuffle spreads out songs by the same artist, so they don't play back to back."),
            TourStep("songs.more", "Song options", "\u2022\u2022\u2022 on a song: add it to a playlist, download it, play it next, or remove it from your Library."),
        ),
    ),
    Playlist(
        listOf(
            TourStep("playlist.buttons", "Play or shuffle", "Shuffle spreads out songs by the same artist, so they don't play back to back."),
            TourStep("playlist.more", "Playlist options", "Download the whole playlist, rename it, or delete it."),
            TourStep(null, "Remove a song", "Tap ••• on a song, then Remove from This Playlist."),
        ),
    ),
    Player(
        listOf(
            TourStep("player.like", "Like", "Adds the song to your Library."),
            TourStep("player.more", "More options", "Add to a playlist, download, or play next."),
            TourStep("player.seek", "Jump around", "Drag or tap the bar to move through the song."),
            TourStep("player.shuffle", "Shuffle", "Balanced shuffle: the same artist won't play twice in a row when it can be avoided."),
            TourStep("player.repeat", "Repeat", "Tap to switch between off, the whole queue, and this song."),
            TourStep("player.queue", "Up Next", "See what's coming and tap any song to jump to it."),
            TourStep(null, "Close the player", "Swipe down or use the back gesture."),
        ),
    ),
}

@Stable
class TutorialController(context: Context) {
    private val prefs = context.getSharedPreferences("tutorial", Context.MODE_PRIVATE)

    /** On-screen bounds of every registered target, in window coordinates. */
    internal val targets = mutableStateMapOf<String, Rect>()

    var active by mutableStateOf<Tour?>(null)
        private set
    var step by mutableIntStateOf(0)
        private set

    fun start(tour: Tour) {
        step = 0
        active = tour
    }

    /** Shows [tour] if it hasn't been seen, tips aren't turned off, and nothing else is showing. */
    fun autoStart(tour: Tour) {
        if (active != null || prefs.getBoolean(KEY_DISABLED, false) || prefs.getBoolean(seenKey(tour), false)) return
        // The other tours wait until the welcome tour has been seen or skipped.
        if (tour != Tour.Welcome && !prefs.getBoolean(seenKey(Tour.Welcome), false)) return
        start(tour)
    }

    fun next() {
        val tour = active ?: return
        if (step < tour.steps.lastIndex) step++ else finish()
    }

    fun back() {
        if (step > 0) step--
    }

    /** Ends the current tour; it won't show by itself again. */
    fun finish() {
        active?.let { prefs.edit { putBoolean(seenKey(it), true) } }
        active = null
    }

    /** "Skip tutorial" on the welcome tour: no more automatic tips (the ? buttons still work). */
    fun skipAll() {
        prefs.edit { putBoolean(KEY_DISABLED, true) }
        finish()
    }

    /** Replays the welcome tour and lets every screen show its tips once more. */
    fun resetAndStart() {
        prefs.edit { clear() }
        start(Tour.Welcome)
    }

    private fun seenKey(tour: Tour) = "seen_${tour.name}"

    private companion object {
        const val KEY_DISABLED = "disabled"
    }
}

val LocalTutorial = staticCompositionLocalOf<TutorialController> { error("No TutorialController") }

@Composable
fun rememberTutorialController(): TutorialController {
    val context = LocalContext.current.applicationContext
    return remember(context) { TutorialController(context) }
}

/** Marks this element as something a tour can spotlight. Use each id on one element at a time. */
@Composable
fun Modifier.tutorialTarget(id: String): Modifier {
    val tutorial = LocalTutorial.current
    DisposableEffect(tutorial, id) { onDispose { tutorial.targets.remove(id) } }
    return onGloballyPositioned { tutorial.targets[id] = it.boundsInRoot() }
}

/** Shows [tour] automatically the first time the calling screen appears (after it has laid out). */
@Composable
fun AutoTour(tour: Tour, delayMs: Long = 600) {
    val tutorial = LocalTutorial.current
    LaunchedEffect(tour) {
        delay(delayMs)
        tutorial.autoStart(tour)
    }
}

/** Round "?" button that replays [tour]. */
@Composable
fun HelpButton(tour: Tour, modifier: Modifier = Modifier, tint: Color = TuneRackTheme.colors.label) {
    val tutorial = LocalTutorial.current
    Box(
        modifier
            .size(40.dp)
            .pressable(pressedScale = 0.88f) { tutorial.start(tour) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).tutorialTarget("help").border(1.6.dp, tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("?", style = TuneRackTheme.typography.callout.copy(color = tint, fontWeight = FontWeight.Bold))
        }
    }
}

/** Dims the screen, cuts a hole around the current step's target, and shows its caption. */
@Composable
fun TutorialOverlay(controller: TutorialController) {
    val tour = controller.active
    // Composed only while a tour runs, so back ends the tour rather than navigating underneath.
    if (tour != null) BackHandler { controller.finish() }

    AnimatedVisibility(tour != null, enter = fadeIn(), exit = fadeOut()) {
        val current = tour ?: return@AnimatedVisibility
        val step = current.steps[controller.step.coerceIn(current.steps.indices)]
        val density = LocalDensity.current
        val pad = with(density) { 8.dp.toPx() }
        val target = step.target?.let { controller.targets[it] }?.inflate(pad)
        val hole by animateRectAsState(target ?: Rect.Zero, label = "spotlight")
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(controller) { detectTapGestures { controller.next() } },
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawRect(Color.Black.copy(alpha = 0.64f))
                if (target != null && hole.width > 0f) {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = hole.topLeft,
                        size = hole.size,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        blendMode = BlendMode.Clear,
                    )
                }
            }

            var cardHeight by remember { mutableIntStateOf(0) }
            val screenHeight = constraints.maxHeight
            val gap = with(density) { 14.dp.roundToPx() }
            val minY = with(density) { top.roundToPx() + 8.dp.roundToPx() }
            val maxY = screenHeight - cardHeight - with(density) { bottom.roundToPx() + 8.dp.roundToPx() }
            val y = when {
                target == null -> (screenHeight - cardHeight) / 2
                target.center.y < screenHeight / 2 -> target.bottom.toInt() + gap
                else -> target.top.toInt() - gap - cardHeight
            }.coerceIn(minY, maxY.coerceAtLeast(minY))

            Box(
                Modifier
                    .offset { IntOffset(0, y) }
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .onSizeChanged { cardHeight = it.height },
            ) {
                Caption(current, controller.step, step, controller)
            }
        }
    }
}

@Composable
private fun Caption(tour: Tour, index: Int, step: TourStep, controller: TutorialController) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val shape = RoundedCornerShape(18.dp)
    val last = index == tour.steps.lastIndex
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape)
            .background(colors.surface, shape)
            // Taps on the card itself shouldn't advance like taps on the dimmed area do.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(18.dp),
    ) {
        if (tour.steps.size > 1) {
            BasicText("${index + 1} of ${tour.steps.size}", style = type.caption.copy(color = colors.secondaryLabel))
        }
        BasicText(step.title, style = type.title3.copy(color = colors.label), modifier = Modifier.padding(top = 4.dp))
        BasicText(step.text, style = type.subheadline.copy(color = colors.secondaryLabel), modifier = Modifier.padding(top = 4.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val skipLabel = if (tour == Tour.Welcome && index == 0) "Skip tutorial" else "Skip"
            if (!last) {
                BasicText(
                    skipLabel,
                    style = type.callout.copy(color = colors.secondaryLabel),
                    modifier = Modifier
                        .pressable { if (tour == Tour.Welcome && index == 0) controller.skipAll() else controller.finish() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            } else {
                Box(Modifier)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    BasicText(
                        "Back",
                        style = type.callout.copy(color = colors.label),
                        modifier = Modifier.pressable(onClick = controller::back).padding(vertical = 8.dp, horizontal = 10.dp),
                    )
                }
                Box(
                    Modifier
                        .pressable(pressedScale = 0.95f, onClick = controller::next)
                        .background(colors.accent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    BasicText(if (last) "Done" else "Next", style = type.headline.copy(color = colors.background))
                }
            }
        }
    }
}
