package com.night.extensionchat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface ChatItem {
    val id: String

    data class TextMessage(
        override val id: String,
        val text: String,
        val mine: Boolean,
        val time: String,
    ) : ChatItem

    data class FileResult(
        override val id: String,
        val title: String,
        val subtitle: String,
        val pages: Int,
        val time: String,
    ) : ChatItem

    data class AnimeResult(
        override val id: String,
        val title: String,
        val episode: String,
        val quality: String,
        val size: String,
        val time: String,
    ) : ChatItem

    data class ImageGrid(
        override val id: String,
        val label: String,
        val time: String,
    ) : ChatItem
}

private val previewItems = listOf(
    ChatItem.TextMessage(
        id = "1",
        text = "Find that Azure pricing PDF I downloaded yesterday.",
        mine = true,
        time = "6:04 PM",
    ),
    ChatItem.FileResult(
        id = "2",
        title = "Azure pricing notes.pdf",
        subtitle = "Downloads · Yesterday · 2.4 MB",
        pages = 14,
        time = "6:04 PM",
    ),
    ChatItem.TextMessage(
        id = "3",
        text = "Now find Solo Leveling episode 8, 1080p.",
        mine = true,
        time = "6:05 PM",
    ),
    ChatItem.AnimeResult(
        id = "4",
        title = "Solo Leveling",
        episode = "Episode 8",
        quality = "1080p",
        size = "428 MB",
        time = "6:05 PM",
    ),
    ChatItem.TextMessage(
        id = "5",
        text = "Get me dark Saturn wallpapers from Pinterest.",
        mine = true,
        time = "6:07 PM",
    ),
    ChatItem.ImageGrid(
        id = "6",
        label = "12 Pinterest results",
        time = "6:07 PM",
    ),
)

@Composable
fun ExtensionChatApp() {
    var input by remember { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    end = 10.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(previewItems, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.TextMessage -> MessageBubble(item)
                        is ChatItem.FileResult -> FileResultBubble(item)
                        is ChatItem.AnimeResult -> AnimeResultBubble(item)
                        is ChatItem.ImageGrid -> ImageGridBubble(item)
                    }
                }
            }

            ChatComposer(
                text = input,
                onTextChange = { input = it },
                onVoiceClick = { voiceMode = true },
                onSendClick = { input = "" },
            )
        }

        AnimatedVisibility(
            visible = voiceMode,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            VoiceInputPanel(onClose = { voiceMode = false })
        }
    }
}

@Composable
private fun ChatHeader() {
    Surface(color = ChatTopBar, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = ChatText,
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ChatAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF062B25),
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = "Assistant",
                    color = ChatText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Hybrid · local + API",
                    color = ChatMuted,
                    fontSize = 12.sp,
                )
            }

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search conversation",
                    tint = ChatText,
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = ChatText,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(item: ChatItem.TextMessage) {
    val alignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (item.mine) ChatOutgoing else ChatIncoming
    val shape = if (item.mine) {
        RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp)
    } else {
        RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = item.text,
                    color = ChatText,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.time,
                    color = ChatMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun FileResultBubble(item: ChatItem.FileResult) {
    RichIncomingBubble(time = item.time) {
        Text(
            text = "Found this in your files",
            color = ChatText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            color = ChatCard,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ChatStroke, RoundedCornerShape(10.dp)),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .background(Color(0xFFE7E2D7)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFFB3261E),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "AZURE",
                                color = Color(0xFF263238),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Pricing notes",
                            color = Color(0xFF263238),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Compute · Storage · AI Services",
                            color = Color(0xFF607D8B),
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = item.pages.toString() + " pages",
                        color = Color(0xFF546E7A),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = ChatLink,
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = ChatText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.subtitle,
                            color = ChatMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(
                containerColor = ChatCardRaised,
                contentColor = ChatAccentStrong,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open in reader")
        }
    }
}

@Composable
private fun AnimeResultBubble(item: ChatItem.AnimeResult) {
    RichIncomingBubble(time = item.time) {
        Text(
            text = "Found a playable result",
            color = ChatText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            color = ChatCard,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ChatStroke, RoundedCornerShape(12.dp)),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 82.dp, height = 116.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF28343B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SL",
                            color = ChatAccentStrong,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "ANIME",
                            color = ChatMuted,
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = item.title,
                        color = ChatText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.episode,
                        color = ChatMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaChip(item.quality)
                        MetaChip(item.size)
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChatAccent,
                                contentColor = Color(0xFF041F1B),
                            ),
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Play", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChatCardRaised,
                                contentColor = ChatText,
                            ),
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Download", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGridBubble(item: ChatItem.ImageGrid) {
    RichIncomingBubble(time = item.time) {
        Text(
            text = item.label,
            color = ChatText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FauxImageTile("SATURN", Color(0xFF3D342C), Modifier.weight(1f))
                FauxImageTile("RINGS", Color(0xFF27323A), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FauxImageTile("SPACE", Color(0xFF1E2635), Modifier.weight(1f))
                FauxImageTile("+9", Color(0xFF332B3A), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = "Pinterest",
            color = ChatLink,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun FauxImageTile(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        color = ChatCardRaised,
        shape = RoundedCornerShape(7.dp),
    ) {
        Text(
            text = text,
            color = ChatMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RichIncomingBubble(
    time: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            color = ChatIncoming,
            shape = RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
                Text(
                    text = time,
                    color = ChatMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottom = if (imeBottom > 0.dp) 4.dp else navigationBottom.coerceAtLeast(4.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatBackground)
            .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = bottom),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            color = ChatComposerSurface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(start = 2.dp, end = 5.dp),
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = ChatMuted,
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = "Message",
                            color = ChatMuted,
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = ChatAccentStrong,
                        focusedTextColor = ChatText,
                        unfocusedTextColor = ChatText,
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                    ),
                    maxLines = 5,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Surface(
            color = ChatAccent,
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .clickable {
                    if (text.isBlank()) onVoiceClick() else onSendClick()
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (text.isBlank()) {
                        Icons.Default.KeyboardVoice
                    } else {
                        Icons.Default.Send
                    },
                    contentDescription = if (text.isBlank()) "Voice input" else "Send",
                    tint = Color(0xFF062B25),
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

@Composable
private fun VoiceInputPanel(onClose: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "voice")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(720),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voicePulse",
    )

    Surface(
        color = ChatTopBar,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening…",
                        color = ChatText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Speech becomes text before sending",
                        color = ChatMuted,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close voice input",
                        tint = ChatText,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(ChatAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardVoice,
                    contentDescription = null,
                    tint = Color(0xFF062B25),
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            VoiceBars()
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Whisper / cloud STT can plug in here",
                color = ChatMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VoiceBars() {
    val transition = rememberInfiniteTransition(label = "bars")
    val levels = listOf(
        transition.animateFloat(
            0.35f,
            1f,
            infiniteRepeatable(tween(420), RepeatMode.Reverse),
            label = "b1",
        ).value,
        transition.animateFloat(
            0.55f,
            0.85f,
            infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "b2",
        ).value,
        transition.animateFloat(
            0.25f,
            0.95f,
            infiniteRepeatable(tween(610), RepeatMode.Reverse),
            label = "b3",
        ).value,
        transition.animateFloat(
            0.50f,
            1f,
            infiniteRepeatable(tween(470), RepeatMode.Reverse),
            label = "b4",
        ).value,
        transition.animateFloat(
            0.30f,
            0.78f,
            infiniteRepeatable(tween(560), RepeatMode.Reverse),
            label = "b5",
        ).value,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(34.dp),
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((30 * level).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ChatAccentStrong),
            )
        }
    }
}
