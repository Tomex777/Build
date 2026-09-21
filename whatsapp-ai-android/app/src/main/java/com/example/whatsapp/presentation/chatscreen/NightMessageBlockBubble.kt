package com.example.whatsapp.presentation.chatscreen

import coil.compose.AsyncImage
import com.example.whatsapp.extensions.messages.ExtensionActionStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BlockIncoming = Color(0xFF242625)
private val BlockOutgoing = Color(0xFF7E112E)
private val BlockPanelColor = Color(0xFF303436)
private val BlockPanelStrong = Color(0xFF1B2022)
private val BlockText = Color(0xFFECEDEE)
private val BlockMuted = Color(0xFFA5AEB2)
private val BlockAccent = Color(0xFF53BDEB)
private val BlockDanger = Color(0xFFFF8CA0)

@Composable
fun NightBlockResultBubble(
    item: BlockResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit = { _, _ -> },
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .clip(
                    if (item.mine) {
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 5.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp,
                        )
                    } else {
                        RoundedCornerShape(
                            topStart = 5.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp,
                        )
                    }
                )
                .background(if (item.mine) BlockOutgoing else BlockIncoming)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item.blocks.forEach { block ->
                NightMessageBlockView(block, item.id, onAction)
            }

            if (item.time.isNotBlank()) {
                Text(
                    text = item.time,
                    color = BlockMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun NightMessageBlockView(
    block: NightMessageBlock,
    messageId: String,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    when (block) {
        is NightMessageBlock.RichText -> {
            Text(block.text, color = BlockText, fontSize = 14.sp)
        }

        is NightMessageBlock.MediaPreview -> {
            BlockPanel {
                if (block.path.isNotBlank()) {
                    AsyncImage(
                        model = block.path,
                        contentDescription = block.title.ifBlank { "Media preview" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
                if (block.title.isNotBlank()) {
                    Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                }
                if (block.subtitle.isNotBlank()) {
                    Text(block.subtitle, color = BlockMuted, fontSize = 12.sp)
                }
                block.actionId?.let { actionId ->
                    BlockActionButton(
                        NightBlockAction(actionId, "Open", NightBlockActionStyle.Primary),
                        messageId,
                        onAction,
                    )
                }
            }
        }

        is NightMessageBlock.Code -> {
            BlockPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = block.title.ifBlank { block.language.ifBlank { "Code" } },
                        color = BlockMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    block.copyActionId?.let { actionId ->
                        BlockActionButton(
                            NightBlockAction(actionId, "Copy"),
                            messageId,
                            onAction,
                        )
                    }
                }
                Text(
                    text = block.code,
                    color = BlockText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }

        is NightMessageBlock.Copy -> {
            BlockPanel {
                Text(block.text, color = BlockText, fontSize = 13.sp)
                BlockActionButton(
                    NightBlockAction(block.actionId, block.label),
                    messageId,
                    onAction,
                )
            }
        }

        is NightMessageBlock.KeyValues -> {
            BlockPanel {
                block.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            item.label,
                            color = BlockMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.value,
                            color = BlockText,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1.25f),
                        )
                    }
                }
            }
        }

        is NightMessageBlock.Table -> {
            BlockPanel {
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (block.headers.isNotEmpty()) NightTableLine(block.headers, true)
                    block.rows.forEach { row -> NightTableLine(row.cells, false) }
                }
            }
        }

        is NightMessageBlock.Tasks -> {
            BlockPanel {
                Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                block.progress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                block.items.forEach { task ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            if (task.completed) "✓" else "○",
                            color = if (task.completed) BlockAccent else BlockMuted,
                            modifier = Modifier.width(22.dp),
                        )
                        Column {
                            Text(task.label, color = BlockText, fontSize = 13.sp)
                            if (task.detail.isNotBlank()) {
                                Text(task.detail, color = BlockMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        is NightMessageBlock.Progress -> {
            BlockPanel {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        block.title,
                        color = BlockText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (block.status.isNotBlank()) {
                        Text(block.status, color = BlockMuted, fontSize = 11.sp)
                    }
                }
                if (block.detail.isNotBlank()) {
                    Text(block.detail, color = BlockMuted, fontSize = 12.sp)
                }
                block.progress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        is NightMessageBlock.ToolResult -> {
            BlockPanel {
                Text(
                    block.toolName,
                    color = BlockAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                if (block.detail.isNotBlank()) {
                    Text(block.detail, color = BlockMuted, fontSize = 12.sp)
                }
                val stateText = when (block.state) {
                    NightToolState.Running -> "Running"
                    NightToolState.Succeeded -> "Completed"
                    NightToolState.Failed -> "Failed"
                }
                Text(
                    stateText,
                    color = if (block.state == NightToolState.Failed) BlockDanger else BlockMuted,
                    fontSize = 11.sp,
                )
                BlockActionRow(block.actions, messageId, onAction)
            }
        }

        is NightMessageBlock.Error -> {
            BlockPanel {
                Text(block.title, color = BlockDanger, fontWeight = FontWeight.SemiBold)
                Text(block.detail, color = BlockText, fontSize = 12.sp)
                block.retryAction?.let { action ->
                    BlockActionButton(action, messageId, onAction)
                }
            }
        }

        is NightMessageBlock.Sources -> {
            BlockPanel {
                Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                block.items.forEachIndexed { index, source ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            (index + 1).toString() + ".",
                            color = BlockMuted,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.title, color = BlockText, fontSize = 12.sp)
                            if (source.subtitle.isNotBlank()) {
                                Text(source.subtitle, color = BlockMuted, fontSize = 11.sp)
                            }
                        }
                        source.actionId?.let { actionId ->
                            BlockActionButton(
                                NightBlockAction(actionId, "Open"),
                                messageId,
                                onAction,
                            )
                        }
                    }
                }
            }
        }

        is NightMessageBlock.Confirmation -> {
            BlockPanel {
                Text(
                    block.title,
                    color = if (block.destructive) BlockDanger else BlockText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(block.body, color = BlockText, fontSize = 12.sp)
                BlockActionRow(block.actions, messageId, onAction)
            }
        }

        is NightMessageBlock.Permission -> {
            BlockPanel {
                Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                Text(block.body, color = BlockText, fontSize = 12.sp)
                if (block.scopes.isNotEmpty()) {
                    Text(
                        block.scopes.joinToString(" · "),
                        color = BlockMuted,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    "Permission: " + block.grant.name,
                    color = BlockMuted,
                    fontSize = 11.sp,
                )
                if (block.destructiveConfirmationRequired) {
                    Text(
                        "Destructive actions still require confirmation.",
                        color = BlockDanger,
                        fontSize = 11.sp,
                    )
                }
                BlockActionRow(block.actions, messageId, onAction)
            }
        }

        is NightMessageBlock.Choice -> {
            BlockPanel {
                Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                if (block.prompt.isNotBlank()) {
                    Text(block.prompt, color = BlockMuted, fontSize = 12.sp)
                }

                block.options.forEach { option ->
                    Surface(
                        color = if (option.selected) Color(0xFF344A55) else BlockPanelStrong,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onAction(
                                messageId,
                                "choice:" + block.id + ":" + option.id,
                            )
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            option.previewPath?.let { preview ->
                                AsyncImage(
                                    model = preview,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(9.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, color = BlockText, fontSize = 13.sp)
                                if (option.description.isNotBlank()) {
                                    Text(option.description, color = BlockMuted, fontSize = 11.sp)
                                }
                            }

                            if (option.selected) {
                                Text("✓", color = BlockAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (block.allowOther) {
                    Text(
                        if (block.multiSelect) {
                            "Multiple choices allowed · Other supported"
                        } else {
                            "One choice · Other supported"
                        },
                        color = BlockMuted,
                        fontSize = 10.sp,
                    )
                }
                BlockActionRow(block.actions, messageId, onAction)
            }
        }

        is NightMessageBlock.Diff -> {
            BlockPanel {
                Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                Text("Before", color = BlockMuted, fontSize = 10.sp)
                Text(
                    block.before,
                    color = BlockDanger,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text("After", color = BlockMuted, fontSize = 10.sp)
                Text(
                    block.after,
                    color = Color(0xFF8FE0AF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }

        is NightMessageBlock.Connection -> {
            BlockPanel {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(block.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                        Text(block.provider, color = BlockMuted, fontSize = 11.sp)
                    }
                    Text(
                        block.state.name,
                        color = if (block.state == NightConnectionState.Error) BlockDanger else BlockAccent,
                        fontSize = 10.sp,
                    )
                }
                if (block.detail.isNotBlank()) {
                    Text(block.detail, color = BlockMuted, fontSize = 12.sp)
                }
                BlockActionRow(block.actions, messageId, onAction)
            }
        }

        is NightMessageBlock.Transfer -> {
            BlockPanel {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        block.title,
                        color = BlockText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(block.state.name, color = BlockMuted, fontSize = 10.sp)
                }
                if (block.detail.isNotBlank()) {
                    Text(block.detail, color = BlockMuted, fontSize = 12.sp)
                }
                block.progress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BlockActionRow(block.actions, messageId, onAction)
            }
        }

        is NightMessageBlock.Details -> {
            BlockPanel {
                Text(block.summary, color = BlockText, fontWeight = FontWeight.SemiBold)
                if (block.expanded) {
                    Text(block.body, color = BlockMuted, fontSize = 12.sp)
                }
                block.toggleActionId?.let { actionId ->
                    BlockActionButton(
                        NightBlockAction(
                            actionId,
                            if (block.expanded) "Show less" else "Show details",
                        ),
                        messageId,
                        onAction,
                    )
                }
            }
        }

        is NightMessageBlock.ExtensionCard -> {
            NightExtensionBlock(block, messageId, onAction)
        }

        is NightMessageBlock.Actions -> {
            BlockActionRow(block.actions, messageId, onAction)
        }
    }
}

@Composable
private fun NightExtensionBlock(
    block: NightMessageBlock.ExtensionCard,
    messageId: String,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    val snapshot = block.snapshot
    BlockPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (snapshot.iconText.isNotBlank()) {
                Surface(
                    color = Color(0xFF3A4245),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            snapshot.iconText.take(2),
                            color = BlockText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(snapshot.title, color = BlockText, fontWeight = FontWeight.SemiBold)
                if (snapshot.subtitle.isNotBlank()) {
                    Text(snapshot.subtitle, color = BlockMuted, fontSize = 11.sp)
                }
            }

            if (snapshot.badge.isNotBlank()) {
                Text(snapshot.badge, color = BlockAccent, fontSize = 10.sp)
            }
        }

        snapshot.artworkPath?.takeIf { it.isNotBlank() }?.let { artwork ->
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }

        if (snapshot.body.isNotBlank()) {
            Text(snapshot.body, color = BlockText, fontSize = 12.sp)
        }
        if (snapshot.status.isNotBlank()) {
            Text(snapshot.status, color = BlockMuted, fontSize = 11.sp)
        }
        snapshot.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        snapshot.metadata.forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    entry.label,
                    color = BlockMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    entry.value,
                    color = BlockText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        snapshot.rows.forEach { row ->
            Surface(
                color = BlockPanelStrong,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.iconText.isNotBlank()) {
                        Text(
                            row.iconText.take(2),
                            color = BlockAccent,
                            modifier = Modifier.width(28.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, color = BlockText, fontSize = 12.sp)
                        if (row.subtitle.isNotBlank()) {
                            Text(row.subtitle, color = BlockMuted, fontSize = 10.sp)
                        }
                    }
                    if (row.value.isNotBlank()) {
                        Text(row.value, color = BlockText, fontSize = 11.sp)
                    }
                }
            }
        }

        val actions = snapshot.actions.map { action ->
            NightBlockAction(
                id = action.id,
                label = action.label,
                style = when (action.style) {
                    ExtensionActionStyle.Primary -> NightBlockActionStyle.Primary
                    ExtensionActionStyle.Secondary -> NightBlockActionStyle.Secondary
                    ExtensionActionStyle.Destructive -> NightBlockActionStyle.Destructive
                },
                enabled = block.extensionAvailable || !action.requiresExtension,
            )
        }
        BlockActionRow(actions, messageId, onAction)

        if (!block.extensionAvailable) {
            Text(
                "Extension unavailable · saved content is still visible",
                color = BlockMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun BlockPanel(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanelColor, RoundedCornerShape(12.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun NightTableLine(
    cells: List<String>,
    bold: Boolean,
) {
    Row {
        cells.forEach { cell ->
            Text(
                text = cell,
                color = if (bold) BlockText else BlockMuted,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(118.dp)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun BlockActionRow(
    actions: List<NightBlockAction>,
    messageId: String,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    if (actions.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
    ) {
        actions.take(4).forEach { action ->
            BlockActionButton(action, messageId, onAction)
        }
    }
}

@Composable
private fun BlockActionButton(
    action: NightBlockAction,
    messageId: String,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    TextButton(
        enabled = action.enabled,
        onClick = { onAction(messageId, action.id) },
    ) {
        Text(
            text = action.label,
            color = when {
                !action.enabled -> BlockMuted
                action.style == NightBlockActionStyle.Destructive -> BlockDanger
                action.style == NightBlockActionStyle.Primary -> BlockAccent
                else -> BlockText
            },
            fontSize = 12.sp,
        )
    }
}
