package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCompatibilityTest {
    @Test fun dialectSelectsValidationMode() {
        assertEquals(ValidationMode.LEGACY, ApiCompatibility.modeFor("1"))
        assertEquals(ValidationMode.STRICT, ApiCompatibility.modeFor("2"))
        listOf("", "0", "1.5", "x", "-1", "01").forEach { assertNull(it, ApiCompatibility.modeFor(it)) }
    }

    @Test fun rangesMatchLikeTheContractExample() {
        val range = VersionRange.parse(">=1 <3")!!
        assertTrue(range.matches("1")); assertTrue(range.matches("2")); assertTrue(range.matches("2.9"))
        assertFalse(range.matches("0")); assertFalse(range.matches("3"))
        assertTrue(VersionRange.parse("2")!!.matches("2.0.0"))
        assertTrue(VersionRange.parse(">1")!!.matches("1.1"))
        assertFalse(VersionRange.parse(">1")!!.matches("1"))
    }

    @Test fun malformedRangesAreRejected() {
        listOf("", "   ", ">=", "^1", "1.x", ">=1 <3 >0 <9 =1", "~2", ">=1,<3").forEach { assertNull(it, VersionRange.parse(it)) }
    }

    @Test fun checkAcceptsOlderDialectsAndMatchingRanges() {
        assertNull(ApiCompatibility.check("1", null, currentApi = 1))
        assertNull(ApiCompatibility.check("1", ">=1 <3", currentApi = 2))
        assertNull(ApiCompatibility.check("2", ">=2", currentApi = 2))
    }

    @Test fun checkExplainsEveryFailureWithAFix() {
        assertTrue(ApiCompatibility.check("3", null, 2)!!.contains("supports up to 2"))
        assertTrue(ApiCompatibility.check("x", null, 2)!!.contains("whole number"))
        assertTrue(ApiCompatibility.check("1", "banana", 2)!!.contains("not a valid range"))
        assertTrue(ApiCompatibility.check("1", ">=3", 2)!!.contains("provides API 2"))
    }

    @Test fun publisherRules() {
        assertNull(ManifestIdentity.validatePublisher("com.example", "Example"))
        assertNotNull(ManifestIdentity.validatePublisher("Example", "Example"))
        assertNotNull(ManifestIdentity.validatePublisher("com.Example", "Example"))
        assertNotNull(ManifestIdentity.validatePublisher(null, "Example"))
        assertNotNull(ManifestIdentity.validatePublisher("com.example", " "))
        assertNotNull(ManifestIdentity.validatePublisher("com.example", "x".repeat(81)))
    }

    @Test fun hostRules() {
        assertNull(ManifestIdentity.validateHosts(listOf("api.example.com", "*.cdn.example.com", "localhost")))
        listOf("https://api.example.com", "api.example.com:8080", "api.example.com/path", "*.com", "*", "a.*.com",
            "192.168.0.1", "-bad.example.com", "UPPER..case", "").forEach {
            assertNotNull("'$it' should be rejected", ManifestIdentity.validateHost(it))
        }
        assertNotNull(ManifestIdentity.validateHosts(listOf("a.example.com", "A.example.com")))
        assertNotNull(ManifestIdentity.validateHosts(List(33) { "h$it.example.com" }))
    }

    @Test fun wildcardMatchesSubdomainsButNotTheApex() {
        val patterns = listOf("api.example.com", "*.cdn.example.com")
        assertTrue(ManifestIdentity.hostAllowed(patterns, "api.example.com"))
        assertTrue(ManifestIdentity.hostAllowed(patterns, "a.cdn.example.com"))
        assertTrue(ManifestIdentity.hostAllowed(patterns, "A.B.cdn.example.com."))
        assertFalse(ManifestIdentity.hostAllowed(patterns, "cdn.example.com"))
        assertFalse(ManifestIdentity.hostAllowed(patterns, "evilcdn.example.com"))
        assertFalse(ManifestIdentity.hostAllowed(patterns, "api.example.com.evil.net"))
        assertFalse(ManifestIdentity.hostAllowed(patterns, "x.api.example.com"))
    }
}
