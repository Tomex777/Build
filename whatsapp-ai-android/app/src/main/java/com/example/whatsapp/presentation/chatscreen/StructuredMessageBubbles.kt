package com.example.whatsapp.presentation.chatscreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A Night-native rich response. The message stays one chat item while its
 * contents are composed from reusable blocks.
 */
data class StructuredResultMessage(
    override val id: String,
    val blocks: List<NightContentBlock>,
    val time: String,
    val mine: Boolean = false,
) : RichResultMessage

sealed interface NightContentBlock

data class NightRichTextBlock(
    val text: String,
) : NightContentBlock

data class NightCodeBlock(
    val code: String,
    val language: String = "",
    val title: String = "",
) : NightContentBlock

data class NightCopyBlock(
    val text: String,
    val label: String = "Copy",
    val preview: String = "",
) : NightContentBlock

data class NightTableBlock(
    val columns: List<String>,
    val rows: List<List<String>>,
    val title: String = "",
) : NightContentBlock

enum class NightProgressState {
    Queued,
    Running,
    Paused,
    Complete,
    Failed,
}

data class NightProgressBlock(
    val title: String,
    val detail: String = "",
    val progress: Float? = null,
    val state: NightProgressState = NightProgressState.Running,
    val action: NightBlockAction? = null,
) : NightContentBlock

data class NightChecklistItem(
    val id: String,
    val text: String,
    val complete: Boolean = false,
)

data class NightTaskBlock(
    val title: String,
    val items: List<NightChecklistItem>,
    val detail: String = "",
) : NightContentBlock

enum class NightToolState {
    Running,
    Success,
    Failed,
    NeedsInput,
}

data class NightToolBlock(
    val toolName: String,
    val title: String,
    val detail: String = "",
    val state: NightToolState = NightToolState.Success,
    val actions: List<NightBlockAction> = emptyList(),
) : NightContentBlock

data class NightErrorBlock(
    val title: String,
    val detail: String,
    val retryActionId: String? = null,
    val retryLabel: String = "Retry",
) : NightContentBlock

data class NightSourceItem(
    val id: String,
    val title: String,
    val detail: String = "",
    val citationLabel: String = "",
    val actionId: String? = null,
)

data class NightSourcesBlock(
    val title: String = "Sources",
    val sources: List<NightSourceItem>,
) : NightContentBlock

enum class NightActionStyle {
    Primary,
    Secondary,
    Destructive,
}

data class NightBlockAction(
    val id: String,
    val label: String,
    val style: NightActionStyle = NightActionStyle.Secondary,
    val enabled: Boolean = true,
)

data class NightConfirmationBlock(
    val title: String,
    val detail: String,
    val confirm: NightBlockAction,
    val cancel: NightBlockAction? = null,
) : NightContentBlock

enum class NightPermissionScope {
    AskEveryTime,
    Once,
    Chat,
    AlwaysSelected,
    FullAccess,
}

data class NightPermissionBlock(
    val title: String,
    val detail: String,
    val permissions: List<String>,
    val selectedScope: NightPermissionScope = NightPermissionScope.AskEveryTime,
    val destructiveConfirmation: Boolean = true,
    val allowFullAccess: Boolean = true,
    val actionPrefix: String = "permission",
) : NightContentBlock

enum class NightQuestionMode {
    Single,
    Multiple,
}

data class NightQuestionOption(
    val id: String,
    val label: String,
    val detail: String = "",
    val previewPath: String? = null,
)

data class NightInteractiveQuestion(
    val id: String,
    val prompt: String,
    val detail: String = "",
    val mode: NightQuestionMode = NightQuestionMode.Single,
    val options: List<NightQuestionOption>,
    val selectedIds: Set<String> = emptySet(),
    val allowCustom: Boolean = true,
    val customLabel: String = "Other",
)

data class NightQuestionBlock(
    val title: String = "",
    val questions: List<NightInteractiveQuestion>,
    val submitActionId: String = "question.submit",
    val submitLabel: String = "Continue",
) : NightContentBlock

enum class NightDiffLineKind {
    Context,
    Added,
    Removed,
}

data class NightDiffLine(
    val kind: NightDiffLineKind,
    val text: String,
)

