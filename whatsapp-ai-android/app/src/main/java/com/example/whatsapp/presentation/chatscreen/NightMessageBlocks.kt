package com.example.whatsapp.presentation.chatscreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.extensions.messages.ExtensionActionStyle
import com.example.whatsapp.extensions.messages.ExtensionCardAction
import com.example.whatsapp.extensions.messages.ExtensionCardMetadata
import com.example.whatsapp.extensions.messages.ExtensionCardRow
import com.example.whatsapp.extensions.messages.ExtensionCardTemplate
import com.example.whatsapp.extensions.messages.ExtensionMessageSnapshot

/**
 * One AI/tool result message can contain multiple reusable blocks.
 *
 * Normal chat primitives (text, image, video, audio, voice, file) intentionally
 * stay as WhatsAppVisualMessage variants. NightBlockMessage is for structured
 * assistant/tool/extension output so we do not need a new message class for
 * every integration.
 */
data class NightBlockMessage(
    override val id: String,
    val blocks: List<NightMessageBlock>,
    val time: String,
    val mine: Boolean = false,
    val read: Boolean = false,
    val reply: ReplyPreview? = null,
) : WhatsAppVisualMessage

sealed interface NightMessageBlock {
    val blockId: String
}

data class NightTextBlock(
    override val blockId: String,
    val text: String,
    val title: String = "",
) : NightMessageBlock

data class NightCodeBlock(
    override val blockId: String,
    val code: String,
    val language: String = "",
    val title: String = "",
) : NightMessageBlock

data class NightCopyBlock(
    override val blockId: String,
    val text: String,
    val label: String = "Copy",
    val title: String = "",
) : NightMessageBlock

data class NightTableBlock(
    override val blockId: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val title: String = "",
) : NightMessageBlock

enum class NightProgressState {
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
    Retry,
}

data class NightProgressBlock(
    override val blockId: String,
    val title: String,
    val detail: String = "",
    val progress: Float? = null,
    val state: NightProgressState = NightProgressState.Downloading,
    val primaryActionId: String? = null,
    val primaryActionLabel: String? = null,
) : NightMessageBlock

data class NightLevelBlock(
    override val blockId: String,
    val title: String,
    val level: Int,
    val currentXp: Long,
    val nextLevelXp: Long,
    val rank: String = "",
    val detail: String = "",
    val badgeText: String = "",
    val action: NightBlockAction? = null,
) : NightMessageBlock {
    val progress: Float
        get() = if (nextLevelXp <= 0L) {
            0f
        } else {
            (currentXp.toDouble() / nextLevelXp.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }
}

data class NightToolBlock(
    override val blockId: String,
    val toolName: String,
    val title: String,
    val subtitle: String = "",
    val iconText: String = "",
    val actions: List<NightBlockAction> = emptyList(),
) : NightMessageBlock

data class NightErrorBlock(
    override val blockId: String,
    val title: String,
    val detail: String,
    val retryActionId: String? = null,
) : NightMessageBlock

data class NightSourceItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val url: String = "",
)

data class NightSourcesBlock(
    override val blockId: String,
    val title: String = "Sources",
    val sources: List<NightSourceItem>,
) : NightMessageBlock

data class NightConfirmationBlock(
    override val blockId: String,
    val title: String,
    val detail: String = "",
    val confirmAction: NightBlockAction,
    val cancelAction: NightBlockAction? = null,
    val destructive: Boolean = false,
) : NightMessageBlock

data class NightPermissionOption(
    val id: String,
    val label: String,
    val description: String = "",
    val selected: Boolean = false,
)

data class NightPermissionBlock(
    override val blockId: String,
    val title: String,
    val detail: String = "",
    val options: List<NightPermissionOption>,
    val allowActionId: String = "allow",
    val destructiveConfirmationRequired: Boolean = false,
) : NightMessageBlock

data class NightQuestionOption(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val previewPath: String? = null,
)

data class NightQuestionBlock(
    override val blockId: String,
    val title: String,
    val detail: String = "",
    val options: List<NightQuestionOption>,
    val multiple: Boolean = false,
    val allowCustom: Boolean = true,
) : NightMessageBlock

