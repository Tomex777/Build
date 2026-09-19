package com.example.whatsapp.presentation.chatscreen

import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.R
import java.io.File
import kotlinx.coroutines.launch

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
        val localPath: String? = null,
        val aspectRatio: Float = 1.25f,
        val reply: ReplyPreview? = null,
    ) : WhatsAppVisualMessage

    data class VideoMessage(
        override val id: String,
        val caption: String,
        val duration: String,
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
        val localPath: String? = null,
        val thumbnailPath: String? = null,
        val aspectRatio: Float = 16f / 9f,
        val reply: ReplyPreview? = null,
    ) : WhatsAppVisualMessage

    data class FileMessage(
        override val id: String,
        val name: String,
        val detail: String,
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
        val reply: ReplyPreview? = null,
    ) : WhatsAppVisualMessage

    data class AudioMessage(
        override val id: String,
        val title: String,
        val artist: String,
        val duration: String,
        val detail: String,
        val caption: String = "",
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
        val localPath: String? = null,
        val artworkPath: String? = null,
        val reply: ReplyPreview? = null,
    ) : WhatsAppVisualMessage

    data class LinkPreviewMessage(
        override val id: String,
        val body: String,
        val url: String,
        val title: String,
        val description: String,
        val site: String,
        val imageUrl: String? = null,
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
        val reply: ReplyPreview? = null,
    ) : WhatsAppVisualMessage

    data class VoiceMessage(
        override val id: String,
        val duration: String,
        val time: String,
        val mine: Boolean,
        val read: Boolean = false,
        val localPath: String? = null,
        val transcript: String? = null,
        val reply: ReplyPreview? = null,
    ) : WhatsAppVisualMessage

    data class DateSeparator(
        override val id: String,
        val label: String,
    ) : WhatsAppVisualMessage
}

enum class ReplyKind {
    Text,
    Image,
    Video,
    Voice,
    File,
    Audio,
    Rich,
}

