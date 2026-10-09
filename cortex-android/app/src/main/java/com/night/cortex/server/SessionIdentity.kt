package com.night.cortex.server

import java.util.Locale

/**
 * Labels for a WhatsApp connection, never a substitute for its stable account ID.
 * The saved account nickname and the bot personality are independent fields.
 */
internal val PairingAccount.title: String
    get() = displayName.trim().ifBlank {
        if (id == "A" || role == "owner") "Main Control"
        else if (id.startsWith("account-") && id.removePrefix("account-").isNotBlank())
            "Account ${id.removePrefix("account-")}"
        else "Account $id"
    }

internal val PairingAccount.profileLabel: String
    get() {
        val key = profile.trim()
        if (key.isEmpty()) return "Not reported by MSCC"
        if (key == "unassigned") return "Unassigned"
        val name = profileDisplayName.trim().ifBlank {
            when (key.lowercase(Locale.ROOT)) {
                "control" -> "Control"
                "josiah" -> "Josia"
                "nami" -> "Nami"
                "mimi" -> "MiMi"
                else -> key
            }
        }
        return if (name.equals(key, ignoreCase = true)) name else "$name ($key)"
    }

internal val PairingAccount.accountRoleLabel: String
    get() = if (id == "A" || role == "owner") "Main control" else "Linked session"

/**
 * A duplicate friendly name does NOT mean that two connections share one
 * authentication session. Keep the actual IDs visible and warn the operator.
 */
internal fun duplicateSavedNameIds(accounts: List<PairingAccount>): Set<String> =
    accounts
        .filter { it.displayName.isNotBlank() }
        .groupBy { it.displayName.trim().lowercase(Locale.ROOT) }
        .values
        .filter { it.size > 1 }
        .flatMap { rows -> rows.map { it.id } }
        .toSet()
