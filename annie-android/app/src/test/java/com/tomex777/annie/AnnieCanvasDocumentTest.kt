package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnieCanvasDocumentTest {
    @Test fun canvasIsRegisteredAsANativeMessageType() {
        assertEquals(ScriptMessageKind.CANVAS,
            MessageTypeRegistry.resolve(JSONObject().put("type", "canvas")).kind)
        assertTrue("canvas" in MessageTypeRegistry.supportedWireNames())
    }

    @Test fun canvasWrapsInlineHtmlCssAndJavaScriptInAnOfflineDocument() {
        val payload = JSONObject()
            .put("type", "canvas")
            .put("title", "Counter")
            .put("height", 900)
            .put("html", "<button id='counter'>+</button>")
            .put("css", "button { color: white; }")
            .put("javascript", "window.counterReady = true;")
        val canvas = AnnieCanvasDocument.from(payload)
        assertNotNull(canvas)
        assertEquals("Counter", canvas!!.title)
        assertEquals(500, canvas.heightDp)
        val page = canvas.page()
        assertTrue(page.contains("window.counterReady = true;"))
        assertTrue(page.contains("<button id='counter'>+</button>"))
        assertTrue(page.contains("button { color: white; }"))
        assertTrue(page.contains("default-src 'none'"))
        assertTrue(page.contains("connect-src 'none'"))
        assertTrue(page.contains("form-action 'none'"))
        assertFalse(page.contains("https://"))
    }

    @Test fun missingCodeAndOversizedPayloadsDoNotCreateCanvas() {
        assertNull(AnnieCanvasDocument.from(JSONObject().put("type", "canvas")))
        val oversized = JSONObject().put("type", "canvas")
            .put("javascript", "x".repeat(MAX_MESSAGE_BYTES + 1))
        assertNull(AnnieCanvasDocument.from(oversized))
    }

    @Test fun starterSnakeUsesRegisteredCanvasWireFormat() {
        assertTrue(StarterScripts.canvasSnake.contains("name: \"snake\""))
        assertTrue(StarterScripts.canvasSnake.contains("type: \"canvas\""))
        assertTrue(StarterScripts.canvasSnake.contains("annieCanvasSnakeReady"))
    }
}
