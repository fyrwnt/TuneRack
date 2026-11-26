package com.gitgud.tunerack.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.charts.ChartCountry
import com.gitgud.tunerack.data.charts.ChartEntry
import com.gitgud.tunerack.data.library.DownloadEntity
import com.gitgud.tunerack.data.library.LibraryRepository
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.tuneRack
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SheetRequest {
    /** Song menu. [playlistId] adds "Remove from This Playlist" when opened inside a playlist. */
    data class Song(val track: Track, val playlistId: Long? = null, val chartEntry: ChartEntry? = null) : SheetRequest
    data object NewPlaylist : SheetRequest
    data class PlaylistOptions(val id: Long, val name: String) : SheetRequest
    data object LibraryOptions : SheetRequest
    class Countries(val countries: List<ChartCountry>, val selected: String, val onPick: (String) -> Unit) : SheetRequest
}

/** App-wide host for action sheets and the confirmation HUD, so any screen can open them. */
@Stable
class SheetController {
    var request by mutableStateOf<SheetRequest?>(null)
        private set
    var hud by mutableStateOf<String?>(null)
        private set

    fun show(request: SheetRequest) { this.request = request }
    fun dismiss() { request = null }
    fun flash(message: String) { hud = message }
    internal fun clearHud() { hud = null }
}

val LocalSheets = staticCompositionLocalOf<SheetController> { error("No SheetController") }

private val Destructive = Color(0xFFE5483D)

private enum class Mode { Actions, PickPlaylist, NewPlaylist, Rename, ConfirmDelete, FixMatch }

@Composable
fun SheetHost(controller: SheetController) {
    val request = controller.request
    // Keep showing the last request while the sheet animates out.
    var shown by remember { mutableStateOf<SheetRequest?>(null) }
    if (request != null) shown = request

    var mode by remember(request) { mutableStateOf(Mode.Actions) }

    BackHandler(enabled = request != null) { controller.dismiss() }

    // Writes are launched from the host, which lives as long as the activity, so closing the sheet
    // never cancels a save half-way.
    val context = LocalContext.current
    val hostScope = rememberCoroutineScope()
    val actions = remember(context, hostScope) { LibraryActions(context.tuneRack.library, hostScope) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(request != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(220))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { controller.dismiss() },
            )
        }
        AnimatedVisibility(
            request != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .background(TuneRackTheme.colors.surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 5.dp)
                        .background(TuneRackTheme.colors.fill, CircleShape),
                )
                when (val r = shown) {
                    is SheetRequest.Song -> SongSheet(r, mode, onMode = { mode = it }, controller, actions)
                    is SheetRequest.NewPlaylist -> NameField(title = "New Playlist", initial = "", action = "Create") { name ->
                        controller.dismiss()
                        controller.flash("Created “$name”")
                        actions.launch { createPlaylist(name) }
                    }
                    is SheetRequest.PlaylistOptions -> PlaylistOptionsSheet(r, mode, onMode = { mode = it }, controller, actions)
                    is SheetRequest.LibraryOptions -> LibraryOptionsSheet(controller)
                    is SheetRequest.Countries -> CountrySheet(r, controller)
                    null -> {}
                }
            }
        }
        Hud(controller)
    }
}

private class LibraryActions(val library: LibraryRepository, private val scope: CoroutineScope) {
    fun launch(block: suspend LibraryRepository.() -> Unit) {
        scope.launch { library.block() }
    }

    fun launchIo(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

@Composable
private fun NameField(title: String, initial: String, action: String, onSubmit: (name: String) -> Unit) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val state = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        state.setTextAndPlaceCursorAtEnd(initial)
        focus.requestFocus()
    }
    val valid = state.text.isNotBlank()
    val submit = { if (valid) onSubmit(state.text.toString().trim()) }

