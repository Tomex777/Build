package com.example.whatsapp.presentation.chatscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.R

private val HeaderBlack = Color(0xFF0A0A0A)
private val IncomingBubble = Color(0xFF242625)
private val IncomingReply = Color(0xFF3C3C3A)
private val OutgoingBubble = Color(0xFF7E112E)
private val AccentPink = Color(0xFFCF4A69)
private val ComposerBackground = Color(0xFF1F272A)
private val DatePill = Color(0xFF13181C)
private val PrimaryText = Color(0xFFECEDEE)
private val SecondaryText = Color(0xFF9EA7AB)
private val TickBlue = Color(0xFF53BDEB)

sealed interface WhatsAppVisualMessage {
    val id: String

    data class TextMessage(
        override val id: String,
        val text: String,
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
        val reply: ReplyPreview? = null,
    ) : WhatsAppVisualMessage

    data class PhotoMessage(
        override val id: String,
        val caption: String,
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
        val compact: Boolean = false,
    ) : WhatsAppVisualMessage

    data class VoiceMessage(
        override val id: String,
        val duration: String,
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
    ) : WhatsAppVisualMessage

    data class DateSeparator(
        override val id: String,
        val label: String,
    ) : WhatsAppVisualMessage
}

data class ReplyPreview(
    val author: String,
    val text: String,
)

@Composable
fun CurrentWhatsAppConversation(
    contactName: String,
    subtitle: String,
    messages: List<WhatsAppVisualMessage>,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSendClick: () -> Unit,
    onCallClick: () -> Unit = {},
    onAttachmentClick: () -> Unit = {},
    onAttachmentAction: (String) -> Unit = {},
    onCameraClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onEmojiClick: () -> Unit = {},
    autoScrollToLatest: Boolean = true,
    attachmentsInitiallyOpen: Boolean = false,
) {
    val state = rememberLazyListState()
    var showAttachments by remember { mutableStateOf(attachmentsInitiallyOpen) }

    LaunchedEffect(messages.size, autoScrollToLatest) {
        if (autoScrollToLatest && messages.isNotEmpty()) {
            state.scrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HeaderBlack),
    ) {
        WhatsAppWallpaper()

        Column(modifier = Modifier.fillMaxSize()) {
            CurrentChatHeader(
                contactName = contactName,
                subtitle = subtitle,
                onBackClick = onBackClick,
                onCallClick = onCallClick,
            )

            LazyColumn(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages, key = { it.id }) { item ->
                    when (item) {
                        is WhatsAppVisualMessage.TextMessage -> CurrentTextBubble(item)
                        is WhatsAppVisualMessage.PhotoMessage -> CurrentPhotoBubble(item)
                        is WhatsAppVisualMessage.VoiceMessage -> CurrentVoiceBubble(item)
                        is WhatsAppVisualMessage.DateSeparator -> CurrentDateSeparator(item.label)
                        is RichResultMessage -> RichResultBubble(item)
                    }
                }
            }

            CurrentComposer(
                text = messageText,
                onTextChange = onMessageTextChange,
                onSendClick = onSendClick,
                onAttachmentClick = {
                    showAttachments = !showAttachments
                    onAttachmentClick()
                },
                onCameraClick = onCameraClick,
                onMicClick = onMicClick,
                onEmojiClick = onEmojiClick,
                applyNavigationPadding = !showAttachments,
            )

            if (showAttachments) {
                AttachmentTray(
                    onAction = { action ->
                        showAttachments = false
                        onAttachmentAction(action)
                    },
                )
            }
        }
    }
}

