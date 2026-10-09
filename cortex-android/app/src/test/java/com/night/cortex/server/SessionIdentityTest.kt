package com.night.cortex.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionIdentityTest {
    private fun account(
        id: String,
        name: String = "",
        profile: String = "",
        profileName: String = "",
        role: String = "linked",
    ) = PairingAccount(
        id = id,
        enabled = true,
        connected = false,
        status = "offline",
        numberMasked = "***1234",
        indexCount = 0,
        indexLimit = 5000,
        pairingMode = "",
        pairingCode = "",
        pairingQr = "",
        pairingError = "",
        displayName = name,
        role = role,
        profile = profile,
        profileDisplayName = profileName,
    )

    @Test fun duplicateJosiaNamesKeepDistinctSessionIds() {
        val first = account("account-2", name = "Josia", profile = "josiah")
        val second = account("account-3", name = "  JOSIA ", profile = "unassigned")
        val third = account("account-4", name = "Nami", profile = "nami")
        assertEquals(setOf("account-2", "account-3"), duplicateSavedNameIds(listOf(first, second, third)))
        assertEquals("Josia", first.title)
        assertEquals("JOSIA", second.title)
        assertEquals("Unassigned", second.profileLabel)
        assertEquals("Josia (josiah)", first.profileLabel)
    }

    @Test fun distinctSessionsAreNotIdentifiedByBotProfile() {
        val first = account("account-2", name = "One", profile = "josiah")
        val second = account("account-3", name = "Two", profile = "josiah")
        assertTrue(duplicateSavedNameIds(listOf(first, second)).isEmpty())
        assertEquals("One", first.title)
        assertEquals("Two", second.title)
        assertEquals("Josia (josiah)", first.profileLabel)
        assertEquals("Josia (josiah)", second.profileLabel)
    }

    @Test fun identityLabelsNeverInventAProfileWhenServerDoesNotReportOne() {
        assertEquals("Not reported by MSCC", account("account-8").profileLabel)
        assertEquals("Account 8", account("account-8").title)
        assertEquals("Main Control", account("A", role = "owner").title)
        assertEquals("Main control", account("A").accountRoleLabel)
        assertEquals("Linked session", account("account-8").accountRoleLabel)
        assertFalse(duplicateSavedNameIds(listOf(account("account-2"), account("account-3"))).isNotEmpty())
    }

    @Test fun profileDisplayNameDoesNotReplaceSessionName() {
        val session = account("account-9", name = "Night Backup", profile = "nami", profileName = "Nami")
        assertEquals("Night Backup", session.title)
        assertEquals("Nami (nami)", session.profileLabel)
    }
}
