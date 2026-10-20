package com.night.browser

import java.net.URI
import java.util.Locale

/** Pure policy, independent of Activity and Gecko, so it can be regression-tested. */
internal object ExternalNavigation {
    enum class Decision { ENGINE, CONFIRM_EXTERNAL, BLOCK }

    /**
     * Never dispatch redirects or script-driven navigations to external apps.
     * Android intent://, file:// and unsafe schemes are rejected instead of
     * parsing attacker-controlled Intent extras/components.
     */
    fun decide(
        target: String,
        hasUserGesture: Boolean,
        directNavigation: Boolean,
        isRedirect: Boolean
    ): Decision {
        val scheme = try {
            URI(target).scheme?.lowercase(Locale.ROOT)
        } catch (_: Exception) { null }
        return when (scheme) {
            "http", "https", "about", "blob", "data" -> Decision.ENGINE
            "mailto", "tel", "sms", "smsto", "geo", "market" ->
                if ((hasUserGesture || directNavigation) && !isRedirect)
                    Decision.CONFIRM_EXTERNAL else Decision.BLOCK
            else -> Decision.BLOCK
        }
    }
}
