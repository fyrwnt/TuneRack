package com.gitgud.tunerack.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gitgud.tunerack.data.Track
import com.gitgud.tunerack.data.library.DownloadEntity
import com.gitgud.tunerack.tuneRack
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Starts downloads, asking for a download folder first if none has been chosen yet. */
@Stable
class DownloadStarter internal constructor(
    private val hasFolder: () -> Boolean,
    private val pickFolder: () -> Unit,
    private val enqueue: (List<Track>) -> Unit,
) {
    /** Runs once a folder has been picked (null when the picker was opened just to change it). */
    internal var afterPick: (() -> Unit)? = null

    fun download(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        withFolder { enqueue(tracks) }
    }

    /** Runs [action] now if a folder is set, otherwise after the user picks one. */
    fun withFolder(action: () -> Unit) {
        if (hasFolder()) {
            action()
        } else {
            afterPick = action
            pickFolder()
        }
    }

    fun changeFolder() {
        afterPick = null
        pickFolder()
    }
}

val LocalDownloads = staticCompositionLocalOf<DownloadStarter> { error("No DownloadStarter") }

@Composable
fun rememberDownloadStarter(): DownloadStarter {
    val context = LocalContext.current
    val downloads = context.tuneRack.downloads
    val scope: CoroutineScope = rememberCoroutineScope()
    // Download progress shows as a notification, which needs permission on Android 13+. Asked once,
    // the first time a download starts; downloads work either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askForNotifications = {
        val prefs = context.getSharedPreferences("downloads", android.content.Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("askedNotifications", false)
        ) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    lateinit var starter: DownloadStarter
    val picker = rememberLauncherForActivityResult(PickFolder()) { tree ->
        if (tree != null) {
            downloads.setFolder(tree)
            starter.afterPick?.invoke()
            starter.afterPick = null
        }
    }
    starter = remember(downloads) {
        DownloadStarter(
            hasFolder = { downloads.folder.value != null },
            pickFolder = { picker.launch(null) },
            enqueue = { tracks ->
                askForNotifications()
                scope.launch { downloads.enqueue(tracks) }
            },
        )
    }
    return starter
}

/** Folder picker that opens in Music by default, where other players look for songs. */
private class PickFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: android.content.Context, input: Uri?): Intent =
        super.createIntent(context, input).apply {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music"),
            )
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}

/** Small status mark for song rows: downloaded, downloading (ring), queued or failed. */
@Composable
fun DownloadIndicator(download: DownloadEntity?, size: Dp = 14.dp) {
    val colors = TuneRackTheme.colors
    when (download?.state) {
        null -> Unit
        DownloadEntity.DONE -> TuneIcon(TuneIcons.DownloadedSmall, colors.secondaryLabel, size = size)
        DownloadEntity.FAILED -> TuneIcon(TuneIcons.Alert, Color(0xFFE5483D), size = size)
        else -> Ring(if (download.state == DownloadEntity.RUNNING) download.progress else 0f, size)
    }
}

/** Indeterminate spinner: a short arc going round. */
@Composable
fun Spinner(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val angle by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle",
    )
    Ring(0.28f, size, modifier.graphicsLayer { rotationZ = angle })
}

@Composable
private fun Ring(progress: Float, size: Dp, modifier: Modifier = Modifier) {
    val track = TuneRackTheme.colors.fill
    val tint = TuneRackTheme.colors.secondaryLabel
    Canvas(modifier.size(size)) {
        val stroke = 1.6.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(tint, -90f, 360f * progress.coerceIn(0.03f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
