package com.night.browser

/**
 * Passive Peek never dispatches external app intents or unsafe local URLs.
 *
 * Loading an ordinary HTTP(S) URL is allowed; opening a full browser tab
 * remains an explicit user action. This policy is independent of the engine
 * bridge so Android host-scheme handlers cannot accidentally widen it.
 */
object PeekNavigationPolicy {
    fun allows(requestUrl: String): Boolean =
        SiteOrigins.fromUrl(requestUrl) != null
}
