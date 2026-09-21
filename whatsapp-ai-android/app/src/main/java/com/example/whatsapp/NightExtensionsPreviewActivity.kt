package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.extensions.runtime.NightInstalledExtensionSummary
import com.example.whatsapp.presentation.profile.NightExtensionsScreen
import com.example.whatsapp.ui.theme.WhatsappTheme

class NightExtensionsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val enabled = NightInstalledExtensionSummary(
            extensionId = "downloads",
            displayName = "Downloads",
            packageName = "com.night.extensions.downloads",
            serviceName = "com.night.extensions.downloads.NightExtensionService",
            toolCount = 2,
            messageTypeCount = 1,
            enabled = true,
        )
        val disabled = NightInstalledExtensionSummary(
            extensionId = "aniyomi",
            displayName = "Aniyomi Tools",
            packageName = "com.night.extensions.aniyomi",
            serviceName = "com.night.extensions.aniyomi.NightExtensionService",
            toolCount = 3,
            messageTypeCount = 2,
            enabled = false,
        )
        val blocked = NightInstalledExtensionSummary(
            extensionId = "duplicate",
            displayName = "Duplicate ID",
            packageName = "com.night.extensions.duplicate",
            serviceName = "com.night.extensions.duplicate.NightExtensionService",
            toolCount = 1,
            messageTypeCount = 1,
            enabled = false,
            error = "Duplicate extension id. Both packages are blocked until the conflict is removed.",
        )

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightExtensionsScreen(
                    extensions = listOf(
                        enabled,
                        disabled,
                        blocked,
                    ),
                    onBack = {},
                    onRefresh = {},
                    onSetEnabled = { _, _ -> },
                )
            }
        }
    }
}
