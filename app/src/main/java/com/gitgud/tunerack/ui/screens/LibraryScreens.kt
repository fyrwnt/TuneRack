package com.gitgud.tunerack.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.library.DownloadEntity
import com.gitgud.tunerack.data.library.toTrack
import com.gitgud.tunerack.ui.components.LocalDownloads
import com.gitgud.tunerack.ui.components.LocalLibraryTools
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.tuneRack
import com.gitgud.tunerack.ui.components.Artwork
import com.gitgud.tunerack.ui.components.EmptyMessage
import com.gitgud.tunerack.ui.components.LargeTitleScreen
import com.gitgud.tunerack.ui.components.LocalSheets
import com.gitgud.tunerack.ui.components.NavigationRow
import com.gitgud.tunerack.ui.components.SectionHeader
import com.gitgud.tunerack.ui.components.SheetRequest
import com.gitgud.tunerack.ui.components.SongCard
import com.gitgud.tunerack.ui.components.SongRow
import com.gitgud.tunerack.ui.components.TuneIcon
import com.gitgud.tunerack.ui.components.pressable
import com.gitgud.tunerack.ui.components.softShadow
import com.gitgud.tunerack.ui.components.songCount
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme

// Routes are plain strings so the back stack survives rotation and process death.
private const val Root = "root"
private const val Songs = "songs"
private const val Playlists = "playlists"
private const val Artists = "artists"
private const val Downloaded = "downloaded"
private const val PlaylistPrefix = "playlist:"
private const val ArtistPrefix = "artist:"

/** Library tab with an iOS-style push/pop stack of its own. */
@Composable
fun LibraryScreen() {
    var stack by rememberSaveable { mutableStateOf(listOf(Root)) }
    val push: (String) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1, onBack = pop)

    val saveable = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            // Pushing keeps the previous screen on the stack; popping has just removed it.
            if (initialState in stack) {
                slideInHorizontally(tween(320)) { it } togetherWith slideOutHorizontally(tween(320)) { -it / 3 }
            } else {
                slideInHorizontally(tween(320)) { -it / 3 } togetherWith slideOutHorizontally(tween(320)) { it }
            }
        },
        label = "library-nav",
    ) { route ->
        saveable.SaveableStateProvider(route) {
            val backLabel = stack.getOrNull(stack.indexOf(route) - 1)?.let(::titleFor).orEmpty()
            when {
                route == Root -> LibraryRoot(push)
                route == Songs -> LikedSongsScreen(pop, backLabel)
                route == Playlists -> PlaylistsScreen(push, pop, backLabel)
                route == Artists -> ArtistsScreen(push, pop, backLabel)
                route == Downloaded -> DownloadedScreen(pop, backLabel)
                route.startsWith(PlaylistPrefix) -> PlaylistScreen(route.removePrefix(PlaylistPrefix).toLong(), pop, backLabel)
                route.startsWith(ArtistPrefix) -> ArtistScreen(route.removePrefix(ArtistPrefix), pop, backLabel)
            }
        }
    }
}

private fun titleFor(route: String): String = when {
    route == Root -> "Library"
    route == Songs -> "Songs"
    route == Playlists -> "Playlists"
    route == Artists -> "Artists"
    route.startsWith(ArtistPrefix) -> route.removePrefix(ArtistPrefix)
    else -> "Back"
}

