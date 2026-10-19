package com.night.cortex.server

import org.junit.Assert.*
import org.junit.Test

class SensitiveLogRedactorTest {
    @Test fun signalIdentityAndRatchetNeverRendered() {
        val examples = listOf(
            "baseKey: <Buffer 05 84 2f 5c 5e da 1a fd>",
            "privKey: <Buffer 98 8c 3e 45 22 aa b6 2b>",
            "ephemeralKeyPair: {",
            "signedPreKey: <Buffer 1c 23>",
            "chainKey: <Buffer 05 84 2f 5c>",
            "identityKey: <Buffer 98 8c 3e 45>",
        )
        examples.forEach { row ->
            val safe = SensitiveLogRedactor.sanitize(row)
            assertTrue(safe.contains("[REDACTED"))
            assertFalse(safe.contains("<Buffer"))
            assertFalse(safe.contains("05 84"))
        }
    }

    @Test fun ordinaryDiagnosticTextRetained() {
        assertEquals("NIGHT connected, uptime 12h", SensitiveLogRedactor.sanitize("NIGHT connected, uptime 12h"))
    }

    @Test fun rawBufferAndAuthorizationMasked() {
        val raw = "session: <Buffer 03 12 41 52 63 74 85 96> bearer: very-private-access-credential"
        val safe = SensitiveLogRedactor.sanitize(raw)
        assertFalse(safe.contains("03 12 41"))
        assertFalse(safe.contains("very-private"))
        assertTrue(safe.contains("REDACTED"))
    }
}
