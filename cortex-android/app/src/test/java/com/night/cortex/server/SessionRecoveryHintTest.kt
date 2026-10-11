package com.night.cortex.server

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryHintTest {
    @Test
    fun badMacDoesNotAutomaticallyInvalidateWhatsAppAuthentication() {
        val text = sessionRecoveryHint("reconnecting", "Error: Bad MAC")
        assertTrue(text!!.contains("does not prove"))
        assertTrue(text.contains("do not delete authentication automatically"))
    }

    @Test
    fun invalidSignInExplainsConfirmedRepairPath() {
        val text = sessionRecoveryHint("auth-invalid", "")
        assertTrue(text!!.contains("Re-pair"))
    }

    @Test
    fun normalConnectedAccountHasNoErrorBanner() {
        assertNull(sessionRecoveryHint("connected", ""))
    }
}
