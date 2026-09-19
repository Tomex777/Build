// Final Night UI validation
package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.whatsapp.data.NightFileLibrary
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.whatsapp.presentation.chat_box.ChatListModel
import com.example.whatsapp.presentation.chatscreen.AnimeResultMessage
import com.example.whatsapp.presentation.chatscreen.ButtonResultMessage
import com.example.whatsapp.presentation.chatscreen.MessageAction
import com.example.whatsapp.presentation.chatscreen.CurrentWhatsAppConversation
import com.example.whatsapp.presentation.chatscreen.DownloadResultMessage
import com.example.whatsapp.presentation.chatscreen.FileResultMessage
import com.example.whatsapp.presentation.chatscreen.ImageSearchResultMessage
import com.example.whatsapp.presentation.chatscreen.ToolResultMessage
import com.example.whatsapp.presentation.chatscreen.WhatsAppVisualMessage
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernCallsTab
import com.example.whatsapp.presentation.shell.ModernChatsTab
import com.example.whatsapp.presentation.shell.ModernCommunitiesTab
import com.example.whatsapp.presentation.shell.ModernSettingsScreen
import com.example.whatsapp.presentation.files.NightFilesTab
import com.example.whatsapp.presentation.profile.NightYouTab
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightApp()
            }
        }
    }
}

