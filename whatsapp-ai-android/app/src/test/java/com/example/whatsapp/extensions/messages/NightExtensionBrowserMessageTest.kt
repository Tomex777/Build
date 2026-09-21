package com.example.whatsapp.extensions.messages

import com.example.whatsapp.data.browser.NightBrowserSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightExtensionBrowserMessageTest {
    @Test
    fun browserCardRoundTripsThroughExtensionSnapshot() {
        val snapshot = ExtensionMessageSnapshot(
            extensionId = "anime",
            messageType = "anime.verification",
            template = ExtensionCardTemplate.Browser,
            extensionName = "Anime",
            title = "Verify Anime",
            browser = NightBrowserSpec(
                sessionId = "anime.verify",
                initialUrl = "https://anime.example.com/",
                allowedHosts = listOf("anime.example.com"),
                verifyActionId = "verify_session",
            ),
        )

        val decoded = ExtensionMessageCodec.decode(
            ExtensionMessageCodec.encode(snapshot)
        )

        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals(ExtensionCardTemplate.Browser, decoded.template)
        assertEquals("anime.verification", decoded.messageType)
        assertTrue(decoded.hasValidNamespace())
        assertEquals("anime.verify", decoded.browser?.sessionId)
        assertEquals("verify_session", decoded.browser?.verifyActionId)
    }

    @Test
    fun registryCanRequireBrowserTemplateForDeclaredType() {
        NightExtensionMessageTypeRegistry.unregisterExtension("auth")
        NightExtensionMessageTypeRegistry.register(
            NightExtensionMessageTypeDefinition(
                extensionId = "auth",
                messageType = "auth.login",
                template = ExtensionCardTemplate.Browser,
                description = "Interactive sign-in browser.",
            )
        )

        assertTrue(
            NightExtensionMessageTypeRegistry.isAllowed(
                extensionId = "auth",
                messageType = "auth.login",
                template = ExtensionCardTemplate.Browser,
            )
        )

        NightExtensionMessageTypeRegistry.unregisterExtension("auth")
    }
}
