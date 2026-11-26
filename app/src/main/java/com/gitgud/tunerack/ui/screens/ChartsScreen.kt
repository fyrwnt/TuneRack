package com.gitgud.tunerack.ui.screens

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.charts.Chart
import com.gitgud.tunerack.data.charts.ChartEntry
import com.gitgud.tunerack.data.charts.ChartCountry
import com.gitgud.tunerack.data.charts.ChartKind
import com.gitgud.tunerack.data.charts.ChartSource
import com.gitgud.tunerack.playback.LocalPlayerConnection
import com.gitgud.tunerack.playback.PlayerConnection
import com.gitgud.tunerack.tuneRack
import com.gitgud.tunerack.ui.components.Artwork
import com.gitgud.tunerack.ui.components.ChartRow
import com.gitgud.tunerack.ui.components.EmptyMessage
import com.gitgud.tunerack.ui.components.LargeTitleScreen
import com.gitgud.tunerack.ui.components.LocalSheets
import com.gitgud.tunerack.ui.components.SectionHeader
import com.gitgud.tunerack.ui.components.SegmentedControl
import com.gitgud.tunerack.ui.components.SheetRequest
import com.gitgud.tunerack.ui.components.Spinner
import com.gitgud.tunerack.ui.components.TuneIcon
import com.gitgud.tunerack.ui.components.pressable
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

private const val SHOWN = 100

sealed interface ChartState {
    data object Loading : ChartState
    data class Loaded(val chart: Chart) : ChartState
    data class Failed(val message: String) : ChartState
}

class ChartsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.tuneRack.charts

    private val _charts = MutableStateFlow<Map<ChartSource, ChartState>>(emptyMap())
    val charts: StateFlow<Map<ChartSource, ChartState>> = _charts.asStateFlow()

    private val _countries = MutableStateFlow<List<ChartCountry>>(emptyList())
    val countries: StateFlow<List<ChartCountry>> = _countries.asStateFlow()

    fun loadCountries() {
        if (_countries.value.isNotEmpty()) return
        viewModelScope.launch { runCatching { repository.countries() }.onSuccess { _countries.value = it } }
    }

    val matches: StateFlow<Map<String, Track>> =
        repository.matches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Rank of the entry being looked up on YouTube Music after a tap, for its row spinner. */
    private val _resolving = MutableStateFlow<Pair<ChartSource, Int>?>(null)
    val resolving: StateFlow<Pair<ChartSource, Int>?> = _resolving.asStateFlow()

    fun load(kind: ChartSource) {
        if (_charts.value[kind] is ChartState.Loaded) return
        _charts.update { it + (kind to ChartState.Loading) }
        viewModelScope.launch {
            val state = runCatching { repository.chart(kind) }.fold(
                onSuccess = { ChartState.Loaded(it.copy(entries = it.entries.take(SHOWN))) },
                onFailure = {
                    ChartState.Failed(if (it is IOException) "Check your connection and try again." else "kworb.net returned something unexpected.")
                },
            )
            _charts.update { it + (kind to state) }
            // Match Spotify songs to YouTube Music in the background, top of the chart first.
            if (state is ChartState.Loaded) repository.matchAll(state.chart.entries)
        }
    }

    fun retry(kind: ChartSource) {
        _charts.update { it - kind }
        load(kind)
    }

    /** Plays the chart from [entry] on, using every entry that has a playable song. */
    fun play(kind: ChartSource, entry: ChartEntry, player: PlayerConnection) {
        val chart = (_charts.value[kind] as? ChartState.Loaded)?.chart ?: return
        viewModelScope.launch {
            _resolving.value = kind to entry.rank
            val tapped = runCatching { repository.trackFor(entry) }.getOrNull()
            _resolving.value = null
            if (tapped == null) return@launch
            val known = matches.value
            val queue = chart.entries.mapNotNull { e -> if (e == entry) tapped else trackOf(e, known) }
            player.playQueue(queue, queue.indexOf(tapped).coerceAtLeast(0))
        }
    }

    fun trackOf(entry: ChartEntry, known: Map<String, Track>): Track? =
        entry.videoId?.let { Track(it, entry.title, entry.artist, 0, "https://i.ytimg.com/vi/$it/hqdefault.jpg") }
            ?: entry.spotifyTrackId?.let(known::get)
}

