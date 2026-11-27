package com.gitgud.tunerack.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gitgud.tunerack.data.youtube.YouTubeAccount
import com.gitgud.tunerack.tuneRack
import com.gitgud.tunerack.ui.icons.TuneIcons
import com.gitgud.tunerack.ui.theme.TuneRackTheme
import com.gitgud.tunerack.ui.tutorial.tutorialTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Sign in, re-import and sign out, usable from any screen. Handles Google's own screens. */
@Stable
class AccountActions internal constructor(
    private val account: YouTubeAccount,
    private val sheets: SheetController,
    private val scope: CoroutineScope,
    private val launch: (IntentSenderRequest) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set

    /** What to do once Google hands back a token (sign in, or import again). */
    private var afterGrant: (suspend (YouTubeAccount.Authorization.Granted) -> Unit)? = null

    fun signIn() = withToken { granted ->
        account.completeSignIn(granted)
        importWith(granted)
    }

    fun importAgain() = withToken(::importWith)

    fun signOut() {
        scope.launch {
            account.signOut()
            sheets.flash("Signed Out")
        }
    }

    internal fun onResult(data: android.content.Intent?) {
        val next = afterGrant ?: return
        afterGrant = null
        scope.launch {
            guarded { next(account.resultFrom(data)) }
        }
    }

    private fun withToken(block: suspend (YouTubeAccount.Authorization.Granted) -> Unit) {
        if (busy) return
        scope.launch {
            guarded {
                when (val auth = account.authorize()) {
                    is YouTubeAccount.Authorization.Granted -> block(auth)
                    is YouTubeAccount.Authorization.NeedsUser -> {
                        afterGrant = block
                        launch(IntentSenderRequest.Builder(auth.intent).build())
                    }
                }
            }
        }
    }

    private suspend fun importWith(granted: YouTubeAccount.Authorization.Granted) {
        val result = try {
            account.importLibrary(granted.token)
        } catch (e: YouTubeAccount.TokenExpiredException) {
            // Google handed out a stale token: drop it and ask once more.
            account.clearToken(granted.token)
            val fresh = account.authorize() as? YouTubeAccount.Authorization.Granted ?: throw e
            account.importLibrary(fresh.token)
        }
        sheets.flash("Imported ${songCount(result.likes)} and ${result.playlists} playlists")
    }

    /** Runs [block] with a busy flag and turns failures into a short message. */
    private suspend fun guarded(block: suspend () -> Unit) {
        busy = true
        try {
            block()
        } catch (e: YouTubeAccount.SignInException) {
            if (e.message != "Sign-in was cancelled") sheets.flash(e.message ?: "Sign-in failed")
        } catch (e: java.io.IOException) {
            sheets.flash(e.message ?: "Check your connection and try again.")
        } catch (e: org.json.JSONException) {
            sheets.flash("YouTube returned something unexpected.")
        } finally {
            busy = false
        }
    }
}

val LocalAccount = staticCompositionLocalOf<AccountActions> { error("No AccountActions") }

@Composable
fun rememberAccountActions(sheets: SheetController): AccountActions {
    val account = LocalContext.current.tuneRack.youTubeAccount
    val scope = rememberCoroutineScope()
    lateinit var actions: AccountActions
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        actions.onResult(result.data)
    }
    actions = remember(account, sheets) { AccountActions(account, sheets, scope) { launcher.launch(it) } }
    return actions
}

/** Small round account button: the channel picture when signed in, a person icon for guests. */
@Composable
fun AccountButton(onClick: () -> Unit) {
    val profile by LocalContext.current.tuneRack.youTubeAccount.profile.collectAsStateWithLifecycle()
    val colors = TuneRackTheme.colors
    Box(Modifier.size(40.dp).pressable(pressedScale = 0.88f, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(30.dp).tutorialTarget("account").clip(CircleShape).background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = profile?.avatarUrl
            if (avatar != null) {
                AsyncImage(avatar, contentDescription = "Account", contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp))
            } else {
                TuneIcon(TuneIcons.Person, colors.label, size = 19.dp)
            }
        }
    }
}

@Composable
internal fun AccountSheet(controller: SheetController) {
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    val actions = LocalAccount.current
    val profile by LocalContext.current.tuneRack.youTubeAccount.profile.collectAsStateWithLifecycle()

    Column(Modifier.padding(bottom = 4.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
                val avatar = profile?.avatarUrl
                if (avatar != null) {
                    AsyncImage(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
                } else {
                    TuneIcon(TuneIcons.Person, colors.label, size = 28.dp)
                }
            }
            Column(Modifier.padding(start = 14.dp)) {
                BasicText(profile?.name ?: "Guest", maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.title3.copy(color = colors.label))
                BasicText(
                    if (profile == null) "Everything is stored on this phone" else "Signed in with Google · read-only",
                    style = type.subheadline.copy(color = colors.secondaryLabel),
                )
            }
        }

        val current = profile
        if (current == null) {
            BasicText(
                "Sign in to bring your YouTube likes and playlists into TuneRack and get picks based on them. " +
                    "TuneRack only reads your account: it never likes, adds or changes anything on YouTube.",
                style = type.subheadline.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .pressable(pressedScale = 0.97f) { if (!actions.busy) actions.signIn() }
                    .background(colors.accent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (actions.busy) {
                    Spinner()
                } else {
                    BasicText("Sign in with Google", style = type.headline.copy(color = colors.background))
                }
            }
        } else {
            val summary = if (current.lastImport > 0) {
                "Last import ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(current.lastImport))}: " +
                    "${songCount(current.likes)} you liked, ${current.playlists} playlists."
            } else {
                "Not imported yet."
            }
            BasicText(summary, style = type.subheadline.copy(color = colors.secondaryLabel), modifier = Modifier.padding(horizontal = 20.dp))
            Box(Modifier.padding(top = 8.dp)) {
                SheetActionRow(if (actions.busy) "Importing…" else "Import from YouTube Again", TuneIcons.Downloaded) {
                    if (!actions.busy) actions.importAgain()
                }
            }
            SheetActionRow("Sign Out", TuneIcons.RemoveCircle, destructive = true) {
                controller.dismiss()
                actions.signOut()
            }
            BasicText(
                "Songs and playlists you imported stay in your library after signing out.",
                style = type.footnote.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}
