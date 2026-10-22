package com.tomex777.annie.browser.engine

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnnieSessionStateTest {
    @Test fun roundTripPreservesOpaqueGeckoBytesAndNavigation() {
        val original = AnnieSessionState(
            tabId = "stable-tab-id",
            sessionStateBytes = byteArrayOf(0, 1, 2, 127, -1),
            metadata = AnnieSessionState.TabMetadata(
                title = "Test", url = "https://example.org",
                isIncognito = false, lastActiveTime = 123L,
                canGoBack = true, canGoForward = false
            ),
            timestamp = 456L
        )
        val recovered = AnnieSessionState.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original, recovered)
        assertArrayEquals(original.sessionStateBytes, recovered.sessionStateBytes)
    }

    @Test fun privateFlagSurvivesSerializerForPrivacyFilter() {
        val privateSession = AnnieSessionState(
            tabId = "secret", sessionStateBytes = byteArrayOf(2),
            metadata = AnnieSessionState.TabMetadata(
                title = "Private", url = "https://example.org/private",
                isIncognito = true, lastActiveTime = 0L,
                canGoBack = false, canGoForward = false
            )
        )
        assertTrue(AnnieSessionState.fromJson(privateSession.toJson()).metadata.isIncognito)
    }
}