data class ReplyPreview(
    val messageId: String,
    val author: String,
    val text: String,
    val kind: ReplyKind = ReplyKind.Text,
    val thumbnailPath: String? = null,
    val meta: String? = null,
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
    onMenuAction: (String) -> Unit = {},
    onMessageButtonClick: (messageId: String, actionId: String) -> Unit = { _, _ -> },
    onAttachmentClick: () -> Unit = {},
    onAttachmentAction: (String) -> Unit = {},
    onCameraClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    isRecording: Boolean = false,
    onVoiceClick: (String) -> Unit = {},
    onAudioClick: (String) -> Unit = {},
    onTranscribeVoice: (String, String) -> Unit = { _, _ -> },
    onSpeakText: (String) -> Unit = {},
    onReplyRequest: (String) -> Unit = {},
    replyPreview: ReplyPreview? = null,
    onCancelReply: () -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onEmojiClick: () -> Unit = {},
    autoScrollToLatest: Boolean = true,
    attachmentsInitiallyOpen: Boolean = false,
    emojiInitiallyOpen: Boolean = false,
    menuInitiallyOpen: Boolean = false,
    appearance: NightChatAppearance = NightChatAppearance(),
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var showAttachments by remember { mutableStateOf(attachmentsInitiallyOpen) }
    var showEmojiPicker by remember { mutableStateOf(emojiInitiallyOpen) }

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
        WhatsAppWallpaper(appearance)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            CurrentChatHeader(
                contactName = contactName,
                subtitle = subtitle,
                onBackClick = onBackClick,
                onCallClick = onCallClick,
                onMenuAction = onMenuAction,
                menuInitiallyOpen = menuInitiallyOpen,
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
                    if (item is WhatsAppVisualMessage.DateSeparator) {
                        CurrentDateSeparator(item.label)
                    } else {
                        SwipeToReplyContainer(
                            messageId = item.id,
                            onReplyRequest = onReplyRequest,
                        ) {
                            when (item) {
                                is WhatsAppVisualMessage.TextMessage -> CurrentTextBubble(
                                    item,
                                    appearance,
                                    onSpeakText,
                                    onReplyPreviewClick = { targetId ->
                                        val index = messages.indexOfFirst { it.id == targetId }
                                        if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                                    },
                                )
                                is WhatsAppVisualMessage.PhotoMessage -> CurrentPhotoBubble(
                                    item,
                                    appearance,
                                    onImageClick,
                                    onReplyPreviewClick = { targetId ->
                                        val index = messages.indexOfFirst { it.id == targetId }
                                        if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                                    },
                                )
                                is WhatsAppVisualMessage.VideoMessage -> CurrentVideoBubble(
                                    item,
                                    appearance,
                                    onVideoClick,
                                    onReplyPreviewClick = { targetId ->
                                        val index = messages.indexOfFirst { it.id == targetId }
                                        if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                                    },
                                )
                                is WhatsAppVisualMessage.FileMessage -> CurrentFileBubble(
                                    item,
                                    appearance,
                                    onReplyPreviewClick = { targetId ->
                                        val index = messages.indexOfFirst { it.id == targetId }
                                        if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                                    },
                                )
                                is WhatsAppVisualMessage.AudioMessage -> CurrentAudioBubble(
                                    item,
                                    appearance,
                                    onAudioClick,
                                    onReplyPreviewClick = { targetId ->
                                        val index = messages.indexOfFirst { it.id == targetId }
                                        if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                                    },
                                )
                                is WhatsAppVisualMessage.LinkPreviewMessage -> CurrentLinkPreviewBubble(
                                    item = item,
                                    appearance = appearance,
                                    onLinkClick = onLinkClick,
                                    onReplyPreviewClick = { targetId ->
                                        val index = messages.indexOfFirst { it.id == targetId }
                                        if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                                    },
                                )
                                is WhatsAppVisualMessage.VoiceMessage -> CurrentVoiceBubble(
                                    item,
                                    appearance,
                                    onVoiceClick,
                                    onTranscribeVoice,
                                    onReplyPreviewClick = { targetId ->
                                        val index = messages.indexOfFirst { it.id == targetId }
                                        if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                                    },
                                )
                                is RichResultMessage -> RichResultBubble(item, onMessageButtonClick)
                                is WhatsAppVisualMessage.DateSeparator -> Unit
                            }
                        }
                    }
                }
            }

            CurrentComposer(
                text = messageText,
                onTextChange = onMessageTextChange,
                onSendClick = onSendClick,
                onAttachmentClick = {
                    keyboardController?.hide()
                    showEmojiPicker = false
                    showAttachments = !showAttachments
                    onAttachmentClick()
                },
                onCameraClick = onCameraClick,
                onMicClick = onMicClick,
                isRecording = isRecording,
                onEmojiClick = {
                    keyboardController?.hide()
                    showAttachments = false
                    showEmojiPicker = !showEmojiPicker
                    onEmojiClick()
                },
                applyNavigationPadding = !showAttachments && !showEmojiPicker,
                appearance = appearance,
                replyPreview = replyPreview,
                onCancelReply = onCancelReply,
            )

            if (showAttachments) {
                AttachmentTray(
                    onAction = { action ->
                        showAttachments = false
                        onAttachmentAction(action)
                    },
                )
            }

            if (showEmojiPicker) {
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        onMessageTextChange(messageText + emoji)
                    },
                    onDismiss = { showEmojiPicker = false },
                )
            }
        }
    }
}

