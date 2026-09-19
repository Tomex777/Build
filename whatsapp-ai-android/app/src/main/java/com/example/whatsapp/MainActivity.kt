package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.whatsapp.presentation.chat_box.ChatListModel
import com.example.whatsapp.presentation.chatscreen.AnimeResultMessage
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

    val chats = remember {
        listOf(
            ChatListModel(
                name = "Night",
                phoneNumber = "night-core",
                userId = "night-core",
                time = "Now",
                message = "Core ready • extensions off",
            )
        )
    }

    BackHandler(enabled = screen != "tabs") {
        screen = "tabs"
    }

    when (screen) {
        "settings" -> ModernSettingsScreen(
            onBack = { screen = "tabs" },
        )

        "chat" -> CurrentWhatsAppConversation(
            contactName = "Night",
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
            onAttachmentClick = {
                messages = messages + ToolResultMessage(
                    id = "attach_${System.nanoTime()}",
                    toolName = "Files",
                    title = "Attachment surface is ready",
                    subtitle = "File tools will plug into this surface without changing the chat UI.",
                    time = nightTime(),
                )
            },
            onCameraClick = {
                messages = messages + ToolResultMessage(
                    id = "camera_${System.nanoTime()}",
                    toolName = "Camera",
                    title = "Camera input is not connected in this core build",
                    subtitle = "The composer keeps the normal chat layout while integrations stay optional.",
                    time = nightTime(),
                )
            },
            onMicClick = {
                messages = messages + ToolResultMessage(
                    id = "mic_${System.nanoTime()}",
                    toolName = "Voice",
                    title = "Voice message surface is ready",
                    subtitle = "Speech transcription/TTS can be connected later without rebuilding the shell.",
                    time = nightTime(),
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
                onChatClick = { screen = "chat" },
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
