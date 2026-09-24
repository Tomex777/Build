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
import com.example.whatsapp.data.browser.NightBrowserSpec
import com.example.whatsapp.data.browser.NightBrowserVerificationState
import com.example.whatsapp.presentation.chatscreen.AnimeResultMessage
import com.example.whatsapp.presentation.chatscreen.ButtonResultMessage
import com.example.whatsapp.extensions.messages.ExtensionActionStyle
import com.example.whatsapp.extensions.messages.ExtensionCardAction
import com.example.whatsapp.extensions.messages.ExtensionCardMetadata
import com.example.whatsapp.extensions.messages.ExtensionCardTemplate
import com.example.whatsapp.extensions.messages.ExtensionConfiguration
import com.example.whatsapp.extensions.messages.ExtensionConfigurationField
import com.example.whatsapp.extensions.messages.ExtensionConfigurationFieldType
import com.example.whatsapp.extensions.messages.ExtensionConfigurationOption
import com.example.whatsapp.extensions.messages.ExtensionMessageSnapshot
import com.example.whatsapp.presentation.chatscreen.AudioPlaybackUiState
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.ExtensionResultMessage
import com.example.whatsapp.presentation.chatscreen.LinkPreviewMessage
import com.example.whatsapp.presentation.chatscreen.MangaResultMessage
import com.example.whatsapp.presentation.chatscreen.NightBlockMessage
import com.example.whatsapp.presentation.chatscreen.NightQuestionBlock
import com.example.whatsapp.presentation.chatscreen.NightQuestionOption
import com.example.whatsapp.presentation.chatscreen.MessageAction
import com.example.whatsapp.presentation.chatscreen.ReplyKind
import com.example.whatsapp.presentation.chatscreen.ReplyPreview
import com.example.whatsapp.presentation.chatscreen.WhatsAppVisualMessage
import com.example.whatsapp.presentation.chatscreen.approvedRichPreviewMessages
import com.example.whatsapp.presentation.chatscreen.mediaPreviewMessages
import com.example.whatsapp.presentation.chatscreen.nightBlockPreviewMessages
import com.example.whatsapp.presentation.chatscreen.richPreviewMessagesPageOne
import com.example.whatsapp.presentation.chatscreen.richPreviewMessagesPageTwo
import com.example.whatsapp.presentation.chatscreen.utilityPreviewMessages
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
                var browserVerificationState by remember {
                    mutableStateOf(NightBrowserVerificationState.Idle)
                }

                CurrentWhatsAppConversation(
                    contactName = "Night",
                    subtitle = "GPT-6 Astra • Memory on",
                    messages = when (mode) {
                        "media" -> mediaPreviewMessages(previewImage)
                        "audio" -> audioPreviewMessages(previewImage)
                        "voice" -> audioPreviewMessages(previewImage)
                        "docs" -> docsPreviewMessages(previewImage)
                        "rich" -> richApprovedPreviewMessages(previewImage)
                        "rich1" -> richPreviewMessagesPageOne()
                        "rich2" -> richPreviewMessagesPageTwo()
                        "approved-rich" -> approvedRichPreviewMessages()
                        "extension" -> extensionSchemaPreviewMessages()
                        "extension-config" -> extensionConfigurationPreviewMessages()
                        "browser" -> browserPreviewMessages(browserVerificationState)
                        "blocks" -> nightBlockPreviewMessages()
                        "options" -> optionPreviewMessages()
                        "utility" -> utilityPreviewMessages()
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
                    onMessageButtonClick = { _, actionId ->
                        if (mode == "browser" && actionId == "verify_session") {
                            browserVerificationState =
                                NightBrowserVerificationState.Verified
                        }
                    },
                    audioPlaybackState = when (mode) {
                        "audio" -> AudioPlaybackUiState(
                            activePath = "preview-audio",
                            isPlaying = true,
                            progress = 0.42f,
                            positionLabel = "1:34",
                        )
                        "voice" -> AudioPlaybackUiState(
                            activePath = "preview-voice-in",
                            isPlaying = true,
                            progress = 0.47f,
                            positionLabel = "0:08",
                        )
                        else -> AudioPlaybackUiState()
                    },
                    autoScrollToLatest = false,
                    attachmentsInitiallyOpen = showAttachments,
                    emojiInitiallyOpen = showEmoji,
                    menuInitiallyOpen = showMenu,
                )
            }
        }
    }
}

