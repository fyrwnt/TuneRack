package com.gitgud.tunerack.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gitgud.tunerack.ui.theme.TuneRackTheme

private const val ISSUES_URL = "https://github.com/fyrwnt/TuneRack/issues/new"
private val Kinds = listOf("Bug", "Idea", "Other")

/**
 * Feedback without a server: the form opens a prefilled GitHub issue in the browser, where the
 * user reviews it and posts it with their GitHub account. Nothing is sent from the app itself.
 */
@Composable
internal fun FeedbackSheet(controller: SheetController) {
    val context = LocalContext.current
    val colors = TuneRackTheme.colors
    val type = TuneRackTheme.typography
    var kind by remember { mutableIntStateOf(0) }
    val text = rememberTextFieldState()
    val details = remember { deviceDetails(context) }
    val valid = text.text.isNotBlank()

    Column(Modifier.padding(horizontal = 20.dp)) {
        BasicText("Send Feedback", style = type.title3.copy(color = colors.label), modifier = Modifier.padding(vertical = 10.dp))
        SegmentedControl(Kinds, kind, { kind = it })
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(colors.fill, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            if (text.text.isEmpty()) {
                BasicText(
                    when (kind) {
                        0 -> "What went wrong, and what did you expect?"
                        1 -> "What would you like TuneRack to do?"
                        else -> "Tell us anything."
                    },
                    style = type.body.copy(color = colors.tertiaryLabel),
                )
            }
            BasicTextField(
                state = text,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4, maxHeightInLines = 8),
                textStyle = type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.label),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicText(
            "Opens GitHub in your browser, where you can review and post it (a free GitHub account is needed). Included: $details.",
            style = type.footnote.copy(color = colors.secondaryLabel),
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(48.dp)
                .background(if (valid) colors.accent else colors.fill, RoundedCornerShape(12.dp))
                .then(
                    if (valid) {
                        Modifier.pressable(pressedScale = 0.97f) {
                            controller.dismiss()
                            if (!openIssue(context, Kinds[kind], text.text.toString().trim(), details)) {
                                controller.flash("No browser found")
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Continue to GitHub", style = type.headline.copy(color = if (valid) colors.background else colors.tertiaryLabel))
        }
    }
}

private fun deviceDetails(context: Context): String {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    return "TuneRack $version, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}"
}

private fun openIssue(context: Context, kind: String, message: String, details: String): Boolean {
    val firstLine = message.lineSequence().first().take(70)
    val body = "$message\n\n---\n$details"
    val uri = Uri.parse(ISSUES_URL).buildUpon()
        .appendQueryParameter("title", "[$kind] $firstLine")
        // Browsers cap URL length; very long reports are trimmed and can be finished on GitHub.
        .appendQueryParameter("body", body.take(6000))
        .build()
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