@Composable
private fun WhatsAppWallpaper(appearance: NightChatAppearance) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    appearance.wallpaperTopColor,
                    appearance.wallpaperMiddleColor,
                    appearance.wallpaperBottomColor,
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
    onMenuAction: (String) -> Unit,
    menuInitiallyOpen: Boolean = false,
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

            IconButton(onClick = onCallClick) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Voice call",
                    tint = PrimaryText,
                    modifier = Modifier.size(23.dp),
                )
            }

            var showMoreMenu by remember { mutableStateOf(menuInitiallyOpen) }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = PrimaryText,
                        modifier = Modifier.size(24.dp),
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    containerColor = Color(0xFF151B1E),
                ) {
                    listOf(
                        "Search chat",
                        "Memory & summary",
                        "Files in chat",
                        "Rename chat",
                        "Export chat",
                        "Clear chat",
                        "Delete chat",
                        "Choose AI",
                    ).forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = action,
                                    color = if (action == "Delete chat") Color(0xFFFF5C72) else PrimaryText,
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onMenuAction(action)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CurrentTextBubble(
    item: WhatsAppVisualMessage.TextMessage,
    appearance: NightChatAppearance,
    onSpeakText: (String) -> Unit,
    onReplyPreviewClick: (String) -> Unit,
) {
    val alignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (item.mine) appearance.userBubbleColor else appearance.aiBubbleColor
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
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (!item.mine && item.text.isNotBlank()) {
                            onSpeakText(item.text)
                        }
                    },
                )
                .padding(
                    start = 10.dp,
                    top = if (item.reply == null) 7.dp else 6.dp,
                    end = 8.dp,
                    bottom = 5.dp,
                ),
        ) {
            item.reply?.let {
                CurrentReplyBlock(
                    reply = it,
                    appearance = appearance,
                    onClick = { onReplyPreviewClick(it.messageId) },
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = item.text,
                    color = PrimaryText,
                    fontSize = (14f * appearance.messageFontScale).sp,
                    lineHeight = (19f * appearance.messageFontScale).sp,
                    fontFamily = appearance.fontFamily,
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
private fun CurrentReplyBlock(
    reply: ReplyPreview,
    appearance: NightChatAppearance,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF343638))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(appearance.accentColor),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 9.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reply.kind !in setOf(ReplyKind.Text, ReplyKind.Image, ReplyKind.Video)) {
                ReplyTypePreview(reply)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reply.author,
                    color = appearance.accentColor,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = reply.text,
                    color = Color(0xFFC4C9CB),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                reply.meta?.takeIf { it.isNotBlank() }?.let { meta ->
                    Text(
                        text = meta,
                        color = Color(0xFF899397),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }

            if (reply.kind == ReplyKind.Image || reply.kind == ReplyKind.Video) {
                Spacer(modifier = Modifier.width(8.dp))
                ReplyTypePreview(reply)
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun ReplyTypePreview(reply: ReplyPreview) {
    when (reply.kind) {
        ReplyKind.Image,
        ReplyKind.Video -> {
            val file = reply.thumbnailPath?.let(::File)
            if (!reply.thumbnailPath.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = if (file != null && file.exists()) file else reply.thumbnailPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (reply.kind == ReplyKind.Video) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                ReplyIconPreview(
                    icon = if (reply.kind == ReplyKind.Video) Icons.Default.PlayArrow else Icons.Default.ImageIcon,
                )
            }
        }

        ReplyKind.Voice -> ReplyIconPreview(Icons.Default.Mic)
        ReplyKind.File -> ReplyIconPreview(Icons.Default.Description)
        ReplyKind.Audio -> ReplyIconPreview(Icons.Default.PlayArrow)
        ReplyKind.Rich -> ReplyIconPreview(Icons.Default.AutoAwesome)
        ReplyKind.Text -> Unit
    }
}

@Composable
private fun ReplyIconPreview(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        color = Color(0xFF45494B),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryText,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun CurrentPhotoBubble(
    item: WhatsAppVisualMessage.PhotoMessage,
    appearance: NightChatAppearance,
    onImageClick: (String) -> Unit,
    onReplyPreviewClick: (String) -> Unit,
) {
    if (item.compact) {
        CompactPhotoBubble(item)
        return
    }

    val bubbleColor = if (item.mine) appearance.userBubbleColor else appearance.aiBubbleColor
    val shape = if (item.mine) {
        RoundedCornerShape(18.dp, 5.dp, 18.dp, 18.dp)
    } else {
        RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(min = 280.dp, max = 390.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(7.dp),
        ) {
            item.reply?.let {
                CurrentReplyBlock(
                    reply = it,
                    appearance = appearance,
                    onClick = { onReplyPreviewClick(it.messageId) },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.aspectRatio.coerceIn(0.70f, 1.85f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF15191B))
                    .clickable {
                        item.localPath?.let(onImageClick)
                    },
            ) {
                if (!item.localPath.isNullOrBlank()) {
                    val file = File(item.localPath)
                    AsyncImage(
                        model = if (file.exists()) file else item.localPath,
                        contentDescription = item.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DemoMediaArtwork(modifier = Modifier.fillMaxSize())
                }

                if (item.caption.isBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(7.dp),
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                            MessageMeta(item.time, item.mine, item.read)
                        }
                    }
                }
            }

            if (item.caption.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 2.dp, top = 8.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = item.caption,
                        color = PrimaryText,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MessageMeta(item.time, item.mine, item.read)
                }
            }
        }
    }
}

@Composable
private fun CurrentVideoBubble(
    item: WhatsAppVisualMessage.VideoMessage,
    appearance: NightChatAppearance,
    onVideoClick: (String) -> Unit,
    onReplyPreviewClick: (String) -> Unit,
) {
    val bubbleColor = if (item.mine) appearance.userBubbleColor else appearance.aiBubbleColor
    val shape = if (item.mine) {
        RoundedCornerShape(18.dp, 5.dp, 18.dp, 18.dp)
    } else {
        RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(min = 280.dp, max = 390.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(7.dp),
        ) {
            item.reply?.let {
                CurrentReplyBlock(
                    reply = it,
                    appearance = appearance,
                    onClick = { onReplyPreviewClick(it.messageId) },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.aspectRatio.coerceIn(0.75f, 1.85f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121617))
                    .clickable {
                        item.localPath?.let(onVideoClick)
                    },
                contentAlignment = Alignment.Center,
            ) {
                val thumb = item.thumbnailPath?.let(::File)
                if (!item.thumbnailPath.isNullOrBlank()) {
                    AsyncImage(
                        model = if (thumb != null && thumb.exists()) thumb else item.thumbnailPath,
                        contentDescription = item.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DemoMediaArtwork(modifier = Modifier.fillMaxSize())
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    shape = CircleShape,
                    modifier = Modifier.size(58.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = item.duration,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }

                if (item.caption.isBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                            MessageMeta(item.time, item.mine, item.read)
                        }
                    }
                }
            }

            if (item.caption.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 2.dp, top = 8.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = item.caption,
                        color = PrimaryText,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MessageMeta(item.time, item.mine, item.read)
                }
            }
        }
    }
}

@Composable
private fun CurrentFileBubble(
    item: WhatsAppVisualMessage.FileMessage,
    appearance: NightChatAppearance,
    onReplyPreviewClick: (String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 250.dp, max = 340.dp)
                .clip(
                    if (item.mine) {
                        RoundedCornerShape(14.dp, 3.dp, 14.dp, 14.dp)
                    } else {
                        RoundedCornerShape(3.dp, 14.dp, 14.dp, 14.dp)
                    }
                )
                .background(if (item.mine) appearance.userBubbleColor else appearance.aiBubbleColor)
                .padding(8.dp),
        ) {
            item.reply?.let {
                CurrentReplyBlock(
                    reply = it,
                    appearance = appearance,
                    onClick = { onReplyPreviewClick(it.messageId) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF303436))
                    .padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Surface(
                color = Color(0xFF343A3D),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = PrimaryText,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = PrimaryText,
                    fontSize = (13f * appearance.messageFontScale).sp,
                    fontFamily = appearance.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.detail,
                    color = SecondaryText,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp, end = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                MessageMeta(item.time, item.mine, item.read)
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
private fun CurrentLinkPreviewBubble(
    item: WhatsAppVisualMessage.LinkPreviewMessage,
    appearance: NightChatAppearance,
    onLinkClick: (String) -> Unit,
    onReplyPreviewClick: (String) -> Unit,
) {
    val bubbleColor = if (item.mine) appearance.userBubbleColor else appearance.aiBubbleColor

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 355.dp)
                .clip(
                    if (item.mine) {
                        RoundedCornerShape(17.dp, 5.dp, 17.dp, 17.dp)
                    } else {
                        RoundedCornerShape(5.dp, 17.dp, 17.dp, 17.dp)
                    }
                )
                .background(bubbleColor)
                .padding(8.dp),
        ) {
            item.reply?.let {
                CurrentReplyBlock(
                    reply = it,
                    appearance = appearance,
                    onClick = { onReplyPreviewClick(it.messageId) },
                )
            }

            if (item.body.isNotBlank()) {
                Text(
                    text = item.body,
                    color = PrimaryText,
                    fontSize = (14f * appearance.messageFontScale).sp,
                    lineHeight = (18f * appearance.messageFontScale).sp,
                    fontFamily = appearance.fontFamily,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 7.dp),
                )
            }

            Surface(
                color = Color(0xFF303436),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLinkClick(item.url) },
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 92.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(116.dp)
                            .height(92.dp)
                            .background(Color(0xFF23292C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!item.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = appearance.accentColor,
                                modifier = Modifier.size(30.dp),
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
                            color = PrimaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (item.description.isNotBlank()) {
                            Text(
                                text = item.description,
                                color = SecondaryText,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }

                        Text(
                            text = item.site,
                            color = SecondaryText,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp, end = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                MessageMeta(item.time, item.mine, item.read)
            }
        }
    }
}

@Composable
private fun CurrentAudioBubble(
    item: WhatsAppVisualMessage.AudioMessage,
    appearance: NightChatAppearance,
    onAudioClick: (String) -> Unit,
    onReplyPreviewClick: (String) -> Unit,
) {
    val bubbleColor = if (item.mine) appearance.userBubbleColor else appearance.aiBubbleColor

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 290.dp, max = 350.dp)
                .clip(
                    if (item.mine) {
                        RoundedCornerShape(17.dp, 5.dp, 17.dp, 17.dp)
                    } else {
                        RoundedCornerShape(5.dp, 17.dp, 17.dp, 17.dp)
                    }
                )
                .background(bubbleColor)
                .padding(8.dp),
        ) {
            item.reply?.let {
                CurrentReplyBlock(
                    reply = it,
                    appearance = appearance,
                    onClick = { onReplyPreviewClick(it.messageId) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF303436))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF45494B)),
                    contentAlignment = Alignment.Center,
                ) {
                    val artwork = item.artworkPath?.let(::File)
                    if (!item.artworkPath.isNullOrBlank()) {
                        AsyncImage(
                            model = if (artwork != null && artwork.exists()) artwork else item.artworkPath,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = appearance.accentColor,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = PrimaryText,
                        fontSize = (15f * appearance.messageFontScale).sp,
                        fontFamily = appearance.fontFamily,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.artist.ifBlank { "Audio" },
                        color = SecondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(
                        text = item.detail.ifBlank { item.duration },
                        color = SecondaryText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Surface(
                    color = Color(0xFF505557),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable {
                            item.localPath?.let(onAudioClick)
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play audio",
                            tint = PrimaryText,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
            }

            if (item.caption.isNotBlank()) {
                Text(
                    text = item.caption,
                    color = PrimaryText,
                    fontSize = (13f * appearance.messageFontScale).sp,
                    lineHeight = (18f * appearance.messageFontScale).sp,
                    fontFamily = appearance.fontFamily,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, end = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                MessageMeta(item.time, item.mine, item.read)
            }
        }
    }
}

@Composable
private fun CurrentVoiceBubble(
    item: WhatsAppVisualMessage.VoiceMessage,
    appearance: NightChatAppearance,
    onVoiceClick: (String) -> Unit,
    onTranscribeVoice: (String, String) -> Unit,
    onReplyPreviewClick: (String) -> Unit,
) {
    val bubbleColor = if (item.mine) appearance.userBubbleColor else appearance.aiBubbleColor
    val playColor = if (item.mine) Color(0xFFD83D67) else Color(0xFF4A4E50)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (item.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 285.dp, max = 350.dp)
                .clip(
                    if (item.mine) {
                        RoundedCornerShape(17.dp, 5.dp, 17.dp, 17.dp)
                    } else {
                        RoundedCornerShape(5.dp, 17.dp, 17.dp, 17.dp)
                    }
                )
                .background(bubbleColor)
                .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 7.dp),
        ) {
            item.reply?.let {
                CurrentReplyBlock(
                    reply = it,
                    appearance = appearance,
                    onClick = { onReplyPreviewClick(it.messageId) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = playColor,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(54.dp)
                        .clickable {
                            item.localPath?.let(onVoiceClick)
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play voice note",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                VoiceWaveform(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    mine = item.mine,
                )

                Spacer(modifier = Modifier.width(9.dp))

                Text(
                    text = item.duration,
                    color = PrimaryText,
                    fontSize = 12.sp,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                MessageMeta(item.time, item.mine, item.read)
            }
        }
    }
}


@Composable
private fun VoiceWaveform(
    modifier: Modifier = Modifier,
    mine: Boolean = false,
) {
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
                    .background(if (mine) Color(0xFFFF8DAA) else Color(0xFFB7BEC1)),
            )
        }
    }
}

@Composable
private fun SwipeToReplyContainer(
    messageId: String,
    onReplyRequest: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 64.dp.toPx() }
    val maxPx = with(density) { 88.dp.toPx() }
    var offset by remember(messageId) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(messageId) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount > 0f || offset > 0f) {
                            change.consume()
                            offset = (offset + dragAmount).coerceIn(0f, maxPx)
                        }
                    },
                    onDragEnd = {
                        if (offset >= thresholdPx) {
                            onReplyRequest(messageId)
                        }
                        offset = 0f
                    },
                    onDragCancel = {
                        offset = 0f
                    },
                )
            }
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = offset
            }
        ) {
            content()
        }

        if (offset > 8f) {
            Surface(
                color = Color(0xFF202628),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Reply",
                        tint = PrimaryText,
                        modifier = Modifier
                            .size(19.dp)
                            .graphicsLayer { rotationZ = 180f },
                    )
                }
            }
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
    isRecording: Boolean = false,
    onEmojiClick: () -> Unit,
    applyNavigationPadding: Boolean = true,
    appearance: NightChatAppearance = NightChatAppearance(),
    replyPreview: ReplyPreview? = null,
    onCancelReply: () -> Unit = {},
) {
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .then(
                if (applyNavigationPadding && imeBottom == 0.dp) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                }
            ),
    ) {
        replyPreview?.let { preview ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 5.dp, bottom = 1.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF171D20))
                    .padding(start = 8.dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CurrentReplyBlock(
                        reply = preview,
                        appearance = appearance,
                    )
                }
                IconButton(onClick = onCancelReply) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel reply",
                        tint = SecondaryText,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                            fontSize = (16f * appearance.messageFontScale).sp,
                        fontFamily = appearance.fontFamily,
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
                        cursorColor = appearance.accentColor,
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = (16f * appearance.messageFontScale).sp,
                        lineHeight = (20f * appearance.messageFontScale).sp,
                        fontFamily = appearance.fontFamily,
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
            color = appearance.accentColor,
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .clickable {
                    if (text.isBlank()) onMicClick() else onSendClick()
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        text.isNotBlank() -> Icons.Default.Send
                        isRecording -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = when {
                        text.isNotBlank() -> "Send"
                        isRecording -> "Stop recording"
                        else -> "Voice message"
                    },
                    tint = Color(0xFF10161A),
                    modifier = Modifier.size(24.dp),
                )
            }
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
        Triple("Choose AI", Icons.Default.Psychology, Color(0xFF039BE5)),
        Triple("Document", Icons.Default.Description, Color(0xFF7E57C2)),
        Triple("Audio", Icons.Default.MusicNote, Color(0xFF8E7CFF)),
        Triple("Options", Icons.Default.CheckCircle, Color(0xFFFFB300)),
        Triple("Schedule", Icons.Default.Schedule, Color(0xFFE91E63)),
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
            messageId = "photo",
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


