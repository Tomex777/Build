package com.night.browser

import org.junit.Assert.*
import org.junit.Test

class TabPersistencePolicyTest {
    @Test fun acceptsNormalWebUrlsAndRetainsEncoding() {
        assertEquals("https://example.com/a%20b?q=%2F", TabPersistencePolicy.sanitizeUrl("https://example.com/a%20b?q=%2F#frag"))
        assertEquals("http://localhost:8080/test", TabPersistencePolicy.sanitizeUrl("http://localhost:8080/test"))
        assertEquals("", TabPersistencePolicy.sanitizeUrl(""))
    }

    @Test fun stripsCredentialsAndFragmentsFromSavedDisplayUrl() {
        assertEquals("https://example.com/page", TabPersistencePolicy.sanitizeUrl("https://pat:password@example.com/page#access-token"))
        assertEquals("https://[::1]:8080/test", TabPersistencePolicy.sanitizeUrl("https://[::1]:8080/test"))
    }

    @Test fun rejectsInvalidLocalOrDangerousUris() {
        listOf(
            "javascript:alert(1)", "file:///data/data/secret", "about:config",
            "data:text/html,secret", "https:///no-host", "not a URL",
            "https://example.com/\nsecret", "https://example.com/ has-space"
        ).forEach { assertNull(it, TabPersistencePolicy.sanitizeUrl(it)) }
    }

    @Test fun rejectsOversizedAndUnsafeMetadata() {
        assertNull(TabPersistencePolicy.sanitizeUrl("https://example.org/" + "a".repeat(5_000)))
        assertTrue(TabPersistencePolicy.validId("c6fce130-7a1d-42cc-99b9-20cae437391a"))
        assertFalse(TabPersistencePolicy.validId("../../private/secret"))
        assertFalse(TabPersistencePolicy.validId(""))
        assertNull(TabPersistencePolicy.safeState("x".repeat(TabPersistencePolicy.MAX_SESSION_STATE_LENGTH + 1)))
        assertEquals("state", TabPersistencePolicy.safeState("state"))
    }
}
