package com.tomex777.annie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manifest steps 10-11: apiVersion dialect, requires range, publisher, network.hosts. */
class AnniePackageManifestTest {
    private val scripts = setOf("main.js")

    private fun manifest(extra: JSONObject.() -> Unit = {}) = JSONObject()
        .put("packageId", "com.example.demo").put("displayName", "Demo").put("version", "1.0.0")
        .put("apiVersion", "1").put("entryPoint", "main.js").apply(extra)

    private fun decode(json: JSONObject) = AnniePackageArchive.decodeManifest(json, scripts)

    private fun rejects(json: JSONObject, fragment: String) {
        val failure = runCatching { decode(json) }.exceptionOrNull()
        assertTrue("expected rejection containing '$fragment', got $failure", failure?.message.orEmpty().contains(fragment))
    }

    @Test fun legacyManifestIsUnchanged() {
        val decoded = decode(manifest())
        assertEquals(ValidationMode.LEGACY, decoded.validationMode)
        assertNull(decoded.requires)
        assertNull(decoded.publisher)
        assertTrue(decoded.networkHosts.isEmpty())
    }

    @Test fun requiresPublisherAndHostsAreParsed() {
        val decoded = decode(manifest {
            put("requires", JSONObject().put("annie", ">=1 <3"))
            put("publisher", JSONObject().put("id", "com.example").put("name", "Example"))
            put("network", JSONObject().put("hosts", JSONArray(listOf("api.example.com", "*.cdn.example.com"))))
        })
        assertEquals(">=1 <3", decoded.requires)
        assertEquals(AnniePackagePublisher("com.example", "Example"), decoded.publisher)
        assertEquals(listOf("api.example.com", "*.cdn.example.com"), decoded.networkHosts)
    }

    @Test fun incompatibleOrMalformedVersionFieldsAreImportErrorsWithAFix() {
        rejects(manifest { put("apiVersion", "2") }, "supports up to 1")
        rejects(manifest { put("apiVersion", "0") }, "whole number")
        rejects(manifest { put("apiVersion", "1.0") }, "whole number")
        rejects(manifest { put("requires", JSONObject().put("annie", ">=2")) }, "provides API 1")
        rejects(manifest { put("requires", JSONObject().put("annie", "^1")) }, "not a valid range")
    }

    @Test fun badPublisherAndHostsAreRejected() {
        rejects(manifest { put("publisher", JSONObject().put("id", "Example").put("name", "x")) }, "publisher.id")
        rejects(manifest { put("publisher", JSONObject().put("id", "com.example")) }, "publisher.name")
        rejects(manifest { put("network", JSONObject().put("hosts", JSONArray(listOf("https://api.example.com")))) }, "network host")
        rejects(manifest { put("network", JSONObject().put("hosts", JSONArray(listOf("a.example.com", "A.example.com")))) }, "network host")
        rejects(manifest { put("network", JSONObject().put("hosts", "api.example.com")) }, "must be an array")
    }

    @Test fun encodeDecodeRoundTripKeepsTheNewFields() {
        val original = decode(manifest {
            put("requires", JSONObject().put("annie", ">=1"))
            put("publisher", JSONObject().put("id", "com.example").put("name", "Example"))
            put("network", JSONObject().put("hosts", JSONArray(listOf("api.example.com"))))
        })
        val again = decode(AnniePackageArchive.encodeManifest(original))
        assertEquals(original.requires, again.requires)
        assertEquals(original.publisher, again.publisher)
        assertEquals(original.networkHosts, again.networkHosts)
    }
}
