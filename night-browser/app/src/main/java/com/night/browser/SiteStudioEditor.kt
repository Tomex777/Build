package com.night.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val StudioInk = Color(0xFF0C1017)
private val StudioPanel = Color(0xFF171D27)
private val StudioField = Color(0xFF101721)
private val StudioText = Color(0xFFF3F5F9)
private val StudioMuted = Color(0xFF9CA9BA)

/**
 * Standalone Compose UI; Agent 1/2 can add a single "Site Studio" menu item
 * and use this dialog without editing its contents.
 *
 * Preview callback stores a session-only draft and reloads the original
 * GeckoSession; reopening the editor reads bridge.editorRule(...).
 * Save/reset callbacks persist the exact origin, discard draft and reload.
 */
@Composable
fun SiteStudioEditor(
    pageUrl: String,
    initial: SiteRule?,
    onPreview: (SiteRule) -> Unit,
    onSave: (SiteRule) -> Unit,
    onReset: () -> Unit,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val origin = remember(pageUrl) { SiteOrigins.fromUrl(pageUrl) }
    if (origin == null) {
        Dialog(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().background(StudioPanel, RoundedCornerShape(18.dp))
                .padding(20.dp)) {
                Text("Site Studio is available on HTTP and HTTPS pages.", color = StudioText)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
        return
    }

    val startingRule = initial?.takeIf { it.origin == origin } ?: SiteRule(origin)
    var css by remember(origin) { mutableStateOf(startingRule.css) }
    var javascript by remember(origin) { mutableStateOf(startingRule.javascript) }
    var cssEnabled by remember(origin) { mutableStateOf(startingRule.cssEnabled) }
    var javascriptEnabled by remember(origin) { mutableStateOf(startingRule.javascriptEnabled) }
    var javascriptConsent by remember(origin) { mutableStateOf(false) }

    val overLimit = css.length > SiteStudioStore.MAX_CSS ||
        javascript.length > SiteStudioStore.MAX_JS
    val unsafeJs = javascriptEnabled && !javascriptConsent
    val allowActions = !overLimit && !unsafeJs
    val draft = SiteRule(origin, css, javascript, cssEnabled, javascriptEnabled)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth(0.96f).heightIn(max = 720.dp)
                .background(StudioPanel, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Site Studio", color = StudioText, fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(origin, color = StudioMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            HorizontalDivider(color = Color(0xFF303A49),
                modifier = Modifier.padding(vertical = 10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                StudioToggle("Custom CSS", "Only on this exact website origin", cssEnabled) {
                    cssEnabled = it
                }
                StudioCodeField(
                    css, { css = it }, "Add your CSS rules",
                    minHeight = 146, contentDescription = "Site CSS editor"
                )
                Spacer(Modifier.height(16.dp))
                StudioToggle("Custom JavaScript",
                    "Runs with this website's access when its policy allows it",
                    javascriptEnabled) { javascriptEnabled = it }
                StudioCodeField(
                    javascript, { javascript = it }, "Add your JavaScript",
                    minHeight = 130, contentDescription = "Site JavaScript editor"
                )
                if (javascriptEnabled) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "Scripts can read page information and send it elsewhere. " +
                            "Use only code you trust. Some websites block script injection.",
                        fontSize = 12.sp, color = Color(0xFFFFCE9B)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = javascriptConsent,
                            onCheckedChange = { javascriptConsent = it })
                        Text("I understand the risk for this site",
                            color = StudioText, fontSize = 12.sp)
                    }
                }
                if (overLimit) {
                    Text("Code is too long. CSS limit: 32 KB; JS limit: 16 KB.",
                        color = Color(0xFFFFB5B5), fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Preview is temporary and local to the current tab. " +
                        "Reset removes saved rules. Reload returns a page to its original state.",
                    fontSize = 11.sp, color = StudioMuted
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReset) { Text("Reset") }
                    if (canUndo) {
                        TextButton(onClick = onUndo) { Text("Undo") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = allowActions, onClick = { onPreview(draft) }) {
                        Text("Preview")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = allowActions, onClick = { onSave(draft) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioToggle(
    title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = StudioText, fontWeight = FontWeight.Medium)
            Text(subtitle, color = StudioMuted, fontSize = 11.sp)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun StudioCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    minHeight: Int,
    contentDescription: String
) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = minHeight.dp)
            .background(StudioField, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp,
                fontFamily = FontFamily.Monospace, color = StudioText),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = (minHeight - 24).dp)
                .semantics { this.contentDescription = contentDescription },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(hint, color = StudioMuted, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace)
                }
                inner()
            }
        )
    }
}
