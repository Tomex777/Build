package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.presentation.shell.ModernCallsTab
import com.example.whatsapp.presentation.shell.ModernChatsTab
import com.example.whatsapp.presentation.shell.ModernCommunitiesTab
import com.example.whatsapp.presentation.shell.ModernSettingsScreen
import com.example.whatsapp.presentation.files.NightFilesTab
import com.example.whatsapp.presentation.profile.NightYouTab
import com.example.whatsapp.ui.theme.WhatsappTheme

class MainTabsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val tab = intent.getStringExtra("tab") ?: "chats"

        setContent {
            WhatsappTheme(darkTheme = true) {
                when (tab) {
                    "updates", "files", "library" -> NightFilesTab(
                        onTabSelected = {},
                        onSettingsClick = {},
                    )
                    "communities" -> ModernCommunitiesTab(
                        onTabSelected = {},
                        onSettingsClick = {},
                    )
                    "calls" -> ModernCallsTab(
                        onTabSelected = {},
                        onSettingsClick = {},
                    )
                    "you" -> NightYouTab(
                        displayName = "Dawson",
                        onTabSelected = {},
                        onProfileClick = {},
                        onProvidersClick = {},
                        onMemoryClick = {},
                        onSchedulesClick = {},
                        onLibraryStorageClick = {},
                        onMediaLibraryClick = {},
                        onBrowserClick = {},
                        onAppearanceClick = {},
                        onPrivacyClick = {},
                        onSettingsClick = {},
                    )
                    "settings" -> ModernSettingsScreen(
                        onBack = {},
                    )
                    else -> ModernChatsTab(
                        chats = emptyList(),
                        onTabSelected = {},
                        onChatClick = {},
                        onSettingsClick = {},
                    )
                }
            }
        }
    }
}