@Composable
fun ChartsScreen(viewModel: ChartsViewModel = viewModel()) {
    var source by rememberSaveable { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableIntStateOf(0) }
    var country by rememberSaveable { mutableStateOf(ChartSource.GLOBAL) }
    val kind = when {
        source == 1 -> ChartSource(ChartKind.YouTube)
        period == 1 -> ChartSource(ChartKind.SpotifyWeekly, country)
        else -> ChartSource(ChartKind.SpotifyDaily, country)
    }
    LaunchedEffect(kind) { viewModel.load(kind) }
    LaunchedEffect(Unit) { viewModel.loadCountries() }
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val countryName = countries.firstOrNull { it.code == country }?.name ?: if (country == ChartSource.GLOBAL) "Global" else country.uppercase()

    val charts by viewModel.charts.collectAsStateWithLifecycle()
    val matches by viewModel.matches.collectAsStateWithLifecycle()
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()
    val player = LocalPlayerConnection.current
    val playing by player.state.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current
    val colors = TuneRackTheme.colors

    LargeTitleScreen("Charts") {
        item(key = "source") {
            SegmentedControl(
                options = listOf("Spotify", "YouTube"),
                selected = source,
                onSelect = { source = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (source == 0) {
            item(key = "period") {
                SegmentedControl(
                    options = listOf("Daily", "Weekly"),
                    selected = period,
                    onSelect = { period = it },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
                )
            }
            item(key = "country") {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .pressable(pressedScale = 0.97f) {
                            if (countries.isNotEmpty()) sheets.show(SheetRequest.Countries(countries, country) { country = it })
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("Country", style = TuneRackTheme.typography.subheadline.copy(color = colors.secondaryLabel))
                    BasicText(
                        countryName,
                        style = TuneRackTheme.typography.subheadline.copy(color = colors.label, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    TuneIcon(TuneIcons.ChevronRight, colors.tertiaryLabel, size = 14.dp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        when (val state = charts[kind] ?: ChartState.Loading) {
            ChartState.Loading -> {
                item(key = "header-loading") { SectionHeader(if (kind.kind == ChartKind.YouTube) "Top Music Videos" else "Top Songs", showChevron = false) }
                items(12, key = { "loading-$it" }) { ChartRow(rank = it + 1) }
            }
            is ChartState.Failed -> item(key = "failed") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyMessage("Couldn't load the chart", state.message)
                    BasicText(
                        "Try Again",
                        style = TuneRackTheme.typography.headline.copy(color = colors.label),
                        modifier = Modifier.pressable { viewModel.retry(kind) }.padding(12.dp),
                    )
                }
            }
            is ChartState.Loaded -> {
                val chart = state.chart
                item(key = "header-${chart.source}") {
                    SectionHeader(
                        if (kind.kind == ChartKind.YouTube) "Top Music Videos" else "Top Songs",
                        showChevron = false,
                        subtitle = when (kind.kind) {
                            ChartKind.SpotifyDaily -> "$countryName · Daily · ${chart.period}"
                            ChartKind.SpotifyWeekly -> "$countryName · Week of ${chart.period}"
                            ChartKind.YouTube -> "Global · Past 24 hours · ${chart.period}"
                        },
                    )
                }
                chart.entries.forEach { entry ->
                    item(key = "${chart.source}-${entry.rank}") {
                        val track = viewModel.trackOf(entry, matches)
                        ChartEntryRow(
                            entry = entry,
                            artworkUrl = track?.artworkUrl,
                            playsLabel = playsLabel(entry.plays, if (kind.kind == ChartKind.YouTube) "views" else "streams"),
                            isCurrent = track != null && playing.nowPlaying?.mediaId == track.id,
                            loading = resolving == kind to entry.rank,
                            onMore = track?.let { t -> { sheets.show(SheetRequest.Song(t, chartEntry = entry)) } },
                            onClick = { viewModel.play(kind, entry, player) },
                        )
                    }
                }
                item(key = "credit") {
                    BasicText(
                        "Chart data from kworb.net",
                        style = TuneRackTheme.typography.footnote.copy(color = colors.tertiaryLabel, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartEntryRow(
    entry: ChartEntry,
    artworkUrl: String?,
    playsLabel: String,
    isCurrent: Boolean,
    loading: Boolean,
    onMore: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) colors.fill else colors.background, label = "row")
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(interaction, indication = null, onClick = onClick)
            .height(68.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(entry.rank.toString(), style = type.headline.copy(color = colors.label))
            BasicText(changeLabel(entry.change), style = type.caption.copy(color = colors.tertiaryLabel, fontWeight = FontWeight.Medium))
        }
        Box(Modifier.padding(start = 8.dp)) {
            Artwork(artworkUrl, 50.dp, corner = 6.dp)
            if (loading) {
                Box(Modifier.size(50.dp).background(colors.fill), contentAlignment = Alignment.Center) { Spinner() }
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            BasicText(
                entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = type.body.copy(color = colors.label, fontWeight = if (isCurrent) FontWeight.SemiBold else null),
            )
            BasicText(
                listOf(entry.artist, playsLabel).filter { it.isNotEmpty() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = type.subheadline.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            Modifier.size(40.dp).then(if (onMore != null) Modifier.pressable(onClick = onMore) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TuneIcon(TuneIcons.More, if (onMore != null) colors.secondaryLabel else colors.tertiaryLabel, size = 20.dp)
        }
    }
}

private fun changeLabel(change: String): String = when {
    change.startsWith("+") -> "▲" + change.drop(1)
    change.startsWith("-") -> "▼" + change.drop(1)
    change == "=" -> "–"
    else -> change.uppercase()
}

private fun playsLabel(plays: Long, unit: String): String = when {
    plays >= 1_000_000_000 -> "%.1fB $unit".format(plays / 1e9)
    plays >= 1_000_000 -> "%.1fM $unit".format(plays / 1e6)
    plays >= 1_000 -> "%.0fK $unit".format(plays / 1e3)
    plays > 0 -> "$plays $unit"
    else -> ""
}
