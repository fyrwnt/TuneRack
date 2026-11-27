package com.gitgud.tunerack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** Scrollable content registers as the blur source; bars blur whatever scrolls under them. */
val LocalHazeState = staticCompositionLocalOf<HazeState> { error("No HazeState provided") }

/** Frosted material. [visibility] is read inside the style so scrolling never recomposes. */
@Composable
fun Modifier.glassBar(visibility: () -> Float = { 1f }): Modifier {
    val tint = TuneRackTheme.colors.barTint
    val style = remember(tint) {
        HazeBlurStyle {
            blurRadius(28.dp)
            backgroundColor(tint.copy(alpha = 1f))
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            alpha(visibility())
        }
    }
    return hazeBlur(input = HazeInput.Sources(LocalHazeState.current), style = style)
}

/** Floating glass surface: soft shadow, frosted fill, hairline light edge. */
@Composable
fun Modifier.glassSurface(shape: Shape): Modifier = this
    .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
    .clip(shape)
    .glassBar()
    .border(0.5.dp, TuneRackTheme.colors.glassEdge, shape)

/** Light, diffuse lift for artwork and cards; heavier shadows read as smudges on grey. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 8.dp): Modifier = shadow(
    elevation,
    shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.06f),
    spotColor = Color.Black.copy(alpha = 0.10f),
)

/** Apple-style press feedback: a slight springy shrink instead of a ripple. */
@Composable
fun Modifier.pressable(
    role: Role = Role.Button,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, spring(stiffness = 900f), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, role = role, onClick = onClick)
}

@Composable
fun TuneIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

/** Empty artwork: soft grey gradient with a faint note, used until real cover art loads. */
@Composable
fun ArtworkPlaceholder(size: Dp, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val colors = TuneRackTheme.colors
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.verticalGradient(listOf(colors.artworkTop, colors.artworkBottom))),
        contentAlignment = Alignment.Center,
    ) {
        TuneIcon(TuneIcons.Note, colors.tertiaryLabel.copy(alpha = 0.55f), size = size * 0.34f)
    }
}

/** Cover art from [url], showing the grey placeholder while loading or when there is none. */
@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    if (url == null) {
        ArtworkPlaceholder(size, modifier, corner)
        return
    }
    // YouTube's 4:3 thumbnails (hqdefault, sddefault, default) letterbox a 16:9 frame; zoom past the bars.
    val zoom = if (LETTERBOXED.containsMatchIn(url)) 4f / 3f else 1f
    Box(modifier.size(size).clip(RoundedCornerShape(corner))) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { ArtworkPlaceholder(size, corner = corner) },
            error = { ArtworkPlaceholder(size, corner = corner) },
            modifier = Modifier.size(size).graphicsLayer { scaleX = zoom; scaleY = zoom },
        )
    }
}

private val LETTERBOXED = Regex("ytimg\\.com/vi(_webp)?/[^/]+/(hq|sd)?default\\.")
