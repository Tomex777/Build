package com.night.cortex.server

import java.util.Locale

/** Client-side presentation only: account ID and live status always come from MSCC. */
internal enum class AccountViewFilter(val title: String) {
    ALL("All"),
    ONLINE("Online"),
    ATTENTION("Needs attention"),
    PAUSED("Paused"),
    CONNECTING("Connecting"),
    OFFLINE("Offline"),
}

internal data class AccountOverview(
    val total: Int,
    val online: Int,
    val attention: Int,
    val paused: Int,
    val connecting: Int,
    val offline: Int,
)

internal fun PairingAccount.needsAttention(): Boolean {
    val condition = status.lowercase(Locale.ROOT)
    return condition in setOf(
        "auth-invalid", "logged-out", "revoked", "session-expired",
        "expired", "failed", "error",
    ) || pairingError.isNotBlank()
}

internal fun PairingAccount.isPaused(): Boolean =
    paused || status.equals("paused", ignoreCase = true)

internal fun PairingAccount.isConnecting(): Boolean =
    !connected && !isPaused() && !needsAttention() &&
        status.lowercase(Locale.ROOT) in setOf("connecting", "reconnecting", "pairing", "pending")

internal fun PairingAccount.isOffline(): Boolean =
    !connected && !isPaused() && !needsAttention() && !isConnecting()

internal fun accountOverview(accounts: List<PairingAccount>): AccountOverview =
    AccountOverview(
        total = accounts.size,
        online = accounts.count { it.connected },
        attention = accounts.count { it.needsAttention() },
        paused = accounts.count { it.isPaused() && !it.needsAttention() },
        connecting = accounts.count { it.isConnecting() },
        offline = accounts.count { it.isOffline() },
    )

internal fun visibleAccounts(
    accounts: List<PairingAccount>,
    query: String,
    filter: AccountViewFilter,
): List<PairingAccount> {
    val needle = query.trim().lowercase(Locale.ROOT)
    return accounts.filter { account ->
        val matchesStatus = when (filter) {
            AccountViewFilter.ALL -> true
            AccountViewFilter.ONLINE -> account.connected
            AccountViewFilter.ATTENTION -> account.needsAttention()
            AccountViewFilter.PAUSED -> account.isPaused() && !account.needsAttention()
            AccountViewFilter.CONNECTING -> account.isConnecting()
            AccountViewFilter.OFFLINE -> account.isOffline()
        }
        val matchesSearch = needle.isEmpty() || listOf(
            account.displayName,
            account.id,
            account.numberMasked,
            account.profile,
            account.profileDisplayName,
        ).any { it.lowercase(Locale.ROOT).contains(needle) }
        matchesStatus && matchesSearch
    }
}
