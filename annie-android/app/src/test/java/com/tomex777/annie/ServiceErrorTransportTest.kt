package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L4 (service half): a provider package's typed error must survive `services.call`. */
class ServiceErrorTransportTest {
    private fun envelope(vararg fields: Pair<String, Any?>): String {
        val inner = JSONObject()
        fields.forEach { (key, value) -> inner.put(key, value ?: JSONObject.NULL) }
        return JSONObject().put(SERVICE_ERROR_KEY, inner).toString()
    }

    private val identity: (String) -> String = { it }

    @Test fun preservesCodeOperationRetryAndRetryAfter() {
        val error = decodeServiceErrorEnvelope(
            envelope(
                "code" to "RATE_LIMITED", "message" to "slow down", "operation" to "android.notifications.post",
                "retryable" to true, "retryAfterMs" to 1500, "permission" to null,
            ),
            identity,
        )
        assertNotNull(error)
        error!!
        assertEquals(AnnieErrorCode.RATE_LIMITED, error.code)
        assertEquals("slow down", error.message)
        assertEquals("android.notifications.post", error.operation)
        assertTrue(error.retryable)
        assertEquals(1500L, error.retryAfterMs)
        assertNull(error.permission)
    }

    @Test fun preservesPermissionOnGateErrors() {
        val error = decodeServiceErrorEnvelope(
            envelope("code" to "NOT_GRANTED", "message" to "Permission x has not been granted",
                "operation" to "android.tts.speak", "permission" to "android.tts.speak"),
            identity,
        )!!
        assertEquals(AnnieErrorCode.NOT_GRANTED, error.code)
        assertEquals("android.tts.speak", error.permission)
    }

    @Test fun ordinaryResultsAreNotErrors() {
        assertNull(decodeServiceErrorEnvelope("""{"ok":true}""", identity))
        assertNull(decodeServiceErrorEnvelope("null", identity))
        assertNull(decodeServiceErrorEnvelope("[1,2,3]", identity))
        assertNull(decodeServiceErrorEnvelope("\"text\"", identity))
        assertNull(decodeServiceErrorEnvelope("""{"$SERVICE_ERROR_KEY":"not an object"}""", identity))
    }

    @Test fun unknownCodeBecomesInternalNotAnArbitraryString() {
        val error = decodeServiceErrorEnvelope(
            envelope("code" to "ENOENT", "message" to "m", "operation" to "o"), identity,
        )!!
        assertEquals(AnnieErrorCode.INTERNAL, error.code)
    }

    @Test fun messageGoesThroughRedaction() {
        val error = decodeServiceErrorEnvelope(
            envelope("code" to "NETWORK_ERROR", "message" to "token=abc123 failed", "operation" to "downloads.start"),
        ) { it.replace("abc123", "[redacted]") }!!
        assertFalse("abc123" in error.message)
        assertTrue("[redacted]" in error.message)
    }

    @Test fun blankFieldsFallBackToSafeDefaults() {
        val error = decodeServiceErrorEnvelope(envelope("code" to "TIMEOUT"), identity)!!
        assertEquals("Service failed", error.message)
        assertEquals("services.call", error.operation)
        assertFalse(error.retryable)
        assertNull(error.retryAfterMs)
    }

    @Test fun negativeRetryAfterIsDropped() {
        val error = decodeServiceErrorEnvelope(
            envelope("code" to "RATE_LIMITED", "message" to "m", "operation" to "o", "retryAfterMs" to -5), identity,
        )!!
        assertNull(error.retryAfterMs)
    }

    @Test fun oversizedFieldsAreBounded() {
        val error = decodeServiceErrorEnvelope(
            envelope("code" to "INTERNAL", "message" to "x".repeat(10_000), "operation" to "y".repeat(500),
                "permission" to "z".repeat(1_000)),
            identity,
        )!!
        assertEquals(2_000, error.message.length)
        assertEquals(128, error.operation.length)
        assertEquals(256, error.permission!!.length)
    }

    @Test fun publicEnvelopeCarriesOnlyTheSixPublicFields() {
        val json = decodeServiceErrorEnvelope(
            envelope("code" to "INVALID_ARGUMENT", "message" to "m", "operation" to "o", "cause" to "stack trace", "extra" to 1),
            identity,
        )!!.toPublicJson()
        val keys = json.keys().asSequence().toSet()
        assertEquals(setOf("code", "message", "operation", "retryable", "retryAfterMs", "permission"), keys)
    }
}