    Column(Modifier.padding(horizontal = 20.dp)) {
        BasicText(title, style = type.title3.copy(color = colors.label), modifier = Modifier.padding(vertical = 10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(colors.fill, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.text.isEmpty()) BasicText("Playlist name", style = type.body.copy(color = colors.tertiaryLabel))
            BasicTextField(
                state = state,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.label),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                onKeyboardAction = { submit() },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(48.dp)
                .background(if (valid) colors.accent else colors.fill, RoundedCornerShape(12.dp))
                .then(if (valid) Modifier.pressable(pressedScale = 0.97f) { submit() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(action, style = type.headline.copy(color = if (valid) colors.background else colors.tertiaryLabel))
        }
    }
}

@Composable
private fun SongSheet(request: SheetRequest.Song, mode: Mode, onMode: (Mode) -> Unit, controller: SheetController, actions: LibraryActions) {
    val liked by actions.library.likedIds.collectAsStateWithLifecycle(emptySet())
    val downloads = LocalContext.current.tuneRack.downloads
    val downloadStates by downloads.states.collectAsStateWithLifecycle(emptyMap())
    val starter = LocalDownloads.current
    val player = LocalPlayerConnection.current
    val track = request.track

    when (mode) {
        Mode.PickPlaylist -> PlaylistPicker(
            onNew = { onMode(Mode.NewPlaylist) },
            onPick = { id, name ->
                controller.dismiss()
                actions.launch {
                    val added = addToPlaylist(id, track)
                    controller.flash(if (added) "Added to “$name”" else "Already in “$name”")
                }
            },
        )
        Mode.FixMatch -> request.chartEntry?.let { entry -> FixMatchSheet(entry, track, controller) }
        Mode.NewPlaylist -> NameField("New Playlist", "", "Create and Add") { name ->
            controller.dismiss()
            actions.launch {
                addToPlaylist(createPlaylist(name), track)
                controller.flash("Added to “$name”")
            }
        }
        else -> Column {
            SheetHeader(track)
            val isLiked = track.id in liked
            ActionRow(if (isLiked) "Remove from Library" else "Add to Library", if (isLiked) TuneIcons.HeartFilled else TuneIcons.Heart) {
                controller.dismiss()
                actions.launch {
                    setLiked(track, !isLiked)
                    controller.flash(if (isLiked) "Removed from Library" else "Added to Library")
                }
            }
            ActionRow("Add to a Playlist…", TuneIcons.Playlists) { onMode(Mode.PickPlaylist) }
            if (request.chartEntry?.spotifyTrackId != null) {
                ActionRow("Wrong Song? Choose Another…", TuneIcons.Search) { onMode(Mode.FixMatch) }
            }
            when (downloadStates[track.id]?.state) {
                null, DownloadEntity.FAILED -> ActionRow(
                    if (downloadStates[track.id]?.state == DownloadEntity.FAILED) "Retry Download" else "Download",
                    TuneIcons.Downloaded,
                ) {
                    controller.dismiss()
                    starter.download(listOf(track))
                }
                DownloadEntity.DONE -> ActionRow("Remove Download", TuneIcons.RemoveCircle, destructive = true) {
                    controller.dismiss()
                    actions.launchIo { downloads.remove(track.id) }
                    controller.flash("Download Removed")
                }
                else -> ActionRow("Cancel Download", TuneIcons.RemoveCircle) {
                    controller.dismiss()
                    actions.launchIo { downloads.remove(track.id) }
                }
            }
            ActionRow("Play Next", TuneIcons.PlayNext) {
                controller.dismiss(); player.playNext(track); controller.flash("Playing Next")
            }
            ActionRow("Play Last", TuneIcons.PlayLast) {
                controller.dismiss(); player.playLast(track); controller.flash("Added to Queue")
            }
            request.playlistId?.let { playlistId ->
                ActionRow("Remove from This Playlist", TuneIcons.RemoveCircle, destructive = true) {
                    controller.dismiss()
                    actions.launch { removeFromPlaylist(playlistId, track.id) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistOptionsSheet(request: SheetRequest.PlaylistOptions, mode: Mode, onMode: (Mode) -> Unit, controller: SheetController, actions: LibraryActions) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    when (mode) {
        Mode.Rename -> NameField("Rename Playlist", request.name, "Save") { name ->
            controller.dismiss()
            actions.launch { renamePlaylist(request.id, name) }
        }
        Mode.ConfirmDelete -> Column(Modifier.padding(horizontal = 20.dp)) {
            BasicText("Delete “${request.name}”?", style = type.title3.copy(color = colors.label), modifier = Modifier.padding(top = 10.dp))
            BasicText(
                "The playlist is removed from your library. Songs you added to your library stay.",
                style = type.subheadline.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            ActionRow("Delete Playlist", TuneIcons.Trash, destructive = true, inset = 0.dp) {
                controller.dismiss()
                actions.launch { deletePlaylist(request.id) }
            }
            ActionRow("Cancel", null, inset = 0.dp) { controller.dismiss() }
        }
        else -> Column {
            BasicText(
                request.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = type.headline.copy(color = colors.label),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            val starter = LocalDownloads.current
            ActionRow("Download Playlist", TuneIcons.Downloaded) {
                controller.dismiss()
                actions.launch { starter.download(playlistSongsOnce(request.id)) }
            }
            ActionRow("Rename", TuneIcons.Pencil) { onMode(Mode.Rename) }
            ActionRow("Delete Playlist", TuneIcons.Trash, destructive = true) { onMode(Mode.ConfirmDelete) }
        }
    }
}

@Composable
private fun LibraryOptionsSheet(controller: SheetController) {
    val tools = LocalLibraryTools.current
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    Column {
        BasicText("Library", style = type.headline.copy(color = colors.label), modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        ActionRow("Export Library\u2026", TuneIcons.PlayLast) { controller.dismiss(); tools.export() }
        ActionRow("Import Library\u2026", TuneIcons.PlayNext) { controller.dismiss(); tools.import() }
        ActionRow("Re-scan Download Folder", TuneIcons.Downloaded) { controller.dismiss(); tools.rescan() }
        BasicText(
            "Export saves your songs, likes, playlists and history to a file. To move to a new phone, copy that file and your download folder, then import and re-scan.",
            style = type.footnote.copy(color = colors.secondaryLabel),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/** Lists other YouTube Music results for a Spotify chart song; the pick replaces the saved match. */
@Composable
private fun FixMatchSheet(entry: ChartEntry, current: Track, controller: SheetController) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val charts = LocalContext.current.tuneRack.charts
    val scope = rememberCoroutineScope()
    val candidates by produceState<List<Track>?>(null, entry) { value = runCatching { charts.candidates(entry) }.getOrDefault(emptyList()) }
    Column {
        BasicText("Choose the Right Song", style = type.title3.copy(color = colors.label), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
        BasicText(
            "For \u201c${entry.title}\u201d by ${entry.artist}",
            style = type.subheadline.copy(color = colors.secondaryLabel),
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )
        val list = candidates
        if (list == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Spinner(24.dp) }
            return@Column
        }
        if (list.isEmpty()) {
            BasicText("No other songs found.", style = type.body.copy(color = colors.secondaryLabel), modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(list, key = { it.id }) { candidate ->
                PressRow(onClick = {
                    controller.dismiss()
                    scope.launch {
                        charts.setMatch(entry, candidate)
                        controller.flash("Match Updated")
                    }
                }) {
                    Artwork(candidate.artworkUrl, 44.dp, corner = 6.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        BasicText(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.body.copy(color = colors.label))
                        BasicText(candidate.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.footnote.copy(color = colors.secondaryLabel))
                    }
                    if (candidate.id == current.id) TuneIcon(TuneIcons.Checkmark, colors.label, size = 18.dp)
                }
            }
        }
    }
}

@Composable
private fun CountrySheet(request: SheetRequest.Countries, controller: SheetController) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    Column {
        BasicText("Spotify Chart Country", style = type.title3.copy(color = colors.label), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            items(request.countries, key = { it.code }) { country ->
                PressRow(onClick = { controller.dismiss(); request.onPick(country.code) }) {
                    BasicText(country.name, style = type.body.copy(color = colors.label), modifier = Modifier.weight(1f))
                    if (country.code == request.selected) TuneIcon(TuneIcons.Checkmark, colors.label, size = 18.dp)
                }
            }
        }
    }
}

@Composable
private fun PlaylistPicker(onNew: () -> Unit, onPick: (Long, String) -> Unit) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val library = LocalContext.current.tuneRack.library
    val playlists by library.playlists.collectAsStateWithLifecycle(emptyList())
    Column {
        BasicText("Add to Playlist", style = type.title3.copy(color = colors.label), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        ActionRow("New Playlist…", TuneIcons.Plus, onClick = onNew)
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                PressRow(onClick = { onPick(playlist.id, playlist.name) }) {
                    Artwork(playlist.coverUrl, 44.dp, corner = 6.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        BasicText(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.body.copy(color = colors.label))
                        BasicText(songCount(playlist.songCount), style = type.footnote.copy(color = colors.secondaryLabel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(track: Track) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.artworkUrl, 48.dp, corner = 6.dp)
        Column(Modifier.padding(start = 12.dp)) {
            BasicText(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.headline.copy(color = colors.label))
            BasicText(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.subheadline.copy(color = colors.secondaryLabel))
        }
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector?,
    destructive: Boolean = false,
    inset: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit,
) {
    val colors = TuneRackTheme.colors
    val tint = if (destructive) Destructive else colors.label
    PressRow(onClick = onClick, horizontal = inset) {
        BasicText(label, style = TuneRackTheme.typography.body.copy(color = tint), modifier = Modifier.weight(1f))
        if (icon != null) TuneIcon(icon, tint, size = 22.dp)
    }
}

@Composable
private fun PressRow(onClick: () -> Unit, horizontal: androidx.compose.ui.unit.Dp = 20.dp, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = TuneRackTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) colors.fill else colors.surface.copy(alpha = 0f), label = "press")
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(interaction, indication = null, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = horizontal, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Brief centered confirmation, like Apple Music's "Added to Library" HUD. */
@Composable
private fun Hud(controller: SheetController) {
    val message = controller.hud
    var shown by remember { mutableStateOf("") }
    if (message != null) shown = message
    LaunchedEffect(message) {
        if (message != null) {
            delay(1400)
            controller.clearHud()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(message != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.95f)) {
            Box(
                Modifier
                    .padding(horizontal = 40.dp)
                    .widthIn(max = 300.dp)
                    .glassSurface(RoundedCornerShape(16.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                BasicText(
                    shown,
                    style = TuneRackTheme.typography.headline.copy(color = TuneRackTheme.colors.label, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"
