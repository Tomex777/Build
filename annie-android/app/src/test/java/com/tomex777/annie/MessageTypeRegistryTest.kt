package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTypeRegistryTest {
    @Test fun firstPartyStructuredTypesResolveThroughTheRegistry() {
        val expected = mapOf(
            "text" to ScriptMessageKind.TEXT,
            "image" to ScriptMessageKind.IMAGE,
            "music" to ScriptMessageKind.MUSIC,
            "video" to ScriptMessageKind.VIDEO,
            "options" to ScriptMessageKind.OPTIONS,
            "browser" to ScriptMessageKind.BROWSER,
            "progress" to ScriptMessageKind.PROGRESS,
            "form" to ScriptMessageKind.FORM,
        )

        expected.forEach { (wire, kind) ->
            assertEquals(kind, MessageTypeRegistry.resolve(JSONObject().put("type", wire)).kind)
        }
        assertEquals(expected.keys, MessageTypeRegistry.supportedWireNames())
        assertEquals(
            ScriptMessageKind.UNKNOWN,
            MessageTypeRegistry.resolve(JSONObject().put("type", "arbitrary-compose")).kind,
        )
    }

    @Test fun videoPreviewUsesStructuredAspectRatioOrMediaDimensions() {
        val landscape = JSONObject().put("width", 1920).put("height", 1080)
        val portrait = JSONObject().put("width", 1080).put("height", 1920)
        val square = JSONObject().put("aspectRatio", 1.0)

        assertEquals(16f / 9f, ScriptVideoLayout.aspectRatio(landscape), 0.001f)
        assertEquals(9f / 16f, ScriptVideoLayout.aspectRatio(portrait), 0.001f)
        assertEquals(1f, ScriptVideoLayout.aspectRatio(square), 0.001f)

        val width = 320f
        val landscapeHeight = ScriptVideoLayout.previewHeightDp(width, landscape)
        val portraitHeight = ScriptVideoLayout.previewHeightDp(width, portrait)
        assertTrue(landscapeHeight in 150f..360f)
        assertTrue(portraitHeight in 150f..360f)
        assertTrue(portraitHeight > landscapeHeight)
    }
}
