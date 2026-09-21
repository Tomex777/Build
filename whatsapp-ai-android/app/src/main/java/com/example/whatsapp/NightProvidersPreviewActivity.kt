package com.example.whatsapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whatsapp.data.night.NightProviderKeySummary
import com.example.whatsapp.data.night.NightProviderModelEntity
import com.example.whatsapp.data.night.NightProviderProfileEntity
import com.example.whatsapp.presentation.profile.NightProvidersScreen
import com.example.whatsapp.ui.theme.WhatsappTheme

class NightProvidersPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val now = 1_797_000_000_000L
        val groq = NightProviderProfileEntity(
            id = "preview-groq",
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Groq primary",
            secretAlias = "preview-groq-secret",
            endpoint = null,
            region = null,
            language = "en-US",
            voiceName = null,
            capabilities = "chat",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val azure = NightProviderProfileEntity(
            id = "preview-azure",
            providerType = "azure",
            serviceKind = "chat",
            displayName = "Azure fallback",
            secretAlias = "preview-azure-secret",
            endpoint = "https://night-preview.openai.azure.com",
            region = null,
            language = "en-US",
            voiceName = null,
            capabilities = "chat",
            isEnabled = true,
            isDefault = false,
            createdAt = now,
            updatedAt = now - 1L,
        )
        val models = listOf(
            NightProviderModelEntity(
                id = "preview-groq-model",
                profileId = groq.id,
                providerType = "groq",
                modelId = "llama-3.3-70b-versatile",
                displayName = "Llama 3.3 70B",
                deploymentName = null,
                capabilities = "text,vision,tools",
                isEnabled = true,
                isDefault = true,
                createdAt = now,
                updatedAt = now,
            ),
            NightProviderModelEntity(
                id = "preview-groq-backup",
                profileId = groq.id,
                providerType = "groq",
                modelId = "llama-3.1-8b-instant",
                displayName = "Llama 3.1 8B backup",
                deploymentName = null,
                capabilities = "text,tools",
                isEnabled = false,
                isDefault = false,
                createdAt = now,
                updatedAt = now - 1L,
            ),
            NightProviderModelEntity(
                id = "preview-azure-model",
                profileId = azure.id,
                providerType = "azure",
                modelId = "gpt-5.6-preview",
                displayName = "Azure GPT preview",
                deploymentName = "night-gpt-preview",
                capabilities = "text,vision,tools,image_generation",
                isEnabled = true,
                isDefault = true,
                createdAt = now,
                updatedAt = now - 2L,
            ),
        )

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightProvidersScreen(
                    profiles = listOf(groq, azure),
                    models = models,
                    onBack = {},
                    onCapabilityRoutingClick = {},
                    onAddProfile = { _, _, _, _, _, _, _, _, _ -> },
                    onAddModel = { _, _, _, _, _, _ -> },
                    onDeleteProfile = {},
                    onDeleteModel = {},
                    onSetProfileEnabled = { _, _ -> },
                    onMakeProfileDefault = {},
                    onSetModelEnabled = { _, _ -> },
                    onMakeModelDefault = {},
                    onEditProfile = { _, _, _, _, _, _, _ -> },
                    onEditModel = { _, _, _, _, _ -> },
                    onTestModel = { _, _ -> },
                    providerKeys = { profile ->
                        if (profile.id == groq.id) {
                            listOf(
                                NightProviderKeySummary("key-1", "Primary", "1234"),
                                NightProviderKeySummary("key-2", "Backup", "5678"),
                                NightProviderKeySummary("key-3", "Burst", "9012"),
                            )
                        } else {
                            emptyList()
                        }
                    },
                    onAddProviderKey = { _, _, _ -> },
                    onDeleteProviderKey = { _, _ -> },
                )
            }
        }
    }
}
