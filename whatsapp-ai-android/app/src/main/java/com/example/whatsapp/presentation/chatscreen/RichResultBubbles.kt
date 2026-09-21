package com.example.whatsapp.presentation.chatscreen

import coil.compose.AsyncImage
import com.example.whatsapp.data.browser.NightBrowserSpec
import com.example.whatsapp.extensions.messages.ExtensionActionStyle
import com.example.whatsapp.extensions.messages.ExtensionCardAction
import com.example.whatsapp.extensions.messages.ExtensionCardTemplate
import com.example.whatsapp.extensions.messages.ExtensionMessageSnapshot
import com.example.whatsapp.extensions.messages.NightExtensionMessageApi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

sealed interface RichResultMessage : WhatsAppVisualMessage

data class MessageAction(
    val id: String,
    val label: String,
)

data class ButtonResultMessage(
    override val id: String,
    val title: String,
    val body: String,
    val actions: List<MessageAction>,
    val time: String,
) : RichResultMessage

data class FileResultMessage(
    override val id: String,
    val name: String,
    val detail: String,
    val time: String,
) : RichResultMessage

data class AnimeResultMessage(
    override val id: String,
    val title: String,
    val episode: String,
    val quality: String,
    val size: String,
    val time: String,
    val coverPath: String? = null,
    val mediaType: String = "TV",
    val status: String = "Ongoing",
    val description: String = "",
    val primaryActionLabel: String = "Play Episode 1",
) : RichResultMessage

data class MangaResultMessage(
    override val id: String,
    val title: String,
    val chapter: String,
    val source: String,
    val description: String,
    val time: String,
    val coverPath: String? = null,
    val status: String = "Ongoing",
    val primaryActionLabel: String = "Read Chapter 1",
) : RichResultMessage

data class ChoiceResultMessage(
    override val id: String,
    val title: String,
    val options: List<String>,
    val selectedIndex: Int? = null,
    val selectedBy: String? = null,
    val mine: Boolean = false,
    val time: String,
) : RichResultMessage

data class LinkPreviewMessage(
    override val id: String,
    val title: String,
    val description: String,
    val domain: String,
    val thumbnailPath: String? = null,
    val mine: Boolean = false,
    val time: String,
) : RichResultMessage

data class GeneratedImageResultMessage(
    override val id: String,
    val title: String,
    val detail: String,
    val localPath: String?,
    val time: String,
) : RichResultMessage

enum class CreationKind {
    Image,
    Pdf,
}

enum class CreationState {
    Working,
    Ready,
    Failed,
}

data class CreationResultMessage(
    override val id: String,
    val kind: CreationKind,
    val state: CreationState,
    val title: String,
    val detail: String,
    val progress: Float? = null,
    val localPath: String? = null,
    val fileName: String? = null,
    val time: String,
) : RichResultMessage

data class ImageSearchResultMessage(
    override val id: String,
    val source: String,
    val resultCount: Int,
    val time: String,
) : RichResultMessage

data class DownloadResultMessage(
    override val id: String,
    val title: String,
    val detail: String,
    val progress: Float,
    val time: String,
) : RichResultMessage

data class ToolResultMessage(
    override val id: String,
    val toolName: String,
    val title: String,
    val subtitle: String,
    val time: String,
    val iconText: String = "",
    val actions: List<MessageAction> = emptyList(),
) : RichResultMessage

data class ExtensionResultMessage(
    override val id: String,
    val snapshot: ExtensionMessageSnapshot,
    val time: String,
    val extensionAvailable: Boolean = true,
) : RichResultMessage


data class BrowserResultMessage(
    override val id: String,
    val spec: NightBrowserSpec,
    val time: String,
    val sourceLabel: String = "",
) : RichResultMessage

private val RichBubble = Color(0xFF242625)
private val RichPanel = Color(0xFF303436)
private val RichText = Color(0xFFECEDEE)
private val RichMuted = Color(0xFF9EA7AB)
private val RichAccent = Color(0xFF25D366)
private val RichBlue = Color(0xFF53BDEB)

