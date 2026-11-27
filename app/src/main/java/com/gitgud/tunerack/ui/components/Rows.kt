package com.gitgud.tunerack.ui.components

import com.gitgud.tunerack.ui.tutorial.tutorialTarget
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.library.DownloadEntity
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme

/** Apple Music library row: icon, title, chevron, hairline inset to the text. */
@Composable
fun NavigationRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = TuneRackTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) colors.fill else colors.background, label = "row")
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TuneIcon(icon, colors.label, size = 24.dp)
            BasicText(
                title,
                style = TuneRackTheme.typography.body.copy(color = colors.label, fontSize = 19.sp, lineHeight = 24.sp),
                modifier = Modifier.padding(start = 16.dp).weight(1f),
            )
            TuneIcon(TuneIcons.ChevronRight, colors.tertiaryLabel, size = 16.dp)
        }
        Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
    }
}

/** Section title, optionally followed by a chevron like Apple Music's "See All" headers. */
@Composable
fun SectionHeader(title: String, showChevron: Boolean = true, subtitle: String? = null) {
    val colors = TuneRackTheme.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = TuneRackTheme.typography.title2.copy(color = colors.label))
            if (showChevron) {
                TuneIcon(TuneIcons.ChevronRight, colors.tertiaryLabel, size = 18.dp, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (subtitle != null) {
            BasicText(
                subtitle,
                style = TuneRackTheme.typography.subheadline.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Faint text placeholder used until real titles load. */
@Composable
fun TextLine(width: Dp, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(
        modifier
            .width(width)
            .height(height)
            .background(TuneRackTheme.colors.fill, RoundedCornerShape(height / 2)),
    )
}

/** Square album card: artwork with a soft shadow, then two text lines. */
@Composable
fun AlbumCard(size: Dp) {
    Column(Modifier.width(size).pressable {}) {
        ArtworkPlaceholder(
            size,
            corner = 10.dp,
            modifier = Modifier.softShadow(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(10.dp))
        TextLine(size * 0.72f)
        Spacer(Modifier.height(7.dp))
        TextLine(size * 0.45f, height = 8.dp)
    }
}

/** Chart row: rank, artwork, two text lines, overflow button. */
@Composable
fun ChartRow(rank: Int) {
    val colors = TuneRackTheme.colors
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            rank.toString(),
            style = TuneRackTheme.typography.headline.copy(color = colors.secondaryLabel, textAlign = TextAlign.Center),
            modifier = Modifier.width(26.dp),
        )
        ArtworkPlaceholder(50.dp, corner = 6.dp, modifier = Modifier.padding(start = 10.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            TextLine(150.dp)
            Spacer(Modifier.size(8.dp))
            TextLine(96.dp, height = 8.dp)
        }
        Box(Modifier.size(40.dp).pressable {}, contentAlignment = Alignment.Center) {
            TuneIcon(TuneIcons.More, colors.secondaryLabel, size = 20.dp)
        }
    }
}

@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = TuneRackTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(colors.fill, RoundedCornerShape(10.dp))
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            val bg by animateColorAsState(if (active) colors.segment else colors.segment.copy(alpha = 0f), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .then(if (active) Modifier.shadow(3.dp, RoundedCornerShape(8.dp)) else Modifier)
                    .background(bg, RoundedCornerShape(8.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    option,
                    style = TuneRackTheme.typography.footnote.copy(
                        color = if (active) colors.label else colors.secondaryLabel,
                        fontWeight = TuneRackTheme.typography.headline.fontWeight,
                    ),
                )
            }
        }
    }
}

@Composable
fun EmptyMessage(title: String, message: String, icon: ImageVector? = null) {
    val colors = TuneRackTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            TuneIcon(icon, colors.tertiaryLabel, size = 40.dp, modifier = Modifier.padding(bottom = 14.dp))
        }
        BasicText(title, style = TuneRackTheme.typography.title3.copy(color = colors.label))
        BasicText(
            message,
            style = TuneRackTheme.typography.subheadline.copy(color = colors.secondaryLabel, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Search result / song list row: artwork, title, artist and length, now-playing highlight. */
@Composable
fun SongRow(
    track: Track,
    isCurrent: Boolean,
    onMore: () -> Unit,
    download: DownloadEntity? = null,
    /** Tutorial target id for this row's ••• button (set on one row only). */
    moreTarget: String? = null,
    onClick: () -> Unit,
) {
    val colors = TuneRackTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) colors.fill else colors.background, label = "row")
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(interaction, indication = null, onClick = onClick)
            .height(66.dp)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artworkUrl, 50.dp, corner = 6.dp)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            BasicText(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TuneRackTheme.typography.body.copy(
                    color = colors.label,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                ),
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (download != null) {
                    Box(Modifier.padding(end = 5.dp)) { DownloadIndicator(download, size = 13.dp) }
                }
                val failure = download?.error?.takeIf { download.state == DownloadEntity.FAILED }
                BasicText(
                    failure ?: listOfNotNull(track.artist.ifEmpty { null }, formatDuration(track.durationSeconds)).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TuneRackTheme.typography.subheadline.copy(color = if (failure != null) Color(0xFFE5483D) else colors.secondaryLabel),
                )
            }
        }
        Box(
            Modifier.size(40.dp).then(if (moreTarget != null) Modifier.tutorialTarget(moreTarget) else Modifier).pressable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            TuneIcon(TuneIcons.More, colors.secondaryLabel, size = 20.dp)
        }
    }
}

private fun formatDuration(seconds: Long): String? =
    if (seconds <= 0) null else "%d:%02d".format(seconds / 60, seconds % 60)

/** Album-style card for a real song: artwork, title, artist. */
@Composable
fun SongCard(track: Track, modifier: Modifier = Modifier, size: Dp? = null, onClick: () -> Unit) {
    val colors = TuneRackTheme.colors
    Column(modifier.then(if (size != null) Modifier.width(size) else Modifier).pressable(onClick = onClick)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.softShadow(RoundedCornerShape(10.dp))) {
            Artwork(track.artworkUrl, size ?: maxWidth, corner = 10.dp)
        }
        BasicText(
            track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TuneRackTheme.typography.footnote.copy(color = colors.label, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(top = 8.dp),
        )
        BasicText(
            track.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TuneRackTheme.typography.footnote.copy(color = colors.secondaryLabel),
        )
    }
}