data class NightDiffBlock(
    val title: String,
    val path: String = "",
    val lines: List<NightDiffLine>,
) : NightContentBlock

enum class NightConnectionState {
    Connected,
    Connecting,
    NeedsAuth,
    Failed,
    Disconnected,
}

data class NightConnectionBlock(
    val service: String,
    val title: String,
    val detail: String = "",
    val state: NightConnectionState,
    val actions: List<NightBlockAction> = emptyList(),
) : NightContentBlock

private val StructuredPanel = Color(0xFF303436)
private val StructuredPanelAlt = Color(0xFF202628)
private val StructuredText = Color(0xFFECEDEE)
private val StructuredMuted = Color(0xFFA8B0B4)
private val StructuredAccent = Color(0xFF25D366)
private val StructuredPink = Color(0xFFE94B72)
private val StructuredDanger = Color(0xFFFF6E7F)

@Composable
internal fun StructuredResultBubble(
    item: StructuredResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    BubbleFrame(time = item.time, mine = item.mine) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.blocks.forEach { block ->
                StructuredContentBlock(
                    messageId = item.id,
                    block = block,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun StructuredContentBlock(
    messageId: String,
    block: NightContentBlock,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    when (block) {
        is NightRichTextBlock -> Text(
            text = block.text,
            color = StructuredText,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )

        is NightCodeBlock -> CodeBlock(block)
        is NightCopyBlock -> CopyBlock(block)
        is NightTableBlock -> TableBlock(block)
        is NightProgressBlock -> ProgressBlock(messageId, block, onAction)
        is NightTaskBlock -> TaskBlock(block)
        is NightToolBlock -> ToolBlock(messageId, block, onAction)
        is NightErrorBlock -> ErrorBlock(messageId, block, onAction)
        is NightSourcesBlock -> SourcesBlock(messageId, block, onAction)
        is NightConfirmationBlock -> ConfirmationBlock(messageId, block, onAction)
        is NightPermissionBlock -> PermissionBlock(messageId, block, onAction)
        is NightQuestionBlock -> QuestionBlock(messageId, block, onAction)
        is NightDiffBlock -> DiffBlock(block)
        is NightConnectionBlock -> ConnectionBlock(messageId, block, onAction)
    }
}

@Composable
private fun CodeBlock(block: NightCodeBlock) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF15191B)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF202527))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Terminal, null, tint = StructuredMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = block.title.ifBlank { block.language.ifBlank { "Code" } },
                color = StructuredMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Copy",
                color = StructuredAccent,
                fontSize = 11.sp,
                modifier = Modifier.clickable { copyNightText(context, block.code) },
            )
        }
        Text(
            text = block.code,
            color = StructuredText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun CopyBlock(block: NightCopyBlock) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StructuredPanel)
            .clickable { copyNightText(context, block.text) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(block.label, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            val preview = block.preview.ifBlank { block.text }
            Text(
                text = preview,
                color = StructuredMuted,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.Default.ContentCopy, "Copy", tint = StructuredAccent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TableBlock(block: NightTableBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StructuredPanelAlt)
            .padding(vertical = 7.dp),
    ) {
        if (block.title.isNotBlank()) {
            Text(
                block.title,
                color = StructuredText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                TableRow(block.columns, header = true)
                block.rows.take(20).forEach { row ->
                    TableRow(row, header = false, columns = block.columns.size)
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    values: List<String>,
    header: Boolean,
    columns: Int = values.size,
) {
    Row(modifier = Modifier.background(if (header) Color(0xFF343B3E) else Color.Transparent)) {
        repeat(columns.coerceAtLeast(1)) { index ->
            Text(
                text = values.getOrElse(index) { "" },
                color = if (header) StructuredText else StructuredMuted,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp,
                modifier = Modifier
                    .widthIn(min = 94.dp, max = 180.dp)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ProgressBlock(
    messageId: String,
    block: NightProgressBlock,
    onAction: (String, String) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.title,
                color = StructuredText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                block.state.name,
                color = if (block.state == NightProgressState.Failed) StructuredDanger else StructuredMuted,
                fontSize = 10.sp,
            )
        }
        if (block.detail.isNotBlank()) {
            MutedText(block.detail)
        }
        block.progress?.let {
            LinearProgressIndicator(
                progress = { it.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
            )
        }
        block.action?.let { action ->
            StructuredActionRow(messageId, listOf(action), onAction)
        }
    }
}

@Composable
private fun TaskBlock(block: NightTaskBlock) {
    Panel {
        Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (block.detail.isNotBlank()) MutedText(block.detail)
        block.items.take(12).forEach { item ->
            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (item.complete) "✓" else "○",
                    color = if (item.complete) StructuredAccent else StructuredMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(7.dp))
                Text(item.text, color = StructuredText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolBlock(
    messageId: String,
    block: NightToolBlock,
    onAction: (String, String) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, null, tint = StructuredAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(block.toolName, color = StructuredMuted, fontSize = 10.sp)
            }
            Text(block.state.name, color = StructuredMuted, fontSize = 10.sp)
        }
        if (block.detail.isNotBlank()) MutedText(block.detail)
        StructuredActionRow(messageId, block.actions, onAction)
    }
}

@Composable
private fun ErrorBlock(
    messageId: String,
    block: NightErrorBlock,
    onAction: (String, String) -> Unit,
) {
    Panel(borderTint = StructuredDanger.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.ErrorOutline, null, tint = StructuredDanger, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                MutedText(block.detail)
            }
        }
        block.retryActionId?.let {
            StructuredActionRow(
                messageId,
                listOf(NightBlockAction(it, block.retryLabel, NightActionStyle.Primary)),
                onAction,
            )
        }
    }
}

@Composable
private fun SourcesBlock(
    messageId: String,
    block: NightSourcesBlock,
    onAction: (String, String) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, null, tint = StructuredAccent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        block.sources.take(8).forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .then(
                        if (source.actionId != null) {
                            Modifier.clickable { onAction(messageId, source.actionId) }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    source.citationLabel.ifBlank { "•" },
                    color = StructuredAccent,
                    fontSize = 11.sp,
                    modifier = Modifier.width(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.title, color = StructuredText, fontSize = 12.sp, maxLines = 2)
                    if (source.detail.isNotBlank()) {
                        Text(source.detail, color = StructuredMuted, fontSize = 10.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationBlock(
    messageId: String,
    block: NightConfirmationBlock,
    onAction: (String, String) -> Unit,
) {
    Panel {
        Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        MutedText(block.detail)
        StructuredActionRow(
            messageId,
            listOfNotNull(block.cancel, block.confirm),
            onAction,
        )
    }
}

@Composable
private fun PermissionBlock(
    messageId: String,
    block: NightPermissionBlock,
    onAction: (String, String) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Lock, null, tint = StructuredAccent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                MutedText(block.detail)
            }
        }

        block.permissions.take(8).forEach { permission ->
            Text(
                text = "• $permission",
                color = StructuredText,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        val choices = buildList {
            add(NightPermissionScope.AskEveryTime to "Ask every time")
            add(NightPermissionScope.Once to "Allow once")
            add(NightPermissionScope.Chat to "This chat")
            add(NightPermissionScope.AlwaysSelected to "Always selected")
            if (block.allowFullAccess) add(NightPermissionScope.FullAccess to "Full access")
        }

        choices.forEach { (scope, label) ->
            val selected = scope == block.selectedScope
            Surface(
                color = if (selected) StructuredAccent.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickable {
                        onAction(messageId, "${block.actionPrefix}.scope.${scope.name}")
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (selected) "●" else "○", color = if (selected) StructuredAccent else StructuredMuted)
                    Spacer(Modifier.width(7.dp))
                    Text(label, color = StructuredText, fontSize = 12.sp)
                }
            }
        }

        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable {
                    onAction(messageId, "${block.actionPrefix}.destructive.toggle")
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Warning, null, tint = StructuredDanger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "Confirm destructive actions separately",
                    color = StructuredText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (block.destructiveConfirmation) "On" else "Off",
                    color = if (block.destructiveConfirmation) StructuredAccent else StructuredMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    messageId: String,
    block: NightQuestionBlock,
    onAction: (String, String) -> Unit,
) {
    Panel {
        if (block.title.isNotBlank()) {
            Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        block.questions.take(6).forEach { question ->
            Column(modifier = Modifier.padding(top = 5.dp)) {
                Text(question.prompt, color = StructuredText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (question.detail.isNotBlank()) MutedText(question.detail)
                question.options.take(8).forEach { option ->
                    val selected = option.id in question.selectedIds
                    Surface(
                        color = if (selected) StructuredAccent.copy(alpha = 0.16f) else Color(0xFF22282B),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                            .clickable {
                                onAction(messageId, "question.${question.id}.option.${option.id}")
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            option.previewPath?.takeIf { it.isNotBlank() }?.let { preview ->
                                AsyncImage(
                                    model = preview,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(7.dp)),
                                )
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                if (selected) "●" else if (question.mode == NightQuestionMode.Multiple) "□" else "○",
                                color = if (selected) StructuredAccent else StructuredMuted,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.width(7.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, color = StructuredText, fontSize = 12.sp)
                                if (option.detail.isNotBlank()) {
                                    Text(option.detail, color = StructuredMuted, fontSize = 10.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                if (question.allowCustom) {
                    StructuredActionRow(
                        messageId,
                        listOf(
                            NightBlockAction(
                                id = "question.${question.id}.custom",
                                label = question.customLabel,
                            )
                        ),
                        onAction,
                    )
                }
            }
        }
        StructuredActionRow(
            messageId,
            listOf(NightBlockAction(block.submitActionId, block.submitLabel, NightActionStyle.Primary)),
            onAction,
        )
    }
}

@Composable
private fun DiffBlock(block: NightDiffBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF15191B)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF202527))
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            if (block.path.isNotBlank()) Text(block.path, color = StructuredMuted, fontSize = 10.sp)
        }
        block.lines.take(80).forEach { line ->
            val prefix = when (line.kind) {
                NightDiffLineKind.Context -> " "
                NightDiffLineKind.Added -> "+"
                NightDiffLineKind.Removed -> "-"
            }
            val tint = when (line.kind) {
                NightDiffLineKind.Context -> Color.Transparent
                NightDiffLineKind.Added -> Color(0xFF143D2B)
                NightDiffLineKind.Removed -> Color(0xFF4A2027)
            }
            Text(
                text = prefix + line.text,
                color = StructuredText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ConnectionBlock(
    messageId: String,
    block: NightConnectionBlock,
    onAction: (String, String) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val connected = block.state == NightConnectionState.Connected
            Icon(
                if (connected) Icons.Default.CheckCircle else Icons.Default.Link,
                null,
                tint = if (connected) StructuredAccent else StructuredPink,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, color = StructuredText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(block.service, color = StructuredMuted, fontSize = 10.sp)
            }
            Text(block.state.name, color = StructuredMuted, fontSize = 10.sp)
        }
        if (block.detail.isNotBlank()) MutedText(block.detail)
        StructuredActionRow(messageId, block.actions, onAction)
    }
}

@Composable
private fun StructuredActionRow(
    messageId: String,
    actions: List<NightBlockAction>,
    onAction: (String, String) -> Unit,
) {
    if (actions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        actions.take(3).forEach { action ->
            val fill = when (action.style) {
                NightActionStyle.Primary -> StructuredAccent.copy(alpha = 0.18f)
                NightActionStyle.Secondary -> Color(0xFF252C2F)
                NightActionStyle.Destructive -> StructuredDanger.copy(alpha = 0.17f)
            }
            val textColor = when (action.style) {
                NightActionStyle.Primary -> StructuredAccent
                NightActionStyle.Secondary -> StructuredText
                NightActionStyle.Destructive -> StructuredDanger
            }
            Surface(
                color = fill,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = action.enabled) {
                        onAction(messageId, action.id)
                    },
            ) {
                Text(
                    text = action.label,
                    color = if (action.enabled) textColor else StructuredMuted.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Panel(
    borderTint: Color = Color.Transparent,
    content: @Composable Column.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (borderTint == Color.Transparent) StructuredPanel else borderTint
            )
            .padding(if (borderTint == Color.Transparent) 9.dp else 1.dp),
    ) {
        if (borderTint == Color.Transparent) {
            content()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(StructuredPanel)
                    .padding(9.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        color = StructuredMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 3.dp),
    )
}

private fun copyNightText(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Copied text", value))
}