@Composable
private fun LibraryRoot(push: (String) -> Unit) {
    val library = LocalContext.current.tuneRack.library
    val player = LocalPlayerConnection.current
    val liked by library.likedSongs.collectAsStateWithLifecycle(null)

    val sheets = LocalSheets.current
    LargeTitleScreen(
        "Library",
        trailing = {
            Box(Modifier.size(40.dp).pressable { sheets.show(SheetRequest.LibraryOptions) }, contentAlignment = Alignment.Center) {
                TuneIcon(TuneIcons.More, TuneRackTheme.colors.label, size = 20.dp)
            }
        },
    ) {
        item(key = Playlists) { NavigationRow("Playlists", TuneIcons.Playlists) { push(Playlists) } }
        item(key = Artists) { NavigationRow("Artists", TuneIcons.Artists) { push(Artists) } }
        // Hidden until album data exists (search results don't carry albums yet).
        // item(key = "albums") { NavigationRow("Albums", TuneIcons.Albums) { } }
        item(key = Songs) { NavigationRow("Songs", TuneIcons.Songs) { push(Songs) } }
        item(key = Downloaded) { NavigationRow("Downloaded", TuneIcons.Downloaded) { push(Downloaded) } }

        item(key = "recent-header") { SectionHeader("Recently Added", showChevron = false) }
        val songs = liked ?: return@LargeTitleScreen
        if (songs.isEmpty()) {
            item(key = "empty") {
                EmptyMessage(
                    title = "Nothing here yet",
                    message = "Tap ••• on any song and choose Add to Library.",
                    icon = TuneIcons.Songs,
                )
            }
        } else {
            val recent = songs.take(12)
            recent.chunked(2).forEachIndexed { row, pair ->
                item(key = "recent-$row") {
                    Row(
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        pair.forEach { track ->
                            SongCard(track, Modifier.weight(1f)) { player.playQueue(recent, recent.indexOf(track)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedSongsScreen(pop: () -> Unit, backLabel: String) {
    val library = LocalContext.current.tuneRack.library
    val songs by library.likedSongs.collectAsStateWithLifecycle(null)
    TrackListScreen(
        title = "Songs",
        tracks = songs,
        pop = pop,
        backLabel = backLabel,
        emptyTitle = "No songs yet",
        emptyMessage = "Songs you add to your library show up here.",
    )
}

@Composable
private fun ArtistScreen(artist: String, pop: () -> Unit, backLabel: String) {
    val library = LocalContext.current.tuneRack.library
    val songs by remember(artist) { library.artistSongs(artist) }.collectAsStateWithLifecycle(null)
    TrackListScreen(artist, songs, pop, backLabel, "No songs", "Nothing by $artist is in your library anymore.")
}

/** Shared layout for a titled list of songs with Play and Shuffle buttons. */
@Composable
private fun TrackListScreen(
    title: String,
    tracks: List<Track>?,
    pop: () -> Unit,
    backLabel: String,
    emptyTitle: String,
    emptyMessage: String,
    playlistId: Long? = null,
    header: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val player = LocalPlayerConnection.current
    val sheets = LocalSheets.current
    val state by player.state.collectAsStateWithLifecycle()
    val downloadStates by LocalContext.current.tuneRack.downloads.states.collectAsStateWithLifecycle(emptyMap())

    LargeTitleScreen(title, onBack = pop, backLabel = backLabel, showLargeTitle = header == null, trailing = trailing) {
        if (header != null) item(key = "header") { header() }
        val list = tracks ?: return@LargeTitleScreen
        if (list.isEmpty()) {
            item(key = "empty") { EmptyMessage(emptyTitle, emptyMessage) }
            return@LargeTitleScreen
        }
        item(key = "buttons") { PlayShuffleButtons(list) }
        list.forEachIndexed { index, track ->
            item(key = "t-${track.id}") {
                SongRow(
                    track = track,
                    isCurrent = state.nowPlaying?.mediaId == track.id,
                    onMore = { sheets.show(SheetRequest.Song(track, playlistId)) },
                    download = downloadStates[track.id],
                    onClick = { player.playQueue(list, index) },
                )
            }
        }
    }
}

@Composable
private fun PlayShuffleButtons(tracks: List<Track>) {
    val player = LocalPlayerConnection.current
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillButton("Play", TuneIcons.Play, Modifier.weight(1f)) { player.playQueue(tracks, 0) }
        PillButton("Shuffle", TuneIcons.Shuffle, Modifier.weight(1f)) { player.playShuffled(tracks) }
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = TuneRackTheme.colors
    Row(
        modifier
            .height(46.dp)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .background(colors.fill, RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TuneIcon(icon, colors.label, size = 18.dp)
        BasicText(label, style = TuneRackTheme.typography.headline.copy(color = colors.label), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PlaylistsScreen(push: (String) -> Unit, pop: () -> Unit, backLabel: String) {
    val library = LocalContext.current.tuneRack.library
    val sheets = LocalSheets.current
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val playlists by library.playlists.collectAsStateWithLifecycle(null)

    LargeTitleScreen("Playlists", onBack = pop, backLabel = backLabel) {
        item(key = "new") { NavigationRow("New Playlist…", TuneIcons.Plus) { sheets.show(SheetRequest.NewPlaylist) } }
        val list = playlists ?: return@LargeTitleScreen
        list.forEach { playlist ->
            item(key = "p-${playlist.id}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(pressedScale = 0.98f) { push(PlaylistPrefix + playlist.id) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(playlist.coverUrl, 60.dp, corner = 8.dp)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        BasicText(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.body.copy(color = colors.label))
                        BasicText(songCount(playlist.songCount), style = type.subheadline.copy(color = colors.secondaryLabel))
                    }
                    TuneIcon(TuneIcons.ChevronRight, colors.tertiaryLabel, size = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun PlaylistScreen(id: Long, pop: () -> Unit, backLabel: String) {
    val library = LocalContext.current.tuneRack.library
    val sheets = LocalSheets.current
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val playlist by remember(id) { library.playlist(id) }.collectAsStateWithLifecycle(null)
    val songs by remember(id) { library.playlistSongs(id) }.collectAsStateWithLifecycle(null)

    // Deleted from the options sheet: leave the screen once the row disappears.
    var seen by remember { mutableStateOf(false) }
    if (playlist != null) seen = true
    if (seen && playlist == null) {
        LaunchedEffect(Unit) { pop() }
        return
    }
    val name = playlist?.name.orEmpty()

    TrackListScreen(
        title = name,
        tracks = songs,
        pop = pop,
        backLabel = backLabel,
        emptyTitle = "This playlist is empty",
        emptyMessage = "Tap ••• on any song and choose Add to a Playlist.",
        playlistId = id,
        trailing = {
            Box(
                Modifier.size(40.dp).pressable { sheets.show(SheetRequest.PlaylistOptions(id, name)) },
                contentAlignment = Alignment.Center,
            ) { TuneIcon(TuneIcons.More, colors.label, size = 20.dp) }
        },
        header = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.softShadow(RoundedCornerShape(12.dp), elevation = 16.dp)) {
                    Artwork(songs?.firstOrNull()?.artworkUrl, 230.dp, corner = 12.dp)
                }
                BasicText(
                    name,
                    style = type.title2.copy(color = colors.label, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(top = 18.dp, start = 20.dp, end = 20.dp),
                )
                BasicText(songCount(songs?.size ?: 0), style = type.subheadline.copy(color = colors.secondaryLabel), modifier = Modifier.padding(top = 2.dp))
            }
        },
    )
}

@Composable
private fun ArtistsScreen(push: (String) -> Unit, pop: () -> Unit, backLabel: String) {
    val library = LocalContext.current.tuneRack.library
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val artists by library.artists.collectAsStateWithLifecycle(null)

    LargeTitleScreen("Artists", onBack = pop, backLabel = backLabel) {
        val list = artists ?: return@LargeTitleScreen
        if (list.isEmpty()) {
            item(key = "empty") { EmptyMessage("No artists yet", "Artists of songs in your library show up here.", TuneIcons.Artists) }
        }
        list.forEach { artist ->
            item(key = "a-${artist.name}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(pressedScale = 0.98f) { push(ArtistPrefix + artist.name) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape)) { Artwork(artist.artworkUrl, 52.dp, corner = 26.dp) }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        BasicText(artist.name.ifEmpty { "Unknown Artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.body.copy(color = colors.label))
                        BasicText(songCount(artist.songCount), style = type.subheadline.copy(color = colors.secondaryLabel))
                    }
                    TuneIcon(TuneIcons.ChevronRight, colors.tertiaryLabel, size = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun DownloadedScreen(pop: () -> Unit, backLabel: String) {
    val downloads = LocalContext.current.tuneRack.downloads
    val starter = LocalDownloads.current
    val tools = LocalLibraryTools.current
    val player = LocalPlayerConnection.current
    val sheets = LocalSheets.current
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val folder by downloads.folder.collectAsStateWithLifecycle()
    val entries by downloads.songs.collectAsStateWithLifecycle(null)
    val playing by player.state.collectAsStateWithLifecycle()

    LargeTitleScreen("Downloaded", onBack = pop, backLabel = backLabel) {
        item(key = "folder") {
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(colors.fill, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText("Saved to", style = type.footnote.copy(color = colors.secondaryLabel))
                    BasicText(
                        folder?.let(downloads::folderName) ?: "No folder chosen yet",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = type.headline.copy(color = colors.label),
                    )
                }
                if (folder != null) {
                    BasicText(
                        "Re-scan",
                        style = type.callout.copy(color = colors.label),
                        modifier = Modifier.pressable(pressedScale = 0.94f) { tools.rescan() }.padding(8.dp),
                    )
                }
                BasicText(
                    if (folder == null) "Choose" else "Change",
                    style = type.callout.copy(color = colors.label),
                    modifier = Modifier.pressable(pressedScale = 0.94f) { starter.changeFolder() }.padding(8.dp),
                )
            }
        }
        val list = entries ?: return@LargeTitleScreen
        if (list.isEmpty()) {
            item(key = "empty") {
                EmptyMessage(
                    title = "No downloads yet",
                    message = "Tap \u2022\u2022\u2022 on a song and choose Download. There is no limit.",
                    icon = TuneIcons.Downloaded,
                )
            }
            return@LargeTitleScreen
        }
        val done = list.filter { it.state == DownloadEntity.DONE }.map { it.song.toTrack() }
        val totalMb = list.filter { it.state == DownloadEntity.DONE }.sumOf { it.sizeBytes } / (1024.0 * 1024.0)
        item(key = "summary") {
            BasicText(
                "${songCount(done.size)} \u00b7 ${"%.0f".format(totalMb)} MB" +
                    list.count { it.state == DownloadEntity.QUEUED || it.state == DownloadEntity.RUNNING }
                        .let { if (it > 0) " \u00b7 $it in progress" else "" } +
                    list.count { it.state == DownloadEntity.FAILED }.let { if (it > 0) " \u00b7 $it failed" else "" },
                style = type.subheadline.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (done.isNotEmpty()) item(key = "buttons") { PlayShuffleButtons(done) }
        list.forEach { entry ->
            val track = entry.song.toTrack()
            item(key = "d-${track.id}") {
                SongRow(
                    track = track,
                    isCurrent = playing.nowPlaying?.mediaId == track.id,
                    onMore = { sheets.show(SheetRequest.Song(track)) },
                    download = DownloadEntity(track.id, entry.state, entry.progress, updatedAt = 0, error = entry.error),
                    onClick = {
                        when (entry.state) {
                            DownloadEntity.DONE -> player.playQueue(done, done.indexOf(track))
                            DownloadEntity.FAILED -> starter.download(listOf(track))
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}
