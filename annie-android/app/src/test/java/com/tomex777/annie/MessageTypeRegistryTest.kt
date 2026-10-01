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
            "file" to ScriptMessageKind.FILE,
            "season_list" to ScriptMessageKind.SEASON_LIST,
            "episode_list" to ScriptMessageKind.EPISODE_LIST,
            "continue_watching" to ScriptMessageKind.CONTINUE_WATCHING,
            "matches" to ScriptMessageKind.MATCHES,
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

    @Test fun episodeQualityPayloadKeepsOnlyRealChoicesAndSelectedValue() {
        val episode = JSONObject()
            .put("id", "e1")
            .put("title", "Episode 1")
            .put("qualities", org.json.JSONArray()
                .put("720p")
                .put(JSONObject().put("value", "1080p").put("label", "Full HD"))
                .put("720p"))
            .put("payload", JSONObject().put("season", 1))

        assertEquals(
            listOf(ScriptMediaQuality("720p", "720p"), ScriptMediaQuality("1080p", "Full HD")),
            scriptMediaQualities(episode),
        )
        val payload = JSONObject(scriptMediaActionPayload(episode, "fallback", "1080p"))
        assertEquals("e1", payload.getString("id"))
        assertEquals("Episode 1", payload.getString("title"))
        assertEquals(1, payload.getInt("season"))
        assertEquals("1080p", payload.getString("quality"))
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