@Composable
private fun WhatsAppWallpaper() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF6A0011),
                    Color(0xFF8F0018),
                    Color(0xFF4F000E),
                )
            )
        )

        val w = size.width
        val h = size.height
        drawOval(
            color = Color.Black.copy(alpha = 0.34f),
            topLeft = Offset(w * 0.42f, h * 0.18f),
            size = Size(w * 0.88f, h * 0.74f),
        )
        drawOval(
            color = Color(0xFF121719).copy(alpha = 0.62f),
            topLeft = Offset(-w * 0.18f, h * 0.53f),
            size = Size(w * 0.92f, h * 0.54f),
        )
        drawRect(
            color = Color(0xFF150006).copy(alpha = 0.45f),
            topLeft = Offset(w * 0.12f, h * 0.46f),
            size = Size(w * 0.72f, h * 0.07f),
        )
    }
}

@Composable
private fun CurrentChatHeader(
    contactName: String,
    subtitle: String,
    onBackClick: () -> Unit,
    onCallClick: () -> Unit,
) {
    Surface(
        color = HeaderBlack,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp)
                .height(68.dp)
                .padding(start = 5.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryText,
                    modifier = Modifier.size(24.dp),
                )
            }

            Image(
                painter = painterResource(R.drawable.ic_night),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = contactName,
                    color = PrimaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = SecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            var showCallMenu by remember { mutableStateOf(false) }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showCallMenu = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call options",
                        tint = PrimaryText,
                        modifier = Modifier.size(23.dp),
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = PrimaryText,
                        modifier = Modifier.size(22.dp),
                    )
                }

                DropdownMenu(
                    expanded = showCallMenu,
                    onDismissRequest = { showCallMenu = false },
                    containerColor = Color(0xFF151B1E),
                ) {
                    DropdownMenuItem(
                        text = { Text("Voice call", color = PrimaryText) },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, null, tint = SecondaryText)
                        },
                        onClick = {
                            showCallMenu = false
                            onCallClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Video call", color = PrimaryText) },
                        leadingIcon = {
                            Icon(Icons.Default.VideoCall, null, tint = SecondaryText)
                        },
                        onClick = { showCallMenu = false },
                    )
                }
            }

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = PrimaryText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun CurrentTextBubble(item: WhatsAppVisualMessage.TextMessage) {
    val alignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (item.mine) OutgoingBubble else IncomingBubble
    val shape = if (item.mine) {
        RoundedCornerShape(
            topStart = 13.dp,
            topEnd = 3.dp,
            bottomStart = 13.dp,
            bottomEnd = 13.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 3.dp,
            topEnd = 13.dp,
            bottomStart = 13.dp,
            bottomEnd = 13.dp,
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(
                    start = 10.dp,
                    top = if (item.reply == null) 7.dp else 6.dp,
                    end = 8.dp,
                    bottom = 5.dp,
                ),
        ) {
            item.reply?.let { CurrentReplyBlock(it) }

            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = item.text,
                    color = PrimaryText,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )

                Spacer(modifier = Modifier.width(10.dp))

                MessageMeta(
                    time = item.time,
                    mine = item.mine,
                    read = item.read,
                )
            }
        }
    }
}

@Composable
private fun CurrentReplyBlock(reply: ReplyPreview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF343638)),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(AccentPink),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 6.dp),
        ) {
            Text(
                text = reply.author,
                color = Color(0xFFE05B7C),
                fontSize = 11.sp,
                lineHeight = 13.sp,
            )
            Text(
                text = reply.text,
                color = Color(0xFFB8BEC1),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Spacer(modifier = Modifier.height(5.dp))
}

@Composable
private fun CurrentPhotoBubble(item: WhatsAppVisualMessage.PhotoMessage) {
    if (item.compact) {
        CompactPhotoBubble(item)
        return
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(
                    if (item.mine) {
                        RoundedCornerShape(14.dp, 3.dp, 14.dp, 14.dp)
                    } else {
                        RoundedCornerShape(3.dp, 14.dp, 14.dp, 14.dp)
                    }
                )
                .background(if (item.mine) OutgoingBubble else IncomingBubble)
                .padding(5.dp),
        ) {
            DemoMediaArtwork(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            if (item.caption.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 3.dp, top = 7.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = item.caption,
                        color = PrimaryText,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    MessageMeta(item.time, item.mine, item.read)
                }
            }
        }
    }
}