data class NightDiffBlock(
    override val blockId: String,
    val title: String = "Changes",
    val before: String,
    val after: String,
) : NightMessageBlock

data class NightConnectionBlock(
    override val blockId: String,
    val service: String,
    val title: String,
    val detail: String = "",
    val connected: Boolean = false,
    val action: NightBlockAction? = null,
) : NightMessageBlock

data class NightExtensionBlock(
    override val blockId: String,
    val snapshot: ExtensionMessageSnapshot,
    val extensionAvailable: Boolean = true,
) : NightMessageBlock

data class NightBlockAction(
    val id: String,
    val label: String,
    val style: NightBlockActionStyle = NightBlockActionStyle.Secondary,
    val enabled: Boolean = true,
)

enum class NightBlockActionStyle {
    Primary,
    Secondary,
    Destructive,
}

private val BlockBubble = Color(0xFF242625)
private val BlockPanel = Color(0xFF303436)
private val BlockText = Color(0xFFECEDEE)
private val BlockMuted = Color(0xFF9EA7AB)
private val BlockAccent = Color(0xFFCF4A69)
private val BlockGreen = Color(0xFF25D366)
private val BlockBlue = Color(0xFF53BDEB)

@Composable
fun NightBlockMessageBubble(
    item: NightBlockMessage,
    appearance: NightChatAppearance,
    onAction: (messageId: String, actionId: String) -> Unit,
    onReplyPreviewClick: (String) -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .background(
                    color = if (item.mine) appearance.userBubbleColor else BlockBubble,
                    shape = RoundedCornerShape(
                        topStart = if (item.mine) 16.dp else 5.dp,
                        topEnd = if (item.mine) 5.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                )
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item.reply?.let { reply ->
                CurrentReplyBlock(
                    reply = reply,
                    appearance = appearance,
                    mine = item.mine,
                    onClick = { onReplyPreviewClick(reply.messageId) },
                )
            }

            item.blocks.forEach { block ->
                NightBlockContent(
                    messageId = item.id,
                    block = block,
                    onAction = onAction,
                )
            }

            Text(
                text = item.time,
                color = BlockMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 1.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun NightBlockContent(
    messageId: String,
    block: NightMessageBlock,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    when (block) {
        is NightTextBlock -> NightTextBlockContent(block)
        is NightCodeBlock -> NightCodeBlockContent(block)
        is NightCopyBlock -> NightCopyBlockContent(block)
        is NightTableBlock -> NightTableBlockContent(block)
        is NightProgressBlock -> NightProgressBlockContent(messageId, block, onAction)
        is NightLevelBlock -> NightLevelBlockContent(messageId, block, onAction)
        is NightToolBlock -> NightToolBlockContent(messageId, block, onAction)
        is NightErrorBlock -> NightErrorBlockContent(messageId, block, onAction)
        is NightSourcesBlock -> NightSourcesBlockContent(messageId, block, onAction)
        is NightConfirmationBlock -> NightConfirmationBlockContent(messageId, block, onAction)
        is NightPermissionBlock -> NightPermissionBlockContent(messageId, block, onAction)
        is NightQuestionBlock -> NightQuestionBlockContent(messageId, block, onAction)
        is NightDiffBlock -> NightDiffBlockContent(block)
        is NightConnectionBlock -> NightConnectionBlockContent(messageId, block, onAction)
        is NightExtensionBlock -> NightExtensionBlockContent(messageId, block, onAction)
    }
}

@Composable
private fun NightTextBlockContent(block: NightTextBlock) {
    Column {
        BlockTitle(block.title)
        Text(
            text = block.text,
            color = BlockText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun NightCodeBlockContent(block: NightCodeBlock) {
    Column {
        if (block.title.isNotBlank() || block.language.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.title.ifBlank { block.language.ifBlank { "Code" } },
                    color = BlockMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                if (block.language.isNotBlank() && block.title.isNotBlank()) {
                    Text(block.language, color = BlockMuted, fontSize = 10.sp)
                }
            }
        }
        Text(
            text = block.code,
            color = BlockText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(BlockPanel, RoundedCornerShape(10.dp))
                .padding(10.dp),
        )
    }
}

@Composable
private fun NightCopyBlockContent(block: NightCopyBlock) {
    val context = LocalContext.current
    Column {
        BlockTitle(block.title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BlockPanel, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.text,
                color = BlockText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = block.label,
                color = BlockAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Night", block.text))
                }.padding(4.dp),
            )
        }
    }
}

@Composable
private fun NightTableBlockContent(block: NightTableBlock) {
    Column {
        BlockTitle(block.title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(BlockPanel, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (block.columns.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.columns.forEach { column ->
                        Text(
                            text = column,
                            color = BlockMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 84.dp, max = 150.dp),
                        )
                    }
                }
            }
            block.rows.take(12).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.take(block.columns.size.coerceAtLeast(row.size)).forEach { cell ->
                        Text(
                            text = cell,
                            color = BlockText,
                            fontSize = 11.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(min = 84.dp, max = 150.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NightProgressBlockContent(
    messageId: String,
    block: NightProgressBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (block.state) {
                    NightProgressState.Paused -> Icons.Default.PlayArrow
                    NightProgressState.Completed -> Icons.Default.Check
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                tint = when (block.state) {
                    NightProgressState.Completed -> BlockGreen
                    NightProgressState.Failed -> Color(0xFFFF6B78)
                    else -> BlockText
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, color = BlockText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (block.detail.isNotBlank()) {
                    Text(block.detail, color = BlockMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(
                text = block.state.name,
                color = BlockMuted,
                fontSize = 9.sp,
            )
        }

        block.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
                color = BlockGreen,
                trackColor = Color(0xFF42494C),
            )
        }

        val actionId = block.primaryActionId
        val actionLabel = block.primaryActionLabel
        if (!actionId.isNullOrBlank() && !actionLabel.isNullOrBlank()) {
            Text(
                text = actionLabel,
                color = BlockAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onAction(messageId, actionId) }
                    .padding(top = 8.dp, start = 8.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun NightLevelBlockContent(
    messageId: String,
    block: NightLevelBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF49313A),
                shape = CircleShape,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = block.badgeText.ifBlank { block.level.toString() }.take(3),
                        color = BlockText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.title,
                    color = BlockText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append("Level ")
                        append(block.level.coerceAtLeast(0))
                        if (block.rank.isNotBlank()) {
                            append(" • ")
                            append(block.rank)
                        }
                    },
                    color = BlockMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                text = block.currentXp.coerceAtLeast(0L).toString() +
                    " / " +
                    block.nextLevelXp.coerceAtLeast(0L).toString() +
                    " XP",
                color = BlockMuted,
                fontSize = 9.sp,
            )
        }

        LinearProgressIndicator(
            progress = { block.progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .height(5.dp),
            color = BlockAccent,
            trackColor = Color(0xFF42494C),
        )

        if (block.detail.isNotBlank()) {
            Text(
                text = block.detail,
                color = BlockMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
        }

        block.action?.let { action ->
            BlockActions(
                messageId = messageId,
                actions = listOf(action),
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun NightToolBlockContent(
    messageId: String,
    block: NightToolBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                color = Color(0xFFF4F4F2),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (block.iconText.isNotBlank()) {
                        Text(
                            text = block.iconText.take(2),
                            color = Color(0xFF151515),
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color(0xFF151515),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, color = BlockText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(block.toolName, color = BlockMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 1.dp))
                if (block.subtitle.isNotBlank()) {
                    Text(
                        block.subtitle,
                        color = BlockMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }

        BlockActions(messageId, block.actions, onAction)
    }
}

@Composable
private fun NightErrorBlockContent(
    messageId: String,
    block: NightErrorBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF40262B), RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF8CA0), modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(7.dp))
            Text(block.title, color = BlockText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(block.detail, color = BlockMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
        block.retryActionId?.let { retry ->
            Text(
                text = "Retry",
                color = Color(0xFFFF8CA0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.End).clickable { onAction(messageId, retry) }.padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun NightSourcesBlockContent(
    messageId: String,
    block: NightSourcesBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, contentDescription = null, tint = BlockBlue, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(block.title, color = BlockText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        block.sources.take(8).forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAction(messageId, "source:" + source.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.title, color = BlockText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (source.subtitle.isNotBlank()) {
                        Text(source.subtitle, color = BlockMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = BlockMuted, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun NightConfirmationBlockContent(
    messageId: String,
    block: NightConfirmationBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Text(block.title, color = BlockText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (block.detail.isNotBlank()) {
            Text(block.detail, color = BlockMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
        }

        val actions = buildList {
            add(
                block.confirmAction.copy(
                    style = if (block.destructive) NightBlockActionStyle.Destructive else block.confirmAction.style
                )
            )
            block.cancelAction?.let(::add)
        }
        BlockActions(messageId, actions, onAction)
    }
}

@Composable
private fun NightPermissionBlockContent(
    messageId: String,
    block: NightPermissionBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = BlockAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(7.dp))
            Text(block.title, color = BlockText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (block.detail.isNotBlank()) {
            Text(block.detail, color = BlockMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
        }

        block.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAction(messageId, "permission:" + option.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (option.selected) BlockAccent else Color(0xFF454B4E),
                    shape = CircleShape,
                    modifier = Modifier.size(18.dp),
                ) {
                    if (option.selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.label, color = BlockText, fontSize = 11.sp)
                    if (option.description.isNotBlank()) {
                        Text(option.description, color = BlockMuted, fontSize = 9.sp, lineHeight = 12.sp)
                    }
                }
            }
        }

        if (block.destructiveConfirmationRequired) {
            Text(
                text = "Destructive actions require a separate confirmation.",
                color = Color(0xFFFF8CA0),
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        BlockActions(
            messageId = messageId,
            actions = listOf(
                NightBlockAction(block.allowActionId, "Continue", NightBlockActionStyle.Primary)
            ),
            onAction = onAction,
        )
    }
}

@Composable
private fun NightQuestionBlockContent(
    messageId: String,
    block: NightQuestionBlock,
    onAction: (String, String) -> Unit,
) {
    Column {
        Text(block.title, color = BlockText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (block.detail.isNotBlank()) {
            Text(block.detail, color = BlockMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
        }

        Column(
            modifier = Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            block.options.take(8).forEach { option ->
                Surface(
                    color = if (option.selected) Color(0xFF7A263E) else BlockPanel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(messageId, "question:" + block.blockId + ":" + option.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (option.selected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(option.label, color = BlockText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (block.allowCustom) {
                Text(
                    text = "Other…",
                    color = BlockAccent,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { onAction(messageId, "question:" + block.blockId + ":custom") }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                )
            }
        }

        Text(
            text = if (block.multiple) "Choose one or more" else "Choose one",
            color = BlockMuted,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun NightDiffBlockContent(block: NightDiffBlock) {
    Column {
        BlockTitle(block.title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BlockPanel, RoundedCornerShape(10.dp))
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = block.before,
                color = Color(0xFFFF9BA8),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
            Text(
                text = block.after,
                color = Color(0xFF8DE6AD),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun NightConnectionBlockContent(
    messageId: String,
    block: NightConnectionBlock,
    onAction: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Extension, contentDescription = null, tint = if (block.connected) BlockGreen else BlockMuted, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, color = BlockText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(block.service, color = BlockMuted, fontSize = 10.sp)
            }
            Text(
                text = if (block.connected) "Connected" else "Not connected",
                color = if (block.connected) BlockGreen else BlockMuted,
                fontSize = 9.sp,
            )
        }
        if (block.detail.isNotBlank()) {
            Text(block.detail, color = BlockMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
        }
        block.action?.let { action ->
            BlockActions(messageId, listOf(action), onAction)
        }
    }
}

@Composable
private fun NightExtensionBlockContent(
    messageId: String,
    block: NightExtensionBlock,
    onAction: (String, String) -> Unit,
) {
    val snapshot = block.snapshot
    val unsupported =
        !snapshot.hasValidNamespace() ||
            snapshot.schemaVersion > com.example.whatsapp.extensions.messages.NightExtensionMessageApi.SUPPORTED_SCHEMA_VERSION

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlockPanel, RoundedCornerShape(11.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                color = Color(0xFFF4F4F2),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = snapshot.iconText.ifBlank {
                            snapshot.extensionName.take(1).uppercase()
                        }.take(2),
                        color = Color(0xFF151515),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(snapshot.title, color = BlockText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = snapshot.badge.ifBlank { snapshot.extensionName },
                    color = BlockMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
                if (snapshot.subtitle.isNotBlank()) {
                    Text(
                        snapshot.subtitle,
                        color = BlockMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (unsupported) {
            Text(
                text = "This card needs a newer renderer.",
                color = BlockMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
            return@Column
        }

        if (snapshot.body.isNotBlank()) {
            Text(snapshot.body, color = BlockText, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }

        snapshot.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
                color = BlockGreen,
                trackColor = Color(0xFF42494C),
            )
        }

        snapshot.metadata.take(4).forEach { meta ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text(meta.label, color = BlockMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                if (meta.value.isNotBlank()) {
                    Text(meta.value, color = BlockText, fontSize = 10.sp)
                }
            }
        }

        snapshot.rows.take(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (row.iconText.isNotBlank()) {
                    Text(row.iconText.take(2), color = BlockMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, color = BlockText, fontSize = 11.sp)
                    if (row.subtitle.isNotBlank()) {
                        Text(row.subtitle, color = BlockMuted, fontSize = 9.sp)
                    }
                }
                if (row.value.isNotBlank()) {
                    Text(row.value, color = BlockMuted, fontSize = 9.sp)
                }
            }
        }

        val actions = snapshot.actions.take(3).map { action ->
            NightBlockAction(
                id = action.id,
                label = action.label,
                style = when (action.style) {
                    ExtensionActionStyle.Primary -> NightBlockActionStyle.Primary
                    ExtensionActionStyle.Destructive -> NightBlockActionStyle.Destructive
                    ExtensionActionStyle.Secondary -> NightBlockActionStyle.Secondary
                },
                enabled = block.extensionAvailable || !action.requiresExtension,
            )
        }
        BlockActions(messageId, actions, onAction)

        if (!block.extensionAvailable) {
            Text(
                text = "Extension unavailable",
                color = BlockMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
private fun BlockActions(
    messageId: String,
    actions: List<NightBlockAction>,
    onAction: (String, String) -> Unit,
) {
    if (actions.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        actions.take(3).forEach { action ->
            val background = when (action.style) {
                NightBlockActionStyle.Primary -> Color(0xFF9D2142)
                NightBlockActionStyle.Destructive -> Color(0xFF5A252C)
                NightBlockActionStyle.Secondary -> Color(0xFF3A4144)
            }
            Surface(
                color = if (action.enabled) background else Color(0xFF34383A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = action.enabled) {
                        onAction(messageId, action.id)
                    },
            ) {
                Text(
                    text = action.label,
                    color = if (action.enabled) Color.White else BlockMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun BlockTitle(title: String) {
    if (title.isNotBlank()) {
        Text(
            text = title,
            color = BlockText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

fun nightBlockPreviewMessages(): List<WhatsAppVisualMessage> {
    val extension = ExtensionMessageSnapshot(
        extensionId = "notion",
        messageType = "notion.page",
        template = ExtensionCardTemplate.Content,
        extensionName = "Notion Assistant",
        title = "Project Night",
        subtitle = "Product workspace",
        body = "14 pages • Updated 3 min ago",
        iconText = "N",
        badge = "Productivity",
        metadata = listOf(
            ExtensionCardMetadata("Workspace", "Night"),
            ExtensionCardMetadata("Owner", "You"),
        ),
        rows = listOf(
            ExtensionCardRow("Roadmap", "Planning", "12 items", "R"),
            ExtensionCardRow("Night Android", "Build", "Active", "A"),
        ),
        actions = listOf(
            ExtensionCardAction("open", "Open", ExtensionActionStyle.Primary),
            ExtensionCardAction("summarize", "Summarize"),
        ),
    )

    return listOf(
        NightBlockMessage(
            id = "blocks-code",
            time = "07:42",
            blocks = listOf(
                NightTextBlock("intro", "I found the issue. The retry count was resetting when the player generation changed."),
                NightCodeBlock(
                    blockId = "code",
                    title = "Fix",
                    language = "kotlin",
                    code = "if (stalled) {\n    retryGeneration += 1\n    resumePosition = player.time\n}",
                ),
                NightCopyBlock(
                    blockId = "copy",
                    title = "Command",
                    text = "./gradlew :app:assembleDebug",
                ),
            ),
        ),
        NightBlockMessage(
            id = "blocks-table",
            time = "07:43",
            blocks = listOf(
                NightTableBlock(
                    blockId = "table",
                    title = "Validation",
                    columns = listOf("Check", "State", "Result"),
                    rows = listOf(
                        listOf("Core", "Done", "Green"),
                        listOf("Video", "Done", "Green"),
                        listOf("Reader", "Done", "Green"),
                    ),
                ),
                NightProgressBlock(
                    blockId = "download",
                    title = "Episode 8",
                    detail = "184 MB of 428 MB • 5.7 MB/s",
                    progress = 0.43f,
                    state = NightProgressState.Downloading,
                    primaryActionId = "pause",
                    primaryActionLabel = "Pause",
                ),
                NightLevelBlock(
                    blockId = "level",
                    title = "Researcher",
                    level = 12,
                    currentXp = 760,
                    nextLevelXp = 1000,
                    rank = "Gold",
                    detail = "240 XP until Level 13",
                    badgeText = "12",
                    action = NightBlockAction("view_progress", "Details"),
                ),
            ),
        ),
        NightBlockMessage(
            id = "blocks-interactive",
            time = "07:44",
            blocks = listOf(
                NightQuestionBlock(
                    blockId = "question",
                    title = "What should I focus on next?",
                    detail = "Choose one. You can also type your own answer.",
                    options = listOf(
                        NightQuestionOption("messages", "Message blocks", selected = true),
                        NightQuestionOption("extensions", "Extensions"),
                        NightQuestionOption("calls", "Calls"),
                    ),
                ),
                NightPermissionBlock(
                    blockId = "permission",
                    title = "Allow this action?",
                    detail = "Choose how much access this tool should get.",
                    options = listOf(
                        NightPermissionOption("once", "Allow once", selected = true),
                        NightPermissionOption("chat", "This chat"),
                        NightPermissionOption("always", "Always for selected actions"),
                    ),
                    destructiveConfirmationRequired = true,
                ),
            ),
        ),
        NightBlockMessage(
            id = "blocks-tool-extension",
            time = "07:45",
            blocks = listOf(
                NightToolBlock(
                    blockId = "tool",
                    toolName = "GitHub",
                    title = "Pull request is ready",
                    subtitle = "All required checks passed.",
                    iconText = "GH",
                    actions = listOf(
                        NightBlockAction("open_pr", "Open", NightBlockActionStyle.Primary),
                        NightBlockAction("details", "Details"),
                    ),
                ),
                NightExtensionBlock(
                    blockId = "extension",
                    snapshot = extension,
                ),
            ),
        ),
        NightBlockMessage(
            id = "blocks-more",
            time = "07:46",
            blocks = listOf(
                NightSourcesBlock(
                    blockId = "sources",
                    sources = listOf(
                        NightSourceItem("1", "Android Media3 Transformer", "Developer docs"),
                        NightSourceItem("2", "LibVLC track metadata", "API reference"),
                    ),
                ),
                NightDiffBlock(
                    blockId = "diff",
                    before = "- Extension timed out",
                    after = "+ Extension unavailable",
                ),
                NightConnectionBlock(
                    blockId = "connection",
                    service = "Notion",
                    title = "Workspace connection",
                    detail = "Use the existing connection for this chat.",
                    connected = true,
                    action = NightBlockAction("manage_connection", "Manage"),
                ),
            ),
        ),
    )
}