private fun optionPreviewMessages(): List<WhatsAppVisualMessage> = listOf(
    NightBlockMessage(
        id = "options-after-repeat",
        time = "14:26",
        blocks = listOf(
            NightQuestionBlock(
                blockId = "pick-one",
                title = "Pick one",
                detail = "Choose one. You can also type your own answer.",
                options = listOf(
                    NightQuestionOption(id = "a", label = "A"),
                    NightQuestionOption(id = "b", label = "B"),
                ),
                multiple = true,
            ),
        ),
    ),
)

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
        localPath = "preview-voice-out",
    ),
    WhatsAppVisualMessage.VoiceMessage(
        id = "voice-in",
        duration = "0:17",
        time = "14:32",
        mine = false,
        localPath = "preview-voice-in",
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
        localPath = "preview-audio",
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
    ExtensionResultMessage(
        id = "notion",
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



private fun browserPreviewMessages(
    verificationState: NightBrowserVerificationState =
        NightBrowserVerificationState.Idle,
): List<WhatsAppVisualMessage> = listOf(
    ExtensionResultMessage(
        id = "extension-browser-test",
        snapshot = ExtensionMessageSnapshot(
            extensionId = "browser_test",
            messageType = "browser_test.verification",
            template = ExtensionCardTemplate.Browser,
            extensionName = "Browser Test Extension",
            title = "Sign in to continue",
            subtitle = "Complete the website step without leaving Night.",
            iconText = "B",
            badge = "Browser",
            browser = NightBrowserSpec(
                sessionId = "test.browser",
                initialUrl = "http://127.0.0.1:8765/start",
                allowedHosts = listOf("127.0.0.1"),
                title = "Verification",
                verifyActionId = "verify_session",
                verifyLabel = "Verify",
                javaScriptEnabled = true,
                thirdPartyCookies = true,
                verificationState = verificationState,
                verificationMessage = when (verificationState) {
                    NightBrowserVerificationState.Verified ->
                        "Session verified by Browser Test Extension."
                    NightBrowserVerificationState.Failed ->
                        "Verification failed."
                    NightBrowserVerificationState.Verifying ->
                        "Checking session…"
                    NightBrowserVerificationState.Idle -> ""
                },
                verifiedAt = 1L.takeIf {
                    verificationState == NightBrowserVerificationState.Verified
                },
            ),
        ),
        time = "16:30",
    )
)

private fun extensionConfigurationPreviewMessages(): List<WhatsAppVisualMessage> = listOf(
    ExtensionResultMessage(
        id = "extension-config-anime",
        snapshot = ExtensionMessageSnapshot(
            extensionId = "anime",
            messageType = "anime.download_configuration",
            template = ExtensionCardTemplate.Configuration,
            extensionName = "Anime Extension",
            title = "Download settings",
            subtitle = "Saved defaults for this extension. A task can still override them.",
            iconText = "A",
            badge = "Configuration",
            configuration = ExtensionConfiguration(
                id = "download",
                submitActionId = "save_download_settings",
                submitLabel = "Save settings",
                advancedLabel = "Advanced",
                fields = listOf(
                    ExtensionConfigurationField(
                        id = "subtitles",
                        label = "Subtitles",
                        type = ExtensionConfigurationFieldType.Toggle,
                        description = "Prefer subtitled releases.",
                        value = "true",
                    ),
                    ExtensionConfigurationField(
                        id = "resolution",
                        label = "Resolution",
                        type = ExtensionConfigurationFieldType.SingleChoice,
                        value = "720p",
                        options = listOf(
                            ExtensionConfigurationOption("1080p", "1080p"),
                            ExtensionConfigurationOption("720p", "720p"),
                            ExtensionConfigurationOption("360p", "360p"),
                        ),
                    ),
                    ExtensionConfigurationField(
                        id = "parallel",
                        label = "Parallel downloads",
                        type = ExtensionConfigurationFieldType.Number,
                        value = "2",
                        placeholder = "2",
                        description =
                            "How many downloads this extension may run at once.",
                    ),
                    ExtensionConfigurationField(
                        id = "filename",
                        label = "Filename template",
                        type = ExtensionConfigurationFieldType.Text,
                        value = "{title} - {episode}",
                        advanced = true,
                    ),
                    ExtensionConfigurationField(
                        id = "test_connection",
                        label = "Test extension",
                        type = ExtensionConfigurationFieldType.Action,
                        actionLabel = "Run connection test",
                        advanced = true,
                    ),
                ),
            ),
        ),
        time = "16:20",
    )
)

private fun extensionSchemaPreviewMessages(): List<WhatsAppVisualMessage> {
    val notionPage = ExtensionMessageSnapshot(
        extensionId = "notion",
        messageType = "notion.page",
        template = ExtensionCardTemplate.Content,
        extensionName = "Notion Assistant",
        title = "Project Night",
        subtitle = "Product workspace",
        body = "14 pages • Updated 3 min ago",
        iconText = "N",
        badge = "Productivity • Tool",
        metadata = listOf(
            ExtensionCardMetadata("Workspace", "Night"),
        ),
        actions = listOf(
            ExtensionCardAction("open", "Open", ExtensionActionStyle.Primary),
            ExtensionCardAction("summarize", "Summarize"),
            ExtensionCardAction("search", "Search"),
        ),
        extensionPayloadJson = "{\"pageId\":\"project-night\",\"workspaceId\":\"night\"}",
    )

    return listOf(
        ExtensionResultMessage(
            id = "extension-notion-page",
            snapshot = notionPage,
            time = "14:24",
        ),
        WhatsAppVisualMessage.TextMessage(
            id = "extension-reply",
            text = "Summarize this page for me.",
            time = "14:25",
            mine = true,
            read = true,
            reply = ReplyPreview(
                messageId = "extension-notion-page",
                author = "Notion Assistant",
                text = "Project Night — Product workspace",
                kind = ReplyKind.Rich,
                meta = "Notion page",
                iconText = "N",
            ),
        ),
        ExtensionResultMessage(
            id = "extension-notion-unavailable",
            snapshot = notionPage.copy(
                title = "Project Night archive",
                subtitle = "Saved message snapshot",
                body = "The original card stays readable after the extension is removed.",
            ),
            time = "14:26",
            extensionAvailable = false,
        ),
    )
}
