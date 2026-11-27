package com.gitgud.tunerack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Set by the app root so the mini player's cover can grow into the full player's (Apple Music style). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Marks the now-playing cover; the mini player and full player share it while one opens the other. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.nowPlayingArtwork(visibility: AnimatedVisibilityScope): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    return with(shared) { sharedElement(rememberSharedContentState("now-playing-artwork"), visibility) }
}
