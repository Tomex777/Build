package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L4 (JVM part): the services.call error transport keeps the public AnnieError fields and nothing else. */
class ServiceErrorTransportTest {
    private val identity: (String) -> String = { it }

    private fun info(code: String, extra: JSONObject.() -> Unit = {}) = JSONObject()
        .put("code", code).put("message", "provider said no").put("operation", "provider.op").apply(extra)

    @Test fun providerCodesSurviveTheBoundary() {
        listOf("NETWORK_ERROR", "RATE_LIMITED", "INVALID_ARGUMENT").forEach { code ->
            val e = decodeServiceError(info(code), identity)
            assertEquals(code, e.code.name)
            assertEquals("provider.op", e.operation)
        }
    }

    @Test fun retryFieldsAreKept() {
        val e = decodeServiceError(info("RATE_LIMITED") { put("retryable", true); put("retryAfterMs", 1500) }, identity)
        assertTrue(e.retryable)
        assertEquals(1500L, e.retryAfterMs)
    }

    @Test fun permissionIsKeptAndNullsStayNull() {
        val e = decodeServiceError(info("NOT_GRANTED") { put("permission", "x.y"); put("retryAfterMs", JSONObject.NULL) }, identity)
        assertEquals("x.y", e.permission)
        assertNull(e.retryAfterMs)
    }

    @Test fun unknownCodeBecomesInternalAndMissingOperationFallsBack() {
        val e = decodeServiceError(JSONObject().put("code", "TOTALLY_MADE_UP").put("message", "m"), identity)
        assertEquals(AnnieErrorCode.INTERNAL, e.code)
        assertEquals("services.call", e.operation)
    }

    @Test fun messageIsRedactedAndBounded() {
        val e = decodeServiceError(info("NETWORK_ERROR") { put("message", "token=abc123 " + "x".repeat(2000)) }) {
            it.replace("abc123", "[redacted]")
        }
        assertFalse("abc123" in e.message)
        assertTrue(e.message.length <= MAX_SERVICE_ERROR_MESSAGE_CHARS)
    }

    @Test fun publicJsonHasNoCause() {
        val json = decodeServiceError(info("TIMEOUT") { put("cause", "java.lang.Boom\n\tat ...") }, identity).toPublicJson()
        assertFalse(json.has("cause"))
        assertEquals("TIMEOUT", json.getString("code"))
    }

    @Test fun gateFailuresCarryTheirOwnCodeAndPermission() {
        val e = serviceFailure(AnnieErrorCode.NOT_GRANTED, "Permission p has not been granted", "p")
        assertEquals("services.call", e.operation)
        assertEquals("p", e.permission)
        assertFalse(e.retryable)
    }
}
