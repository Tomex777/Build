package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.whatsapp.presentation.chatscreen.AnimeResultMessage
import com.example.whatsapp.presentation.chatscreen.ButtonResultMessage
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.LinkPreviewMessage
import com.example.whatsapp.presentation.chatscreen.MangaResultMessage
import com.example.whatsapp.presentation.chatscreen.MessageAction
import com.example.whatsapp.presentation.chatscreen.ReplyKind
import com.example.whatsapp.presentation.chatscreen.ReplyPreview
import com.example.whatsapp.presentation.chatscreen.ToolResultMessage
import com.example.whatsapp.presentation.chatscreen.WhatsAppVisualMessage
import com.example.whatsapp.presentation.chatscreen.whatsappPreviewMessages
import com.example.whatsapp.ui.theme.WhatsappTheme

class ChatPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val showAttachments = intent.getBooleanExtra("attachments", false)
        val showEmoji = intent.getBooleanExtra("emoji", false)
        val showMenu = intent.getBooleanExtra("menu", false)
        val mode = intent.getStringExtra("mode").orEmpty()
        val previewImage = "android.resource://" + packageName + "/" + R.drawable.bilal

        setContent {
            WhatsappTheme(darkTheme = true) {
                var text by remember { mutableStateOf("") }

                CurrentWhatsAppConversation(
                    contactName = "Night",
                    subtitle = "GPT-6 Astra • Memory on",
                    messages = when (mode) {
                        "media" -> mediaPreviewMessages(previewImage)
                        "audio" -> audioPreviewMessages(previewImage)
                        "docs" -> docsPreviewMessages(previewImage)
                        "rich" -> richApprovedPreviewMessages(previewImage)
                        else -> whatsappPreviewMessages()
                    },
                    messageText = text,
                    onMessageTextChange = { text = it },
                    onBackClick = {},
                    onSendClick = { text = "" },
                    onCallClick = {},
                    onAttachmentClick = {},
                    onCameraClick = {},
                    onMicClick = {},
                    onAttachmentAction = {},
                    onMessageButtonClick = { _, _ -> },
                    autoScrollToLatest = false,
                    attachmentsInitiallyOpen = showAttachments,
                    emojiInitiallyOpen = showEmoji,
                    menuInitiallyOpen = showMenu,
                )
            }
        }
    }
}

private fun mediaPreviewMessages(image: String): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.PhotoMessage(
        id = "media-photo-in",
        caption = "Beautiful morning from my hike! 🏔",
        time = "07:42",
        mine = false,
        localPath = image,
        aspectRatio = 1.65f,
    ),
    WhatsAppVisualMessage.PhotoMessage(
        id = "media-photo-out",
        caption = "Coffee and planning mode ☕",
        time = "08:15",
        mine = true,
        read = true,
        localPath = image,
        aspectRatio = 1.65f,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "media-reply",
        text = "That looks incredible! Where was this?",
        time = "08:16",
        mine = true,
        read = true,
        reply = ReplyPreview(
            messageId = "media-photo-in",
            author = "Night",
            text = "Beautiful morning from my hike! 🏔",
            kind = ReplyKind.Image,
            thumbnailPath = image,
        ),
    ),
    WhatsAppVisualMessage.VideoMessage(
        id = "media-video",
        caption = "Quick video from the viewpoint!",
        duration = "0:28",
        time = "18:21",
        mine = true,
        read = true,
        localPath = "preview-video",
        thumbnailPath = image,
        aspectRatio = 16f / 9f,
    ),
)

private fun audioPreviewMessages(image: String): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.TextMessage(
        id = "audio-intro",
        text = "Looks good! Do you want to record a quick voice note with your rough ideas?",
        time = "14:30",
        mine = false,
    ),
    WhatsAppVisualMessage.VoiceMessage(
        id = "voice-out",
        duration = "0:28",
        time = "14:31",
        mine = true,
        read = true,
    ),
    WhatsAppVisualMessage.VoiceMessage(
        id = "voice-in",
        duration = "0:17",
        time = "14:32",
        mine = false,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "voice-reply",
        text = "That makes sense! I like that approach.",
        time = "14:33",
        mine = true,
        read = true,
        reply = ReplyPreview(
            messageId = "voice-in",
            author = "Night",
            text = "Voice message",
            kind = ReplyKind.Voice,
            meta = "0:17",
        ),
    ),
    WhatsAppVisualMessage.AudioMessage(
        id = "music",
        title = "Midnight Drive",
        artist = "Lofi Zenith",
        duration = "3:42",
        detail = "MP3 • 4.8 MB",
        caption = "Here’s a track I’ve been listening to lately. Might fit the vibe you mentioned.",
        time = "14:34",
        mine = false,
        artworkPath = image,
    ),
)

private fun docsPreviewMessages(image: String): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.TextMessage(
        id = "docs-intro",
        text = "Here’s the project brief I mentioned yesterday. Take a look when you have a moment.",
        time = "10:16",
        mine = false,
    ),
    WhatsAppVisualMessage.FileMessage(
        id = "pdf",
        name = "Project_Brief_v1.pdf",
        detail = "2.4 MB • PDF Document",
        time = "10:16",
        mine = false,
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "thanks",
        text = "Thanks! This looks perfect.",
        time = "10:18",
        mine = true,
        read = true,
    ),
    LinkPreviewMessage(
        id = "link",
        title = "Material Design for Android",
        description = "Build beautiful, usable apps with Material Design 3, Google’s latest design system.",
        domain = "developer.android.com",
        thumbnailPath = image,
        mine = true,
        time = "10:19",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "pdf-reply",
        text = "I’ll review this in more detail and share my notes this evening.",
        time = "10:23",
        mine = false,
        reply = ReplyPreview(
            messageId = "pdf",
            author = "You",
            text = "Project_Brief_v1.pdf",
            kind = ReplyKind.File,
            meta = "2.4 MB • PDF",
        ),
    ),
)

private fun richApprovedPreviewMessages(image: String): List<WhatsAppVisualMessage> = listOf(
    ButtonResultMessage(
        id = "mood",
        title = "What are you in the mood for?",
        body = "I can suggest anime, movies, or TV shows based on your taste.",
        actions = listOf(
            MessageAction("surprise", "Surprise me"),
            MessageAction("anime", "Anime"),
            MessageAction("movies", "Movies"),
        ),
        time = "14:20",
    ),
    AnimeResultMessage(
        id = "anime",
        title = "Solo Leveling",
        episode = "12 Episodes",
        quality = "1080p",
        size = "Sub",
        time = "14:21",
        coverPath = image,
        description = "In a world of hunters and monsters, Sung Jin-Woo, the weakest hunter, gains a mysterious system that lets him level up.",
        primaryActionLabel = "Play Episode 1",
    ),
    MangaResultMessage(
        id = "manga",
        title = "Chainsaw Man",
        chapter = "Chapter 173",
        source = "MangaPlus",
        description = "Denji, a boy with a devil’s heart, hunts devils for a better life. A dark and thrilling story of chaos, power, and what it means to dream.",
        time = "14:22",
        coverPath = image,
        primaryActionLabel = "Read Chapter 1",
    ),
    ToolResultMessage(
        id = "notion",
        toolName = "Productivity • Tool",
        title = "Notion Assistant",
        subtitle = "Search, summarize, and write directly in your Notion workspace. Turn ideas into action faster.",
        time = "14:24",
        iconText = "N",
        actions = listOf(
            MessageAction("open", "Open"),
            MessageAction("setup", "Setup"),
            MessageAction("learn", "Learn More"),
        ),
    ),
)
