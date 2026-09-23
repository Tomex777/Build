package com.example.whatsapp.extensions.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.whatsapp.data.night.NightAgentToolExecutor
import com.example.whatsapp.data.night.NightRepository
import com.example.whatsapp.data.night.NightToolInvocation
import com.example.whatsapp.extensions.messages.ExtensionCardTemplate
import com.example.whatsapp.extensions.messages.ExtensionMessageCodec
import com.example.whatsapp.extensions.messages.ExtensionMessageSnapshot
import com.example.whatsapp.extensions.messages.NightExtensionMessageTypeDefinition
import com.example.whatsapp.extensions.messages.NightExtensionMessageTypeRegistry
import com.example.whatsapp.extensions.runtime.NightExternalExtensionManager
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightExtensionToolIntegrationInstrumentedTest {

    @Test
    fun installedAnimePaheIsRefreshedIntoTheEnabledModelInventory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = NightExternalExtensionManager.get(context)

        manager.refreshInstalledExtensions()
        val discovered = manager.extensions.value
            .singleOrNull { it.extensionId == "animepahe" }
            ?: error("The separately installed AnimePahe APK was not discovered.")

        assertEquals(5, discovered.toolCount)
        assertEquals(6, discovered.messageTypeCount)

        try {
            manager.setEnabled(discovered, true)

            val enabled = manager.extensions.value
                .single { it.extensionId == "animepahe" }
            assertTrue(enabled.enabled)
            assertEquals(5, enabled.toolCount)
            assertEquals(6, enabled.messageTypeCount)

            val toolSchemas = NightExtensionToolRegistry.schemas()
            assertEquals(5, toolSchemas.length())
            val registeredToolNames = buildList {
                for (index in 0 until toolSchemas.length()) {
                    add(
                        toolSchemas.getJSONObject(index)
                            .getJSONObject("function")
                            .getString("name")
                    )
                }
            }
            assertTrue(registeredToolNames.any { it.contains("search_anime") })

            val inventory = manager.modelContextSummary()
            assertTrue(inventory.contains("enabled in Night"))
            assertTrue(inventory.contains("5 tools"))
            assertTrue(inventory.contains("6 message types"))

            val toolPrompt = NightExtensionToolRegistry.promptSummary()
            registeredToolNames.forEach { toolName ->
                assertTrue("Missing model tool $toolName", toolPrompt.contains(toolName))
            }
            val messageTypePrompt = NightExtensionMessageTypeRegistry.promptSummary()
            assertTrue(messageTypePrompt.contains("animepahe."))

            val integrationPrompt = NightIntegrationToolRegistry.promptSummary()
            assertTrue(integrationPrompt.contains("search_anime"))
        } finally {
            manager.extensions.value
                .singleOrNull { it.extensionId == "animepahe" }
                ?.let { manager.setEnabled(it, false) }
        }
    }

    @Test
    fun extensionToolPersistsDeclaredRichMessage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val executor = NightAgentToolExecutor.get(context)
        val extensionId = "testext"
        val chatId = "extension-tool-" + UUID.randomUUID()

        val definition = NightExtensionToolDefinition(
            extensionId = extensionId,
            name = "lookup",
            description = "Return a test extension result.",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "query",
                        JSONObject().put("type", "string"),
                    )
                ),
        )
        val messageType = extensionId + ".result"

        try {
            repository.ensureChat(chatId, "Extension tool test")
            NightExtensionMessageTypeRegistry.register(
                NightExtensionMessageTypeDefinition(
                    extensionId = extensionId,
                    messageType = messageType,
                    template = ExtensionCardTemplate.Status,
                    description = "A lookup status result.",
                    whenToUse = "After lookup completes.",
                )
            )
            NightExtensionToolRegistry.register(definition) {
                    _,
                    arguments,
                ->
                val query = arguments.optString("query")
                val snapshot = ExtensionMessageSnapshot(
                    extensionId = extensionId,
                    messageType = messageType,
                    template = ExtensionCardTemplate.Status,
                    extensionName = "Test Extension",
                    title = "Found " + query,
                    status = "Ready",
                )
                JSONObject()
                    .put("ok", true)
                    .put(
                        "night_message",
                        JSONObject(ExtensionMessageCodec.encode(snapshot)),
                    )
            }

            val raw = executor.execute(
                chatId = chatId,
                invocation = NightToolInvocation(
                    id = "call-extension",
                    name = definition.qualifiedName,
                    argumentsJson = JSONObject()
                        .put("query", "episode")
                        .toString(),
                ),
            )
            val result = JSONObject(raw)

            assertTrue(result.optBoolean("ok", false))
            assertEquals(
                1,
                result.getJSONArray("night_rendered_messages").length(),
            )

            val message = repository.getMessages(chatId)
                .lastOrNull { it.type == "extension" }
                ?: error("Extension message was not persisted.")
            val snapshot = ExtensionMessageCodec.decode(message.payloadJson)
                ?: error("Persisted extension message was invalid.")

            assertEquals(extensionId, snapshot.extensionId)
            assertEquals(messageType, snapshot.messageType)
            assertEquals("Found episode", snapshot.title)
            assertEquals("Ready", snapshot.status)
        } finally {
            NightExtensionToolRegistry.unregisterExtension(extensionId)
            NightExtensionMessageTypeRegistry.unregisterExtension(extensionId)
            repository.deleteChat(chatId)
        }
    }
}
