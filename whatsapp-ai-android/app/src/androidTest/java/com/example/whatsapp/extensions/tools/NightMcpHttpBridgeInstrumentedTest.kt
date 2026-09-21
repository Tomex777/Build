package com.example.whatsapp.extensions.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightMcpHttpBridgeInstrumentedTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        NightMcpToolRegistry.unregisterServer("github")
        server.shutdown()
    }

    @Test
    fun discoversAndExecutesMcpToolThroughUnifiedRegistry() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "night-session")
                .setBody(
                    """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"test","version":"1"}}}"""
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setHeader("Content-Type", "application/json")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"search_issues","description":"Search issues","inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]},"annotations":{"readOnlyHint":true}}]}}"""
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"Issue #42"}],"structuredContent":{"count":1},"isError":false}}"""
                )
        )

        val bridge = NightMcpHttpBridge(
            serverId = "github",
            endpoint = server.url("/mcp").toString(),
            bearerToken = "mcp-token",
            http = OkHttpClient.Builder().build(),
        )

        try {
            val connected = bridge.connect()
            assertTrue(connected.isSuccess)
            assertEquals(1, connected.getOrThrow())

            val schemas = NightMcpToolRegistry.schemas()
            assertEquals(1, schemas.length())
            val name = schemas
                .getJSONObject(0)
                .getJSONObject("function")
                .getString("name")
            assertEquals("mcp__github__search_issues", name)
            assertTrue(!NightMcpToolRegistry.isSideEffect(name))

            val result = NightMcpToolRegistry.execute(
                qualifiedName = name,
                chatId = "chat",
                arguments = JSONObject().put("query", "agent"),
            ) ?: error("MCP tool was not registered.")

            assertTrue(result.optBoolean("ok", false))
            assertEquals("Issue #42", result.optString("text"))
            assertEquals(1, result.getJSONObject("structured_content").optInt("count"))

            val initialize = server.takeRequest()
            val initialized = server.takeRequest()
            val list = server.takeRequest()
            val call = server.takeRequest()

            assertEquals(
                "initialize",
                JSONObject(initialize.body.readUtf8()).optString("method"),
            )
            assertEquals(
                "notifications/initialized",
                JSONObject(initialized.body.readUtf8()).optString("method"),
            )
            assertEquals("night-session", initialized.getHeader("Mcp-Session-Id"))
            assertEquals(
                "tools/list",
                JSONObject(list.body.readUtf8()).optString("method"),
            )
            assertEquals(
                "tools/call",
                JSONObject(call.body.readUtf8()).optString("method"),
            )
            assertEquals("Bearer mcp-token", call.getHeader("Authorization"))
        } finally {
            bridge.disconnect()
        }
    }
}
