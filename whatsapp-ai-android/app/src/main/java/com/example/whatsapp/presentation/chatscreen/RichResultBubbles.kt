package com.example.whatsapp.presentation.chatscreen

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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface RichResultMessage : WhatsAppVisualMessage

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
) : RichResultMessage

private val RichBubble = Color(0xFF242625)
private val RichPanel = Color(0xFF303436)
private val RichText = Color(0xFFECEDEE)
private val RichMuted = Color(0xFF9EA7AB)
private val RichAccent = Color(0xFF25D366)
private val RichBlue = Color(0xFF53BDEB)

@Composable
fun RichResultBubble(item: RichResultMessage) {
    when (item) {
        is FileResultMessage -> FileResultBubble(item)
        is AnimeResultMessage -> AnimeResultBubble(item)
        is ImageSearchResultMessage -> ImageSearchBubble(item)
        is DownloadResultMessage -> DownloadResultBubble(item)
        is ToolResultMessage -> ToolResultBubble(item)
    }
}

@Composable
private fun BubbleFrame(
    time: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(RichBubble)
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
private fun FileResultBubble(item: FileResultMessage) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(RichPanel)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFBE3D4B)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = RichText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.detail,
                    color = RichMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Surface(
                color = Color(0xFF3A4144),
                shape = CircleShape,
                modifier = Modifier.size(38.dp).clickable {},
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Open file",
                        tint = RichText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Text(
            text = "Open inside app",
            color = RichAccent,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 1.dp),
        )
    }
}

@Composable
private fun AnimeResultBubble(item: AnimeResultMessage) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(RichPanel)
                .padding(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF243B55), Color(0xFF141E30))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "SL",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
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
                Text(
                    text = item.episode,
                    color = RichMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = item.quality + " • " + item.size,
                    color = RichMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RichActionButton(
                        label = "Play",
                        icon = Icons.Default.PlayArrow,
                    )
                    RichActionButton(
                        label = "Download",
                        icon = Icons.Default.Download,
                    )
                }
            }
        }
    }
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

        Text(
            text = ((item.progress * 100).toInt()).toString() + "%",
            color = RichMuted,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
        )
    }
}

@Composable
private fun ToolResultBubble(item: ToolResultMessage) {
    BubbleFrame(time = item.time) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2F493A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = RichAccent,
                    modifier = Modifier.size(21.dp),
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.toolName,
                    color = RichAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.title,
                    color = RichText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Text(
                    text = item.subtitle,
                    color = RichMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
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
        episode = "Season 2 • Episode 8",
        quality = "1080p",
        size = "428 MB",
        time = "19:23",
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
    WhatsAppVisualMessage.TextMessage(
        id = "u4",
        text = "Download episode 8",
        time = "19:25",
        mine = true,
        read = true,
        reply = ReplyPreview(
            author = "Assistant",
            text = "Solo Leveling • Season 2 • Episode 8 • 1080p",
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
        toolName = "Extension result",
        title = "Anime source resolved",
        subtitle = "AnimePahe returned 3 playable sources. 1080p selected automatically.",
        time = "19:25",
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