@Composable
private fun NightApp() {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.Chats.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)
    var screen by rememberSaveable { mutableStateOf("tabs") }
    var messageText by rememberSaveable { mutableStateOf("") }
    var messages by remember {
        mutableStateOf<List<WhatsAppVisualMessage>>(nightWelcomeMessages())
    }
    val context = LocalContext.current
    var pendingPickerKind by rememberSaveable { mutableStateOf("Document") }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val saved = NightFileLibrary.importUri(context, uri)
            val time = nightTime()
            val id = System.nanoTime().toString()
            if (saved != null) {
                messages = messages + when {
                    saved.mimeType.startsWith("image/") ->
                        WhatsAppVisualMessage.PhotoMessage(
                            id = "photo_$id",
                            caption = saved.name,
                            time = time,
                            mine = true,
                            read = true,
                        )
                    else ->
                        WhatsAppVisualMessage.TextMessage(
                            id = "file_$id",
                            text = "📎 " + saved.name,
                            time = time,
                            mine = true,
                            read = true,
                        )
                }
            }
        }
    }

    var activeChatTitle by rememberSaveable { mutableStateOf("Night") }
    var chatGeneration by rememberSaveable { mutableStateOf(1) }

    val chats = remember(chatGeneration) {
        buildList {
            add(
                ChatListModel(
                    name = "Night",
                    phoneNumber = "night-core",
                    userId = "night-core",
                    time = "Now",
                    message = "Summary synced • Core ready",
                )
            )
            if (chatGeneration > 1) {
                add(
                    0,
                    ChatListModel(
                        name = "New chat " + chatGeneration,
                        phoneNumber = "night-chat-" + chatGeneration,
                        userId = "night-chat-" + chatGeneration,
                        time = "Now",
                        message = "Fresh AI conversation",
                    )
                )
            }
            add(
                ChatListModel(
                    name = "Night UI",
                    phoneNumber = "night-ui",
                    userId = "night-ui",
                    time = "Yesterday",
                    message = "Summary ready • chat + library + memory",
                )
            )
        }
    }

    BackHandler(enabled = screen != "tabs") {
        screen = "tabs"
    }

    when (screen) {
        "settings" -> ModernSettingsScreen(
            onBack = { screen = "tabs" },
        )

        "chat" -> CurrentWhatsAppConversation(
            contactName = activeChatTitle,
            subtitle = "Core mode • extensions off",
            messages = messages,
            messageText = messageText,
            onMessageTextChange = { messageText = it },
            onBackClick = { screen = "tabs" },
            onSendClick = {
                val text = messageText.trim()
                if (text.isNotEmpty()) {
                    val time = nightTime()
                    val id = System.nanoTime().toString()
                    messages = messages +
                        WhatsAppVisualMessage.TextMessage(
                            id = "user_$id",
                            text = text,
                            time = time,
                            mine = true,
                            read = true,
                        ) +
                        routeNightCore(text, time, id)
                    messageText = ""
                }
            },
            onCallClick = {
                messages = messages + ToolResultMessage(
                    id = "call_${System.nanoTime()}",
                    toolName = "Voice",
                    title = "Voice service is not connected yet",
                    subtitle = "The Night chat UI is ready. Live voice can be wired after the core build.",
                    time = nightTime(),
                )
            },
            onMenuAction = { action ->
                val time = nightTime()
                messages = messages + when (action) {
                    "Memory & summary" -> ButtonResultMessage(
                        id = "memory_${System.nanoTime()}",
                        title = "Memory & summary",
                        body = "This chat keeps its own rolling summary and can reference summaries from your other Night chats.",
                        actions = listOf(
                            MessageAction("view_summary", "View latest summary"),
                            MessageAction("refresh_summary", "Refresh summary now"),
                        ),
                        time = time,
                    )
                    "Files in chat" -> ButtonResultMessage(
                        id = "files_${System.nanoTime()}",
                        title = "Files in chat",
                        body = "Open the items referenced by this conversation in your Night Library.",
                        actions = listOf(MessageAction("open_library", "Open Library")),
                        time = time,
                    )
                    "Choose AI" -> ButtonResultMessage(
                        id = "choose_ai_${System.nanoTime()}",
                        title = "Choose AI",
                        body = "Select which model should continue this conversation.",
                        actions = listOf(
                            MessageAction("ai_default", "Default"),
                            MessageAction("ai_fast", "Fast"),
                            MessageAction("ai_reasoning", "Reasoning"),
                        ),
                        time = time,
                    )
                    else -> ToolResultMessage(
                        id = "menu_${System.nanoTime()}",
                        toolName = "Chat",
                        title = action,
                        subtitle = "The chat action is wired into Night Core and ready for its final data operation.",
                        time = time,
                    )
                }
            },
            onMessageButtonClick = { _, actionId ->
                val time = nightTime()
                messages = messages + when (actionId) {
                    "open_library" -> WhatsAppVisualMessage.TextMessage(
                        id = "action_${System.nanoTime()}",
                        text = "Open Library",
                        time = time,
                        mine = true,
                        read = true,
                    )
                    "summarize", "refresh_summary" -> ToolResultMessage(
                        id = "summary_${System.nanoTime()}",
                        toolName = "Memory",
                        title = "Summary checkpoint requested",
                        subtitle = "Night will fold unsummarized messages and Library references into this chat’s latest summary.",
                        time = time,
                    )
                    "choose_ai", "ai_default", "ai_fast", "ai_reasoning" -> ToolResultMessage(
                        id = "ai_${System.nanoTime()}",
                        toolName = "AI",
                        title = "AI selection received",
                        subtitle = actionId.removePrefix("ai_").replaceFirstChar { it.uppercase() },
                        time = time,
                    )
                    "schedule_once", "schedule_repeat", "schedule_reminder" -> ToolResultMessage(
                        id = "schedule_action_${System.nanoTime()}",
                        toolName = "Schedule",
                        title = "Schedule option selected",
                        subtitle = actionId.removePrefix("schedule_").replaceFirstChar { it.uppercase() },
                        time = time,
                    )
                    else -> ToolResultMessage(
                        id = "button_${System.nanoTime()}",
                        toolName = "Action",
                        title = actionId,
                        subtitle = "Button action delivered to Night Core.",
                        time = time,
                    )
                }
            },
            onAttachmentClick = {},
            onAttachmentAction = { action ->
                when (action) {
                    "Gallery" -> {
                        pendingPickerKind = action
                        attachmentPicker.launch(arrayOf("image/*"))
                    }
                    "Document" -> {
                        pendingPickerKind = action
                        attachmentPicker.launch(arrayOf("*/*"))
                    }
                    "Camera" -> {
                        messages = messages + WhatsAppVisualMessage.PhotoMessage(
                            id = "camera_${System.nanoTime()}",
                            caption = "Photo",
                            time = nightTime(),
                            mine = true,
                            read = true,
                        )
                    }
                    "Choose AI" -> {
                        messages = messages + ButtonResultMessage(
                            id = "choose_ai_${System.nanoTime()}",
                            title = "Choose AI",
                            body = "Choose which AI should handle this conversation.",
                            actions = listOf(
                                MessageAction("ai_default", "Default"),
                                MessageAction("ai_fast", "Fast"),
                                MessageAction("ai_reasoning", "Reasoning"),
                            ),
                            time = nightTime(),
                        )
                    }
                    "Schedule" -> {
                        messages = messages + ButtonResultMessage(
                            id = "schedule_${System.nanoTime()}",
                            title = "Schedule with Night",
                            body = "Choose what you want Night to do with this task.",
                            actions = listOf(
                                MessageAction("schedule_once", "Schedule once"),
                                MessageAction("schedule_repeat", "Repeat"),
                                MessageAction("schedule_reminder", "Reminder only"),
                            ),
                            time = nightTime(),
                        )
                    }
                    else -> {
                        messages = messages + WhatsAppVisualMessage.TextMessage(
                            id = "attachment_${System.nanoTime()}",
                            text = "Attached " + action.lowercase(Locale.getDefault()),
                            time = nightTime(),
                            mine = true,
                            read = true,
                        )
                    }
                }
            },
            onCameraClick = {
                messages = messages + WhatsAppVisualMessage.PhotoMessage(
                    id = "camera_${System.nanoTime()}",
                    caption = "Photo",
                    time = nightTime(),
                    mine = true,
                    read = true,
                )
            },
            onMicClick = {
                messages = messages + WhatsAppVisualMessage.VoiceMessage(
                    id = "voice_${System.nanoTime()}",
                    duration = "0:08",
                    time = nightTime(),
                    mine = true,
                    read = true,
                )
            },
        )

        else -> when (selectedTab) {
            MainTab.Chats -> ModernChatsTab(
                chats = chats,
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onChatClick = {
                    activeChatTitle = it.name ?: "Night"
                    messages = nightWelcomeMessages()
                    screen = "chat"
                },
                onNewChat = {
                    chatGeneration += 1
                    activeChatTitle = "New chat " + chatGeneration
                    messages = nightWelcomeMessages()
                    messageText = ""
                    screen = "chat"
                },
                onSettingsClick = { screen = "settings" },
            )

            MainTab.Updates -> NightFilesTab(
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onSettingsClick = { screen = "settings" },
            )

            MainTab.Communities -> ModernCommunitiesTab(
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onSettingsClick = { screen = "settings" },
            )

            MainTab.Calls -> ModernCallsTab(
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onSettingsClick = { screen = "settings" },
            )

            MainTab.You -> NightYouTab(
                onTabSelected = {
                    selectedTabName = it.name
                    screen = "tabs"
                },
                onSettingsClick = { screen = "settings" },
            )
        }
    }
}