fun mediaPreviewMessages(): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.PhotoMessage(
        id = "media-photo",
        caption = "This composition is exactly the direction I mean",
        time = "18:41",
        mine = false,
        aspectRatio = 1.18f,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "media-photo-reply",
        text = "Yeah, make the image card this large.",
        time = "18:42",
        mine = true,
        read = true,
        reply = ReplyPreview(
            messageId = "media-photo",
            author = "Night",
            text = "This composition is exactly the direction I mean and this preview can continue until the third rendered line before it gets cut off properly…",
            kind = ReplyKind.Image,
        ),
    ),
    WhatsAppVisualMessage.VideoMessage(
        id = "media-video",
        caption = "Video can use this bubble before the player is finished",
        duration = "2:14",
        time = "18:43",
        mine = true,
        read = true,
        aspectRatio = 16f / 9f,
    ),
    WhatsAppVisualMessage.VoiceMessage(
        id = "media-voice",
        duration = "0:23",
        time = "18:44",
        mine = false,
    ),
    WhatsAppVisualMessage.AudioMessage(
        id = "media-audio",
        title = "Midnight Drive",
        artist = "Night Library",
        duration = "3:42",
        detail = "MP3 • 8.6 MB",
        caption = "This is the separate music/audio message type.",
        time = "18:45",
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
    val context = LocalContext.current
    val emojiImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    val emojis = listOf(
        "😀" to "file:///android_asset/fluent_emoji/grinning.svg",
        "😂" to "file:///android_asset/fluent_emoji/joy.svg",
        "🥹" to "file:///android_asset/fluent_emoji/holding_tears.svg",
        "😍" to "file:///android_asset/fluent_emoji/heart_eyes.svg",
        "😭" to "file:///android_asset/fluent_emoji/crying.svg",
        "😎" to "file:///android_asset/fluent_emoji/sunglasses.svg",
        "👍" to "file:///android_asset/fluent_emoji/thumbs_up.svg",
        "❤️" to "file:///android_asset/fluent_emoji/red_heart.svg",
        "🙏" to "file:///android_asset/fluent_emoji/folded_hands.svg",
        "🔥" to "file:///android_asset/fluent_emoji/fire.svg",
    )

    Surface(
        color = ComposerBackground,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emojis.forEach { (emoji, asset) ->
                AsyncImage(
                    model = asset,
                    imageLoader = emojiImageLoader,
                    contentDescription = emoji,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onEmojiSelected(emoji) }
                        .padding(3.dp),
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