@Composable
private fun CompactPhotoBubble(item: WhatsAppVisualMessage.PhotoMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.bilal),
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(2.dp)),
                contentScale = ContentScale.Crop,
            )
            Surface(
                color = DatePill.copy(alpha = 0.92f),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = item.time,
                    color = SecondaryText,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DemoMediaArtwork(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFF4F1EB)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFFDF67C8),
                radius = size.minDimension * 0.29f,
                center = Offset(size.width * 0.48f, size.height * 0.48f),
            )
            drawCircle(
                color = Color(0xFFFFFF00),
                radius = size.minDimension * 0.20f,
                center = Offset(size.width * 0.28f, size.height * 0.55f),
            )
            drawCircle(
                color = Color(0xFF2196F3),
                radius = size.minDimension * 0.09f,
                center = Offset(size.width * 0.82f, size.height * 0.38f),
            )
            drawCircle(
                color = Color(0xFFFFA51F),
                radius = size.minDimension * 0.07f,
                center = Offset(size.width * 0.18f, size.height * 0.78f),
            )
        }
        Text(
            text = "PAGE  NOT  FOUND",
            color = Color(0xFF181818),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun CurrentVoiceBubble(item: WhatsAppVisualMessage.VoiceMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 300.dp)
                .clip(
                    if (item.mine) {
                        RoundedCornerShape(14.dp, 3.dp, 14.dp, 14.dp)
                    } else {
                        RoundedCornerShape(3.dp, 14.dp, 14.dp, 14.dp)
                    }
                )
                .background(if (item.mine) OutgoingBubble else IncomingBubble)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.bilal),
                        contentDescription = null,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (item.mine) OutgoingBubble else IncomingBubble),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = PrimaryText,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(7.dp))

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play voice note",
                    tint = PrimaryText,
                    modifier = Modifier.size(34.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(PrimaryText),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        VoiceWaveform(modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = item.duration,
                            color = SecondaryText,
                            fontSize = 10.sp,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        MessageMeta(item.time, item.mine, item.read)
                    }
                }
            }

            Text(
                text = "Setting up transcripts, Stop",
                color = Color(0xFFCE8B9D),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 51.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun VoiceWaveform(modifier: Modifier = Modifier) {
    val heights = listOf(
        7, 12, 9, 18, 10, 7, 16, 23, 13, 8, 11, 20, 26, 14, 9, 18, 24, 12,
        8, 15, 21, 10, 7, 18, 13, 23, 16, 8, 11, 20, 9, 14, 24, 12, 7, 16,
    )

    Row(
        modifier = modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFCE8B9D)),
            )
        }
    }
}

