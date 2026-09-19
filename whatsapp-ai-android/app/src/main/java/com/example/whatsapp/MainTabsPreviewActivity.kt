package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernCallsTab
import com.example.whatsapp.presentation.shell.ModernChatsTab
import com.example.whatsapp.presentation.shell.ModernCommunitiesTab
import com.example.whatsapp.presentation.shell.ModernSettingsScreen
import com.example.whatsapp.presentation.shell.ModernUpdatesTab
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
                    "updates" -> ModernUpdatesTab(
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
