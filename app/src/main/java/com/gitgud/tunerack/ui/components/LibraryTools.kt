package com.gitgud.tunerack.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.gitgud.tunerack.tuneRack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Moving the library: export it to a JSON file, import one back, and re-scan the download
 * folder to reconnect songs that are already on the phone.
 */
@Stable
class LibraryTools internal constructor(
    val export: () -> Unit,
    val import: () -> Unit,
    val rescan: () -> Unit,
)

val LocalLibraryTools = staticCompositionLocalOf<LibraryTools> { error("No LibraryTools") }

@Composable
fun rememberLibraryTools(sheets: SheetController, downloads: DownloadStarter): LibraryTools {
    val app = LocalContext.current.tuneRack
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { resolver.openOutputStream(uri)?.use { app.library.exportTo(it) } ?: error("no stream") }
            }.isSuccess
            sheets.flash(if (ok) "Library Exported" else "Export Failed")
        }
    }

    val rescan: () -> Unit = {
        downloads.withFolder {
            scope.launch {
                val message = runCatching { app.downloads.rescan() }.fold(
                    onSuccess = { "Found ${songCount(it.linked)}" + if (it.removed > 0) ", removed ${it.removed} missing" else "" },
                    onFailure = { "Re-scan Failed" },
                )
                sheets.flash(message)
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { app.library.importFrom(it) } ?: error("no stream") }
            }
            result.fold(
                onSuccess = {
                    sheets.flash(
                        when (it.playlists) {
                            0 -> "Library Imported"
                            1 -> "Library Imported · 1 new playlist"
                            else -> "Library Imported · ${it.playlists} new playlists"
                        },
                    )
                    // Downloaded files already in the folder are linked right away when one is set.
                    if (app.downloads.folder.value != null) runCatching { app.downloads.rescan() }
                },
                onFailure = { sheets.flash(it.message?.takeIf { msg -> msg.startsWith("Not a") || msg.startsWith("This backup") } ?: "Import Failed") },
            )
        }
    }

    return remember(sheets, downloads) {
        LibraryTools(
            export = { exporter.launch("TuneRack Library ${LocalDate.now()}.json") },
            import = { importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            rescan = rescan,
        )
    }
}
