package com.example.whatsapp.extensions.messages

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExtensionMessageTypeRegistryTest {
    @Test
    fun registeredExtensionOwnsItsNamespacedTypesAndTemplates() {
        NightExtensionMessageTypeRegistry.unregisterExtension("anime")
        NightExtensionMessageTypeRegistry.register(
            NightExtensionMessageTypeDefinition(
                extensionId = "anime",
                messageType = "anime.download_configuration",
                template = ExtensionCardTemplate.Configuration,
                description = "Download defaults and task settings.",
            )
        )

        assertTrue(
            NightExtensionMessageTypeRegistry.isAllowed(
                extensionId = "anime",
                messageType = "anime.download_configuration",
                template = ExtensionCardTemplate.Configuration,
            )
        )
        assertFalse(
            NightExtensionMessageTypeRegistry.isAllowed(
                extensionId = "anime",
                messageType = "anime.download_configuration",
                template = ExtensionCardTemplate.Content,
            )
        )
        assertFalse(
            NightExtensionMessageTypeRegistry.isAllowed(
                extensionId = "anime",
                messageType = "notion.page",
                template = ExtensionCardTemplate.Content,
            )
        )

        NightExtensionMessageTypeRegistry.unregisterExtension("anime")
    }

    @Test
    fun undeclaredLegacyExtensionStillRequiresNamespaceOwnership() {
        NightExtensionMessageTypeRegistry.unregisterExtension("legacy")

        assertTrue(
            NightExtensionMessageTypeRegistry.isAllowed(
                extensionId = "legacy",
                messageType = "legacy.result",
                template = ExtensionCardTemplate.Content,
            )
        )
        assertFalse(
            NightExtensionMessageTypeRegistry.isAllowed(
                extensionId = "legacy",
                messageType = "other.result",
                template = ExtensionCardTemplate.Content,
            )
        )
    }

    @Test
    fun promptSummaryExposesExactlyDeclaredMessageTypes() {
        NightExtensionMessageTypeRegistry.unregisterExtension("notion")
        NightExtensionMessageTypeRegistry.register(
            NightExtensionMessageTypeDefinition(
                extensionId = "notion",
                messageType = "notion.page",
                template = ExtensionCardTemplate.Content,
                description = "A Notion page result.",
                whenToUse = "the extension returns a page",
            )
        )

        val prompt = NightExtensionMessageTypeRegistry.promptSummary()
        assertTrue(prompt.contains("notion.page"))
        assertTrue(prompt.contains("content_card"))
        assertTrue(prompt.contains("do not invent or rename it"))

        NightExtensionMessageTypeRegistry.unregisterExtension("notion")
    }
}