private fun nightWelcomeMessages(): List<WhatsAppVisualMessage> = listOf(
    WhatsAppVisualMessage.DateSeparator(
        id = "today",
        label = "Today",
    ),
    WhatsAppVisualMessage.TextMessage(
        id = "welcome",
        text = "Night is ready. The core chat works without extensions. Type “help” for the local routes or “demo” to see every rich result bubble.",
        time = nightTime(),
        mine = false,
    ),
)

private fun routeNightCore(
    raw: String,
    time: String,
    id: String,
): List<WhatsAppVisualMessage> {
    val query = raw.lowercase(Locale.getDefault())

    return when {
        query == "demo" || "show bubbles" in query || "show cards" in query -> listOf(
            FileResultMessage(
                id = "file_$id",
                name = "Night demo.pdf",
                detail = "UI preview • file result",
                time = time,
            ),
            AnimeResultMessage(
                id = "anime_$id",
                title = "Anime result preview",
                episode = "Extension card",
                quality = "1080p",
                size = "Preview",
                time = time,
            ),
            ImageSearchResultMessage(
                id = "images_$id",
                source = "Image result preview",
                resultCount = 4,
                time = time,
            ),
            DownloadResultMessage(
                id = "download_$id",
                title = "Download result preview",
                detail = "UI preview only",
                progress = 0.43f,
                time = time,
            ),
            ButtonResultMessage(
                id = "buttons_$id",
                title = "Choose an action",
                body = "Buttons are now a native Night message type.",
                actions = listOf(
                    MessageAction("open_library", "Open Library"),
                    MessageAction("summarize", "Summarize chat"),
                    MessageAction("choose_ai", "Choose AI"),
                ),
                time = time,
            ),
            ToolResultMessage(
                id = "tool_$id",
                toolName = "Night Core",
                title = "Rich result surfaces ready",
                subtitle = "Extensions are intentionally deferred; these are UI previews, not fake live results.",
                time = time,
            ),
        )

        "status" in query -> listOf(
            ToolResultMessage(
                id = "status_$id",
                toolName = "Night Core",
                title = "Core ready",
                subtitle = "Chat shell, navigation, composer, quotes, voice/file surfaces and rich result rendering are active. Extensions are off.",
                time = time,
            )
        )

        "extension" in query -> listOf(
            ToolResultMessage(
                id = "extensions_$id",
                toolName = "Extensions",
                title = "Extensions are deferred",
                subtitle = "Night remains usable in core mode. Source/tool extensions can be added later.",
                time = time,
            )
        )

        "anime" in query || "manga" in query || "episode" in query -> listOf(
            ToolResultMessage(
                id = "media_$id",
                toolName = "Extensions",
                title = "Media source not connected",
                subtitle = "The anime/media result UI is already present; the source extension will be added later.",
                time = time,
            )
        )

        "file" in query || "pdf" in query || "document" in query -> listOf(
            ToolResultMessage(
                id = "files_$id",
                toolName = "Files",
                title = "File result UI is ready",
                subtitle = "Type “demo” to preview the in-chat file card. Live file actions are kept separate from extensions.",
                time = time,
            )
        )

        "voice" in query || "mic" in query || "speak" in query -> listOf(
            ToolResultMessage(
                id = "voice_$id",
                toolName = "Voice",
                title = "Voice surface ready",
                subtitle = "The normal chat composer and voice-result flow are in place; speech services can be connected later.",
                time = time,
            )
        )

        "help" in query -> listOf(
            WhatsAppVisualMessage.TextMessage(
                id = "help_$id",
                text = "Core routes: status, extensions, files, voice, anime/media, and demo. Open-ended model replies stay optional so Night can still run without an AI service.",
                time = time,
                mine = false,
            )
        )

        else -> listOf(
            WhatsAppVisualMessage.TextMessage(
                id = "local_$id",
                text = "I received that in Night Core. This build keeps open-ended AI optional, so the local router stays usable even when no model or extension is connected.",
                time = time,
                mine = false,
            )
        )
    }
}

private fun nightTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
