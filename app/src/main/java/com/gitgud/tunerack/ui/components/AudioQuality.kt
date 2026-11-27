package com.gitgud.tunerack.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitgud.tunerack.tuneRack
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme

/** Streaming (Wi-Fi / mobile data) and download quality. Changes apply from the next song on. */
@Composable
internal fun AudioQualitySheet() {
    val settings = LocalContext.current.tuneRack.audioSettings
    val wifi by settings.wifi.collectAsStateWithLifecycle()
    val mobile by settings.mobile.collectAsStateWithLifecycle()
    val download by settings.download.collectAsStateWithLifecycle()
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography

    Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
        BasicText("Audio Quality", style = type.title3.copy(color = colors.label), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
        BasicText(
            "Applies from the next song. YouTube decides what's available; when a song has nothing at or under your choice, the closest lower-data version plays.",
            style = type.footnote.copy(color = colors.secondaryLabel),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Group("Streaming on Wi-Fi")
        StreamQualityOptions(wifi, settings::setWifi)
        Group("Streaming on Mobile Data")
        StreamQualityOptions(mobile, settings::setMobile)
        Group("Downloads")
        com.gitgud.tunerack.data.settings.DownloadQuality.entries.forEach { quality ->
            Choice(quality.label, quality.detail, selected = quality == download) { settings.setDownload(quality) }
        }
        BasicText(
            "Songs already downloaded keep their quality. Remove and download them again to change it.",
            style = type.footnote.copy(color = colors.secondaryLabel),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StreamQualityOptions(
    current: com.gitgud.tunerack.data.settings.StreamQuality,
    onPick: (com.gitgud.tunerack.data.settings.StreamQuality) -> Unit,
) {
    com.gitgud.tunerack.data.settings.StreamQuality.entries.forEach { quality ->
        Choice(quality.label, quality.detail, selected = quality == current) { onPick(quality) }
    }
}

@Composable
private fun Group(title: String) {
    BasicText(
        title.uppercase(),
        style = TuneRackTheme.typography.caption.copy(color = TuneRackTheme.colors.secondaryLabel),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun Choice(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    SheetPressRow(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = type.body.copy(color = colors.label))
            BasicText(detail, style = type.footnote.copy(color = colors.secondaryLabel))
        }
        if (selected) TuneIcon(TuneIcons.Checkmark, colors.label, size = 18.dp)
    }
}
