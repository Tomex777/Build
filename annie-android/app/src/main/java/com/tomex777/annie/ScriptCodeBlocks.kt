package com.tomex777.annie

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val BlockShell = Color(0xFF10243A)
private val BlockCodeBackground = Color(0xFF081624)
private val BlockForeground = Color(0xFFE8F4FF)
private val BlockSecondary = Color(0xFF9DB5CE)
private val BlockAction = Color(0xFF42B9F5)

/**
 * Code and copy bubbles remain pure data, rendered by Annie's native message dispatcher.
 * They never execute the displayed code or interpret it as HTML.
 */
@Composable
internal fun ScriptCodeBlockMessage(data: JSONObject) {
    val context = LocalContext.current
    val code = data.optString("code").take(MAX_MESSAGE_BYTES)
    val label = data.optString("language").trim().take(28).ifBlank { "Code" }
    val title = data.optString("title").trim().take(140)

    Surface(
        color = BlockShell,
        shape = RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp),
        modifier = Modifier.fillMaxWidth().testTag("script_code_message"),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (title.isNotBlank()) {
                Text(title, color = BlockForeground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, color = BlockSecondary, fontSize = 11.sp)
                TextButton(onClick = { copyBlockText(context, "Annie code", code) },
                    modifier = Modifier.testTag("script_code_copy")) {
                    Text("Copy code", color = BlockAction, fontSize = 12.sp)
                }
            }
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp)
                        .background(BlockCodeBackground, RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        code.ifBlank { "// Empty code block" },
                        color = BlockForeground,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        softWrap = false,
                        modifier = Modifier.testTag("script_code_content")
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScriptCopyBlockMessage(data: JSONObject) {
    val context = LocalContext.current
    val label = data.optString("title").trim().take(140).ifBlank { "Copyable text" }
    val value = data.optString("text").ifBlank { data.optString("value") }.take(MAX_MESSAGE_BYTES)
    Surface(
        color = BlockShell,
        shape = RoundedCornerShape(8.dp, 20.dp, 20.dp, 20.dp),
        modifier = Modifier.fillMaxWidth().testTag("script_copy_message"),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = BlockForeground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { copyBlockText(context, label, value) },
                    modifier = Modifier.testTag("script_copy_action")) {
                    Text("Copy", color = BlockAction, fontSize = 12.sp)
                }
            }
            SelectionContainer {
                Text(value, color = BlockSecondary, fontSize = 13.sp,
                    modifier = Modifier.testTag("script_copy_content"))
            }
        }
    }
}

private fun copyBlockText(context: Context, label: String, value: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
}
