package com.example.whatsapp.extensions.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        NightMcpToolRegistry.unregisterServer("legacy")
        server.shutdown()
    }

    @Test
    fun modernMcpDiscoversAndExecutesThroughUnifiedRegistry() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":1,"result":{"resultType":"complete","supportedVersions":["2026-07-28"],"capabilities":{"tools":{}}}}"""
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":2,"result":{"resultType":"complete","tools":[{"name":"search_issues","description":"Search issues","inputSchema":{"type":"object","properties":{"query":{"type":"string","x-mcp-header":"Query"}},"required":["query"]},"annotations":{"readOnlyHint":true}}]}}"""
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":3,"result":{"resultType":"complete","content":[{"type":"text","text":"Issue #42"}],"structuredContent":{"count":1},"isError":false}}"""
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
                arguments = JSONObject().put("query", "Hello, 世界"),
            ) ?: error("MCP tool was not registered.")

            assertTrue(result.optBoolean("ok", false))
            assertEquals("Issue #42", result.optString("text"))
            assertEquals(
                1,
                result.getJSONObject("structured_content").optInt("count"),
            )

            val discover = server.takeRequest()
            val list = server.takeRequest()
            val call = server.takeRequest()

            val discoverBody = JSONObject(discover.body.readUtf8())
            assertEquals("server/discover", discoverBody.optString("method"))
            assertEquals(
                "2026-07-28",
                discover.getHeader("MCP-Protocol-Version"),
            )
            assertEquals("server/discover", discover.getHeader("Mcp-Method"))
            assertNull(discover.getHeader("Mcp-Session-Id"))
            assertEquals(
                "2026-07-28",
                discoverBody
                    .getJSONObject("params")
                    .getJSONObject("_meta")
                    .optString("io.modelcontextprotocol/protocolVersion"),
            )

            assertEquals(
                "tools/list",
                JSONObject(list.body.readUtf8()).optString("method"),
            )
            assertEquals(
                "2026-07-28",
                list.getHeader("MCP-Protocol-Version"),
            )
            assertEquals("tools/list", list.getHeader("Mcp-Method"))
            assertNull(list.getHeader("Mcp-Session-Id"))

            assertEquals(
                "tools/call",
                JSONObject(call.body.readUtf8()).optString("method"),
            )
            assertEquals("Bearer mcp-token", call.getHeader("Authorization"))
            assertEquals("tools/call", call.getHeader("Mcp-Method"))
            assertEquals("search_issues", call.getHeader("Mcp-Name"))
            assertEquals(
                "=?base64?SGVsbG8sIOS4lueVjA==?=",
                call.getHeader("Mcp-Param-Query"),
            )
        } finally {
            bridge.disconnect()
        }
    }

    @Test
    fun autoNegotiationFallsBackToLegacyHandshakeAndSession() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}"""
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "legacy-session")
                .setBody(
                    """{"jsonrpc":"2.0","id":2,"result":{"protocolVersion":"2025-11-25","capabilities":{"tools":{}},"serverInfo":{"name":"legacy-test","version":"1"}}}"""
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
                    """{"jsonrpc":"2.0","id":3,"result":{"tools":[{"name":"legacy_lookup","description":"Legacy lookup","inputSchema":{"type":"object","properties":{"query":{"type":"string"}}}}]}}"""
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"legacy ok"}],"isError":false}}"""
                )
        )

        val bridge = NightMcpHttpBridge(
            serverId = "legacy",
            endpoint = server.url("/mcp").toString(),
            http = OkHttpClient.Builder().build(),
        )

        try {
            val connected = bridge.connect()
            assertTrue(connected.isSuccess)
            assertEquals(1, connected.getOrThrow())

            val name = NightMcpToolRegistry.schemas()
                .getJSONObject(0)
                .getJSONObject("function")
                .getString("name")
            assertEquals("mcp__legacy__legacy_lookup", name)

            val result = NightMcpToolRegistry.execute(
                qualifiedName = name,
                chatId = "chat",
                arguments = JSONObject().put("query", "night"),
            ) ?: error("Legacy MCP tool was not registered.")
            assertTrue(result.optBoolean("ok", false))
            assertEquals("legacy ok", result.optString("text"))

            val probe = server.takeRequest()
            val initialize = server.takeRequest()
            val initialized = server.takeRequest()
            val list = server.takeRequest()
            val call = server.takeRequest()

            assertEquals(
                "server/discover",
                JSONObject(probe.body.readUtf8()).optString("method"),
            )
            assertEquals(
                "initialize",
                JSONObject(initialize.body.readUtf8()).optString("method"),
            )
            assertNull(initialize.getHeader("Mcp-Session-Id"))
            assertNull(initialize.getHeader("MCP-Protocol-Version"))

            assertEquals(
                "notifications/initialized",
                JSONObject(initialized.body.readUtf8()).optString("method"),
            )
            assertEquals("legacy-session", initialized.getHeader("Mcp-Session-Id"))
            assertEquals(
                "2025-11-25",
                initialized.getHeader("MCP-Protocol-Version"),
            )

            assertEquals("legacy-session", list.getHeader("Mcp-Session-Id"))
            assertEquals(
                "2025-11-25",
                list.getHeader("MCP-Protocol-Version"),
            )
            assertNull(list.getHeader("Mcp-Method"))

            assertEquals("legacy-session", call.getHeader("Mcp-Session-Id"))
            assertNull(call.getHeader("Mcp-Name"))
        } finally {
            bridge.disconnect()
        }
    }
}
