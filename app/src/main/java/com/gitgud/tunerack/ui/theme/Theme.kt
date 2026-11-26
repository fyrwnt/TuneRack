package com.gitgud.tunerack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gitgud.tunerack.R

/** Graphite-and-grey palette. `accent` is deliberately neutral; colour comes from artwork. */
@Immutable
data class TuneRackColors(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val barTint: Color,
    val glassEdge: Color,
    val artworkTop: Color,
    val artworkBottom: Color,
    /** Selected segment of a segmented control; lifted above the track in both themes. */
    val segment: Color,
)

private val LightColors = TuneRackColors(
    accent = Color(0xFF2C2C2E),
    background = Color(0xFFF4F4F2),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFEBEBE8),
    label = Color(0xFF1A1A1A),
    secondaryLabel = Color(0xFF6E6E73),
    tertiaryLabel = Color(0xFFA1A1A6),
    separator = Color(0x17000000),
    fill = Color(0x0F000000),
    barTint = Color(0xB8F8F8F6),
    glassEdge = Color(0x99FFFFFF),
    artworkTop = Color(0xFFE2E2DF),
    artworkBottom = Color(0xFFC9C9C5),
    segment = Color(0xFFFFFFFF),
)

private val DarkColors = TuneRackColors(
    accent = Color(0xFFE8E8EA),
    background = Color(0xFF0B0B0C),
    surface = Color(0xFF161618),
    elevated = Color(0xFF222225),
    label = Color(0xFFF5F5F7),
    secondaryLabel = Color(0xFF98989D),
    tertiaryLabel = Color(0xFF5A5A5F),
    separator = Color(0x1AFFFFFF),
    fill = Color(0x14FFFFFF),
    barTint = Color(0xB31C1C1E),
    glassEdge = Color(0x1FFFFFFF),
    artworkTop = Color(0xFF2E2E31),
    artworkBottom = Color(0xFF19191B),
    segment = Color(0xFF48484C),
)

@Immutable
data class TuneRackTypography(
    val largeTitle: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val tabLabel: TextStyle,
)

// Static Inter instances cut from the variable font (Latin subset): the display optical size
// (tighter, crisper) for titles, the text optical size for everything else.
private val InterText = FontFamily(
    Font(R.font.inter_text_400, FontWeight.Normal),
    Font(R.font.inter_text_500, FontWeight.Medium),
    Font(R.font.inter_text_600, FontWeight.SemiBold),
)

private val InterDisplay = FontFamily(
    Font(R.font.inter_display_600, FontWeight.SemiBold),
    Font(R.font.inter_display_700, FontWeight.Bold),
    Font(R.font.inter_display_800, FontWeight.ExtraBold),
)

private val Typography = TuneRackTypography(
    largeTitle = TextStyle(fontFamily = InterDisplay, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em),
    title2 = TextStyle(fontFamily = InterDisplay, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    title3 = TextStyle(fontFamily = InterDisplay, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.015).em),
    headline = TextStyle(fontFamily = InterText, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    body = TextStyle(fontFamily = InterText, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.01).em),
    callout = TextStyle(fontFamily = InterText, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em),
    subheadline = TextStyle(fontFamily = InterText, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.005).em),
    footnote = TextStyle(fontFamily = InterText, fontSize = 13.sp, lineHeight = 17.sp),
    caption = TextStyle(fontFamily = InterText, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    tabLabel = TextStyle(fontFamily = InterText, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
)

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf { Typography }

object TuneRackTheme {
    val colors: TuneRackColors
        @Composable get() = LocalColors.current
    val typography: TuneRackTypography
        @Composable get() = LocalTypography.current
}

@Composable
fun TuneRackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColors provides if (darkTheme) DarkColors else LightColors,
        LocalTypography provides Typography,
        content = content,
    )
}