@Composable
private fun CurrentDateSeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = DatePill,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = label,
                color = SecondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun MessageMeta(
    time: String,
    mine: Boolean,
    read: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            color = SecondaryText,
            fontSize = 10.sp,
        )

        if (mine) {
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = if (read) "Read" else "Delivered",
                tint = if (read) TickBlue else SecondaryText,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun CurrentComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    onEmojiClick: () -> Unit,
    applyNavigationPadding: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .then(if (applyNavigationPadding) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            color = ComposerBackground,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 3.dp, end = 2.dp),
            ) {
                IconButton(onClick = onEmojiClick) {
                    Icon(
                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = "Emoji",
                        tint = SecondaryText,
                        modifier = Modifier.size(28.dp),
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = "Message",
                            color = Color(0xFF8F999E),
                            fontSize = 16.sp,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        cursorColor = AccentPink,
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    ),
                )

                IconButton(onClick = onAttachmentClick) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        tint = SecondaryText,
                        modifier = Modifier.size(25.dp),
                    )
                }

                IconButton(onClick = onCameraClick) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Camera",
                        tint = SecondaryText,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(7.dp))

        Surface(
            color = AccentPink,
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .clickable {
                    if (text.isBlank()) onMicClick() else onSendClick()
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (text.isBlank()) Icons.Default.Mic else Icons.Default.Send,
                    contentDescription = if (text.isBlank()) "Voice message" else "Send",
                    tint = Color(0xFF10161A),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun AttachmentTray(
    onAction: (String) -> Unit,
) {
    val items = listOf(
        Triple("Gallery", Icons.Default.ImageIcon, Color(0xFF2196F3)),
        Triple("Camera", Icons.Default.PhotoCamera, Color(0xFFE91E63)),
        Triple("Location", Icons.Default.LocationOn, Color(0xFF20C997)),
        Triple("Contact", Icons.Default.Person, Color(0xFF039BE5)),
        Triple("Document", Icons.Default.Description, Color(0xFF7E57C2)),
        Triple("Poll", Icons.Default.Poll, Color(0xFFFFB300)),
        Triple("Event", Icons.Default.Event, Color(0xFFE91E63)),
        Triple("AI images", Icons.Default.AutoAwesome, Color(0xFF1976D2)),
    )

    Surface(
        color = Color(0xFF111719),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(30.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF788287)),
            )

            items.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAction(item.first) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                color = Color(0xFF141B1E),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.second,
                                        contentDescription = item.first,
                                        tint = item.third,
                                        modifier = Modifier.size(25.dp),
                                    )
                                }
                            }
                            Text(
                                text = item.first,
                                color = SecondaryText,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun whatsappPreviewMessages(): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.PhotoMessage(
        id = "photo",
        caption = "Something like this",
        time = "21:58",
        mine = true,
        read = true,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "reply",
        text = "Great great",
        time = "22:19",
        mine = false,
        reply = ReplyPreview(
            author = "You",
            text = "I learnt about balance, unity, contrasts, emphasis and repetition/patterns",
        ),
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "t2",
        text = "Find ways to implement them",
        time = "22:19",
        mine = false,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "t3",
        text = "You can design a flyer for the app if you are feeling like it",
        time = "22:19",
        mine = false,
    ),
    WhatsAppVisualMessage.DateSeparator("wed", "Wednesday"),
    WhatsAppVisualMessage.TextMessage(
        id = "morning-in",
        text = "Good morning boss",
        time = "09:44",
        mine = false,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "morning-out",
        text = "Good morning",
        time = "09:54",
        mine = true,
        read = true,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "church",
        text = "I'm in church",
        time = "09:54",
        mine = true,
        read = true,
    ),
    WhatsAppVisualMessage.DateSeparator("yesterday", "Yesterday"),
    WhatsAppVisualMessage.TextMessage(
        id = "since",
        text = "Since yesterday",
        time = "13:08",
        mine = false,
    ),
    WhatsAppVisualMessage.PhotoMessage(
        id = "compact-photo",
        caption = "",
        time = "13:09",
        mine = false,
        compact = true,
    ),
    WhatsAppVisualMessage.VoiceMessage(
        id = "voice",
        duration = "0:15",
        time = "18:28",
        mine = true,
        read = true,
    ),
)


@Composable
fun ChatInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    onEmojiClick: () -> Unit,
) {
    CurrentComposer(
        text = messageText,
        onTextChange = onMessageTextChange,
        onSendClick = onSendClick,
        onAttachmentClick = onAttachmentClick,
        onCameraClick = onCameraClick,
        onMicClick = onMicClick,
        onEmojiClick = onEmojiClick,
    )
}

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val emojis = listOf("😀", "😂", "🥹", "😍", "😭", "😎", "👍", "❤️", "🙏", "🔥")
    Surface(
        color = ComposerBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 25.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onEmojiSelected(emoji) }
                        .padding(4.dp),
                )
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}
