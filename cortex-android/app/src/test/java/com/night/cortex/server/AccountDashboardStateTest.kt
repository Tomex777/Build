package com.night.cortex.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDashboardStateTest {
    private fun account(
        id: String,
        name: String,
        status: String = "offline",
        connected: Boolean = false,
        paused: Boolean = false,
        profile: String = "",
        pairingError: String = "",
    ) = PairingAccount(
        id = id, enabled = true, connected = connected, status = status,
        numberMasked = "234••••" + id.takeLast(1),
        indexCount = 0, indexLimit = 5000,
        pairingMode = "", pairingCode = "", pairingQr = "",
        pairingError = pairingError, displayName = name,
        paused = paused, profile = profile,
    )

    private val accounts = listOf(
        account("A", "Main", "connected", connected = true, profile = "control"),
        account("account-2", "Josia", "connected", connected = true, profile = "josiah"),
        account("account-3", "Nami", "paused", paused = true, profile = "nami"),
        account("account-4", "Night Backup", "auth-invalid", profile = "unassigned"),
        account("account-5", "MiMi", "offline", profile = "mimi"),
        account("account-6", "Night Archive", "reconnecting", profile = "nami"),
        account("account-7", "Bad QR", "pairing", pairingError = "QR no longer valid"),
    )

    @Test fun overviewCountsDoNotMergeDuplicateAccountIdentities() {
        assertEquals(
            AccountOverview(total = 7, online = 2, attention = 2, paused = 1, offline = 2),
            accountOverview(accounts),
        )
    }

    @Test fun filterByLiveConnectionState() {
        assertEquals(listOf("A", "account-2"), visibleAccounts(accounts, "", AccountViewFilter.ONLINE).map { it.id })
        assertEquals(listOf("account-4", "account-7"), visibleAccounts(accounts, "", AccountViewFilter.ATTENTION).map { it.id })
        assertEquals(listOf("account-3"), visibleAccounts(accounts, "", AccountViewFilter.PAUSED).map { it.id })
        assertEquals(listOf("account-5", "account-6"), visibleAccounts(accounts, "", AccountViewFilter.OFFLINE).map { it.id })
    }

    @Test fun searchMatchesNicknameStableIdMaskedNumberAndBotProfile() {
        assertEquals(listOf("account-4"), visibleAccounts(accounts, "ACCOUNT-4", AccountViewFilter.ALL).map { it.id })
        assertEquals(listOf("account-4", "account-6"), visibleAccounts(accounts, "night", AccountViewFilter.ALL).map { it.id })
        assertEquals(listOf("account-2"), visibleAccounts(accounts, "joSIa", AccountViewFilter.ONLINE).map { it.id })
        assertEquals(listOf("account-3", "account-6"), visibleAccounts(accounts, "nami", AccountViewFilter.ALL).map { it.id })
        assertTrue(visibleAccounts(accounts, "unmatched", AccountViewFilter.ALL).isEmpty())
        assertEquals(7, visibleAccounts(accounts, "   ", AccountViewFilter.ALL).size)
    }
}