@Composable
fun RichResultBubble(
    item: RichResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit = { _, _ -> },
) {
    when (item) {
        is ButtonResultMessage -> ButtonResultBubble(item, onAction)
        is FileResultMessage -> FileResultBubble(item)
        is AnimeResultMessage -> AnimeResultBubble(item, onAction)
        is MangaResultMessage -> MangaResultBubble(item, onAction)
        is ChoiceResultMessage -> ChoiceResultBubble(item, onAction)
        is LinkPreviewMessage -> LinkPreviewBubble(item)
        is GeneratedImageResultMessage -> GeneratedImageResultBubble(item)
        is CreationResultMessage -> CreationResultBubble(item)
        is ImageSearchResultMessage -> ImageSearchBubble(item)
        is DownloadResultMessage -> DownloadResultBubble(item)
        is ToolResultMessage -> ToolResultBubble(item, onAction)
        is ExtensionResultMessage -> ExtensionResultBubble(item, onAction)
        is BrowserResultMessage -> NightBrowserMessageBubble(
            messageId = item.id,
            spec = item.spec,
            time = item.time,
            sourceLabel = item.sourceLabel,
            onAction = onAction,
        )
    }
}

@Composable
private fun BubbleFrame(
    time: String,
    modifier: Modifier = Modifier,
    mine: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = modifier
                .widthIn(max = 350.dp)
                .clip(
                    if (mine) {
                        RoundedCornerShape(topStart = 16.dp, topEnd = 5.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    } else {
                        RoundedCornerShape(topStart = 5.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    }
                )
                .background(if (mine) Color(0xFF7E112E) else RichBubble)
                .padding(7.dp),
        ) {
            content()

            Text(
                text = time,
                color = RichMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun ButtonResultBubble(
    item: ButtonResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    BubbleFrame(time = item.time) {
        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4A2934)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFFFF6D91),
                    modifier = Modifier.size(25.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (item.body.isNotBlank()) {
                    Text(
                        text = item.body,
                        color = RichMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        if (item.actions.size in 2..3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item.actions.forEach { action ->
                    Surface(
                        color = RichPanel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAction(item.id, action.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = actionIcon(action.id),
                                contentDescription = null,
                                tint = Color(0xFFFF6D91),
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = action.label,
                                color = Color(0xFFFF7998),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.actions.forEach { action ->
                    Surface(
                        color = RichPanel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(item.id, action.id) },
                    ) {
                        Text(
                            text = action.label,
                            color = Color(0xFFFF7998),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun FileResultBubble(item: FileResultMessage) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(RichPanel)
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFC43F59)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (item.name.endsWith(".pdf", ignoreCase = true)) "PDF" else "FILE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = RichText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.detail,
                    color = RichMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                color = Color(0xFF3A4144),
                shape = CircleShape,
                modifier = Modifier
                    .size(40.dp)
                    .clickable {},
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Open file",
                        tint = RichText,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}


@Composable
private fun AnimeResultBubble(
    item: AnimeResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            MediaCover(
                model = item.coverPath,
                fallback = item.title.take(2).uppercase(),
                modifier = Modifier
                    .width(96.dp)
                    .height(132.dp),
            )

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.mediaType + " • " + item.episode + " • " + item.status,
                    color = RichMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetaChip(item.quality)
                    MetaChip(if (item.size.isBlank()) "HD" else item.size)
                    MetaChip("HD", accent = true)
                }

                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        color = RichMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FullActionButton(
                label = item.primaryActionLabel,
                icon = Icons.Default.PlayArrow,
                primary = true,
                modifier = Modifier.weight(1.4f),
                onClick = { onAction(item.id, "play") },
            )
            FullActionButton(
                label = "Download",
                icon = Icons.Default.Download,
                modifier = Modifier.weight(1f),
                onClick = { onAction(item.id, "download") },
            )
        }
    }
}


@Composable
private fun MangaResultBubble(
    item: MangaResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            MediaCover(
                model = item.coverPath,
                fallback = item.title.take(2).uppercase(),
                modifier = Modifier
                    .width(96.dp)
                    .height(132.dp),
            )

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Manga • " + item.chapter + " • " + item.status,
                    color = RichMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                MetaChip(
                    text = item.source,
                    modifier = Modifier.padding(top = 7.dp),
                )

                Text(
                    text = item.description,
                    color = RichMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FullActionButton(
                label = item.primaryActionLabel,
                icon = Icons.Default.MenuBook,
                primary = true,
                modifier = Modifier.weight(1.25f),
                onClick = { onAction(item.id, "read") },
            )
            FullActionButton(
                label = "Download",
                icon = Icons.Default.Download,
                modifier = Modifier.weight(1f),
                onClick = { onAction(item.id, "download") },
            )
            FullActionButton(
                label = "Add to Library",
                icon = Icons.Default.Description,
                modifier = Modifier.weight(1.15f),
                onClick = { onAction(item.id, "library") },
            )
        }
    }
}


@Composable
private fun ChoiceResultBubble(
    item: ChoiceResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    BubbleFrame(
        time = item.time,
        mine = item.mine,
    ) {
        Text(
            text = item.title,
            color = RichText,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (item.options.size in 2..3 && item.options.all { it.length <= 18 }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item.options.forEachIndexed { index, option ->
                    ChoiceOption(
                        text = option,
                        selected = item.selectedIndex == index,
                        enabled = item.selectedIndex == null,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(item.id, "option_" + index) },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(top = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.options.forEachIndexed { index, option ->
                    ChoiceOption(
                        text = option,
                        selected = item.selectedIndex == index,
                        enabled = item.selectedIndex == null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onAction(item.id, "option_" + index) },
                    )
                }
            }
        }

        item.selectedBy?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "Chosen by " + it,
                color = RichMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }
    }
}


@Composable
private fun LinkPreviewBubble(item: LinkPreviewMessage) {
    BubbleFrame(
        time = item.time,
        mine = item.mine,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (item.mine) Color(0xFF6E1028) else RichPanel
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(92.dp)
                    .background(Color(0xFF343A3D)),
                contentAlignment = Alignment.Center,
            ) {
                val file = item.thumbnailPath?.let(::File)
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(112.dp)
                            .height(92.dp),
                    )
                } else if (!item.thumbnailPath.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailPath,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(112.dp)
                            .height(92.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = RichMuted,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.description,
                    color = RichMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = item.domain,
                    color = RichMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GeneratedImageResultBubble(item: GeneratedImageResultMessage) {
    BubbleFrame(time = item.time) {
        if (item.title.isNotBlank()) {
            Text(
                text = item.title,
                color = RichText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
            )
        }

        val file = item.localPath?.let(::File)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF14191B)),
            contentAlignment = Alignment.Center,
        ) {
            if (file != null && file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = RichMuted,
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        if (item.detail.isNotBlank()) {
            Text(
                text = item.detail,
                color = RichMuted,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 3.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun MediaCover(
    model: String?,
    fallback: String,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF49334F), Color(0xFF171D23))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        val file = model?.let(::File)
        when {
            file != null && file.exists() -> {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                )
            }

            !model.isNullOrBlank() -> {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                )
            }

            else -> {
                Text(
                    text = fallback,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun MetaChip(
    text: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (accent) Color(0xFF7A263E) else Color(0xFF3A4043),
        shape = RoundedCornerShape(9.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = if (accent) Color(0xFFFF7B9B) else RichText,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FullActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        color = when {
            !enabled -> Color(0xFF34383A)
            primary -> Color(0xFFB51E42)
            else -> RichPanel
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color.White else RichMuted,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = if (enabled) Color.White else RichMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChoiceOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) Color(0xFF9D2142) else RichPanel,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                color = if (selected) Color.White else Color(0xFFFF7998),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun actionIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector =
    when {
        id.contains("surprise", ignoreCase = true) -> Icons.Default.AutoAwesome
        id.contains("anime", ignoreCase = true) -> Icons.Default.Movie
        id.contains("movie", ignoreCase = true) -> Icons.Default.Tv
        id.contains("open", ignoreCase = true) -> Icons.Default.OpenInNew
        id.contains("setup", ignoreCase = true) -> Icons.Default.Settings
        id.contains("learn", ignoreCase = true) -> Icons.Default.Info
        id.contains("download", ignoreCase = true) -> Icons.Default.Download
        id.contains("read", ignoreCase = true) -> Icons.Default.MenuBook
        else -> Icons.Default.AutoAwesome
    }

@Composable
private fun RichActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        color = Color(0xFF3A4144),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.clickable {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RichText,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = RichText,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CreationResultBubble(item: CreationResultMessage) {
    BubbleFrame(time = item.time) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (item.kind) {
                            CreationKind.Image -> Color(0xFF344A68)
                            CreationKind.Pdf -> Color(0xFFB9364F)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (item.kind) {
                        CreationKind.Image -> Icons.Default.Image
                        CreationKind.Pdf -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        text = item.detail,
                        color = RichMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Text(
                text = when (item.state) {
                    CreationState.Working -> "Creating"
                    CreationState.Ready -> "Ready"
                    CreationState.Failed -> "Failed"
                },
                color = when (item.state) {
                    CreationState.Working -> RichBlue
                    CreationState.Ready -> RichAccent
                    CreationState.Failed -> Color(0xFFFF6B78)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (item.state == CreationState.Working) {
            LinearProgressIndicator(
                progress = { item.progress?.coerceIn(0f, 1f) ?: 0.15f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFCF4A69),
                trackColor = Color(0xFF41484B),
            )
        } else if (item.state == CreationState.Ready) {
            when (item.kind) {
                CreationKind.Image -> {
                    val file = item.localPath?.let(::File)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color(0xFF15191B)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (file != null && file.exists()) {
                            AsyncImage(
                                model = file,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = RichMuted,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                }

                CreationKind.Pdf -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(RichPanel)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFC43F59)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "PDF",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = item.fileName ?: "Document.pdf",
                            color = RichText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Open PDF",
                            tint = RichText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSearchBubble(item: ImageSearchResultMessage) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = RichAccent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = item.source,
                    color = RichText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.resultCount.toString() + " results",
                    color = RichMuted,
                    fontSize = 10.sp,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SearchTile("1", Modifier.weight(1f))
                SearchTile("2", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SearchTile("3", Modifier.weight(1f))
                SearchTile("4", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchTile(label: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF354F52), Color(0xFF52796F))
                )
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.padding(7.dp),
        )
    }
}

@Composable
private fun DownloadResultBubble(item: DownloadResultMessage) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A4144)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = RichText,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.detail,
                    color = RichMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 7.dp),
                )
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
                    color = RichAccent,
                    trackColor = Color(0xFF42494C),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Pause",
                tint = RichText,
                modifier = Modifier.size(21.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = ((item.progress * 100).toInt()).toString() + "%",
                color = RichMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ToolResultBubble(
    item: ToolResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF343A3D)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.iconText.isNotBlank()) {
                    Surface(
                        color = Color(0xFFF4F4F2),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.iconText.take(2),
                                color = Color(0xFF151515),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = RichText,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.toolName,
                    color = RichMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = item.subtitle,
                    color = RichMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }

        if (item.actions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item.actions.take(3).forEachIndexed { index, action ->
                    FullActionButton(
                        label = action.label,
                        icon = actionIcon(action.id),
                        primary = index == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(item.id, action.id) },
                    )
                }
            }
        }
    }
}



@Composable
private fun ExtensionResultBubble(
    item: ExtensionResultMessage,
    onAction: (messageId: String, actionId: String) -> Unit,
) {
    val snapshot = item.snapshot
    val unsupported =
        !snapshot.hasValidNamespace() ||
            snapshot.schemaVersion > NightExtensionMessageApi.SUPPORTED_SCHEMA_VERSION

    if (unsupported) {
        BubbleFrame(time = item.time) {
            Text(
                text = "Extension result",
                color = RichText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = snapshot.extensionName,
                color = RichMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "This content requires a newer Night card renderer.",
                color = RichMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
            FullActionButton(
                label = "Open in extension",
                icon = Icons.Default.OpenInNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                primary = true,
                enabled = item.extensionAvailable,
                onClick = { onAction(item.id, "open_extension") },
            )
        }
        return
    }

    if (
        snapshot.template == ExtensionCardTemplate.Configuration &&
        snapshot.configuration != null
    ) {
        NightExtensionConfigurationBubble(
            item = item,
            onAction = onAction,
        )
        return
    }

    if (
        snapshot.template == ExtensionCardTemplate.Browser &&
        snapshot.browser != null
    ) {
        NightBrowserMessageBubble(
            messageId = item.id,
            spec = snapshot.browser,
            time = item.time,
            sourceLabel = snapshot.extensionName,
            onAction = onAction,
        )
        return
    }

    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            val leadingSize = when (snapshot.template) {
                ExtensionCardTemplate.Media,
                ExtensionCardTemplate.Gallery,
                ExtensionCardTemplate.Entity -> 72.dp
                else -> 58.dp
            }

            Box(
                modifier = Modifier
                    .size(leadingSize)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF343A3D)),
                contentAlignment = Alignment.Center,
            ) {
                if (!snapshot.artworkPath.isNullOrBlank()) {
                    val artworkFile = File(snapshot.artworkPath)
                    AsyncImage(
                        model = if (artworkFile.exists()) artworkFile else snapshot.artworkPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(leadingSize),
                    )
                } else {
                    Surface(
                        color = Color(0xFFF4F4F2),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = snapshot.iconText.ifBlank {
                                    snapshot.extensionName.take(1).uppercase()
                                }.take(2),
                                color = Color(0xFF151515),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.title,
                    color = RichText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = snapshot.badge.ifBlank { snapshot.extensionName },
                    color = RichMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (snapshot.subtitle.isNotBlank()) {
                    Text(
                        text = snapshot.subtitle,
                        color = RichMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }

        if (snapshot.body.isNotBlank()) {
            Text(
                text = snapshot.body,
                color = RichText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val badges = buildList {
            if (snapshot.status.isNotBlank()) add(snapshot.status)
            snapshot.metadata.take(3).forEach { metadata ->
                add(
                    if (metadata.value.isBlank()) metadata.label
                    else metadata.label + " • " + metadata.value
                )
            }
        }.take(3)

        if (badges.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                badges.forEach { label ->
                    Surface(
                        color = RichPanel,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            color = RichMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        snapshot.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = RichAccent,
                trackColor = Color(0xFF42494C),
            )
        }

        if (snapshot.rows.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                snapshot.rows.take(4).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (row.iconText.isNotBlank()) {
                            Surface(
                                color = RichPanel,
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = row.iconText.take(2),
                                        color = RichText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.title,
                                color = RichText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (row.subtitle.isNotBlank()) {
                                Text(
                                    text = row.subtitle,
                                    color = RichMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (row.value.isNotBlank()) {
                            Text(
                                text = row.value,
                                color = RichMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (snapshot.actions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                snapshot.actions.take(3).forEachIndexed { index, action ->
                    val enabled = item.extensionAvailable || !action.requiresExtension
                    FullActionButton(
                        label = action.label,
                        icon = actionIcon(action.id),
                        primary = action.style == ExtensionActionStyle.Primary || index == 0,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(item.id, action.id) },
                    )
                }
            }
        }

        if (!item.extensionAvailable) {
            Text(
                text = "Extension unavailable",
                color = RichMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            )
        }
    }
}

fun richPreviewMessagesPageOne(): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.TextMessage(
        id = "u1",
        text = "Find my Azure notes and show me the file",
        time = "19:22",
        mine = true,
        read = true,
    ),
    FileResultMessage(
        id = "file",
        name = "Azure Notes.pdf",
        detail = "PDF • 2.4 MB • 12 pages",
        time = "19:22",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "u2",
        text = "Get Solo Leveling episode 8",
        time = "19:23",
        mine = true,
        read = true,
    ),
    AnimeResultMessage(
        id = "anime",
        title = "Solo Leveling",
        episode = "12 Episodes",
        quality = "1080p",
        size = "Sub",
        time = "19:23",
        description = "In a world of hunters and monsters, Sung Jin-Woo gains a mysterious system that lets him level up.",
        primaryActionLabel = "Play Episode 1",
    ),
    MangaResultMessage(
        id = "manga-preview",
        title = "Solo Leveling",
        chapter = "Chapter 202",
        source = "Manga",
        description = "Sung Jin-Woo continues beyond the gates as the world changes around him.",
        time = "19:24",
        primaryActionLabel = "Read Chapter 202",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "u3",
        text = "Find Saturn pictures on Pinterest",
        time = "19:24",
        mine = true,
        read = true,
    ),
    ImageSearchResultMessage(
        id = "images",
        source = "Pinterest",
        resultCount = 12,
        time = "19:24",
    ),
)

fun richPreviewMessagesPageTwo(): List<WhatsAppVisualMessage> = listOf(
    ButtonResultMessage(
        id = "buttons",
        title = "What are you in the mood for?",
        body = "I can suggest anime, movies, or TV shows based on your taste.",
        actions = listOf(
            MessageAction("surprise", "Surprise me"),
            MessageAction("anime", "Anime"),
            MessageAction("movies", "Movies"),
        ),
        time = "19:25",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "u4",
        text = "Download episode 8",
        time = "19:25",
        mine = true,
        read = true,
        reply = ReplyPreview(
            messageId = "anime",
            author = "Assistant",
            text = "Solo Leveling • Season 2 • Episode 8 • 1080p",
            kind = ReplyKind.Rich,
        ),
    ),
    DownloadResultMessage(
        id = "download",
        title = "Solo Leveling S2E8",
        detail = "184 MB of 428 MB • 5.7 MB/s",
        progress = 0.43f,
        time = "19:25",
    ),
    ToolResultMessage(
        id = "tool",
        toolName = "Productivity • Tool",
        title = "Notion Assistant",
        subtitle = "Search, summarize, and write directly in your Notion workspace. Turn ideas into action faster.",
        time = "19:25",
        iconText = "N",
        actions = listOf(
            MessageAction("open", "Open"),
            MessageAction("setup", "Setup"),
            MessageAction("learn", "Learn More"),
        ),
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "u5",
        text = "Move my Azure PDF into Documents",
        time = "19:26",
        mine = true,
        read = true,
    ),
    ToolResultMessage(
        id = "files",
        toolName = "Files",
        title = "Moved Azure Notes.pdf",
        subtitle = "Downloads → Documents/Azure Notes.pdf",
        time = "19:26",
    ),
)


fun approvedRichPreviewMessages(): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.TextMessage(
        id = "approved-watch",
        text = "Can you recommend something to watch tonight?",
        time = "14:20",
        mine = true,
        read = true,
    ),
    ButtonResultMessage(
        id = "approved-options",
        title = "What are you in the mood for?",
        body = "I can suggest anime, movies, or TV shows based on your taste.",
        actions = listOf(
            MessageAction("surprise", "Surprise me"),
            MessageAction("anime", "Anime"),
            MessageAction("movies", "Movies"),
        ),
        time = "14:20",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "approved-anime-prompt",
        text = "Show me a popular anime.",
        time = "14:21",
        mine = true,
        read = true,
    ),
    AnimeResultMessage(
        id = "approved-anime",
        title = "Solo Leveling",
        episode = "12 Episodes",
        quality = "1080p",
        size = "Sub",
        status = "Ongoing",
        description = "In a world of hunters and monsters, Sung Jin-Woo gains a mysterious system that lets him level up.",
        primaryActionLabel = "Play Episode 1",
        time = "14:21",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "approved-manga-prompt",
        text = "Find a good manga too.",
        time = "14:22",
        mine = true,
        read = true,
    ),
    MangaResultMessage(
        id = "approved-manga",
        title = "Chainsaw Man",
        chapter = "Chapter 173",
        source = "MangaPlus",
        status = "Ongoing",
        description = "Denji, a boy with a devil’s heart, hunts devils for a better life. A dark and thrilling story of chaos, power, and dreams.",
        primaryActionLabel = "Read Chapter 1",
        time = "14:22",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "approved-tool-prompt",
        text = "Anything useful for productivity?",
        time = "14:24",
        mine = true,
        read = true,
    ),
    ExtensionResultMessage(
        id = "approved-notion",
        snapshot = ExtensionMessageSnapshot(
            extensionId = "notion",
            messageType = "notion.page",
            template = ExtensionCardTemplate.Content,
            extensionName = "Notion Assistant",
            title = "Notion Assistant",
            subtitle = "Search, summarize, and write directly in your Notion workspace. Turn ideas into action faster.",
            iconText = "N",
            badge = "Productivity • Tool",
            actions = listOf(
                ExtensionCardAction("open", "Open", ExtensionActionStyle.Primary),
                ExtensionCardAction("setup", "Setup"),
                ExtensionCardAction("learn", "Learn More"),
            ),
        ),
        time = "14:24",
    ),
)

