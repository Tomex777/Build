package com.night.sora.extension.api

/**
 * Optional API-v1 extension methods for manual source sessions.
 *
 * Existing extensions do not need to implement these methods. A source that
 * declares the `webview` capability can return a browser entry point and then
 * accept the resulting source-scoped cookie header after the user signs in or
 * completes a challenge in Sora's built-in browser.
 */
object ExtensionSessionContract {
    const val CAPABILITY_WEBVIEW = "webview"

    /**
     * Request payload:
     * { "sourceId": "...", "id": "optional source media id" }
     *
     * Response payload:
     * {
     *   "url": "https://source.example/title/...",
     *   "title": "Optional title",
     *   "headers": { "User-Agent": "...", "Referer": "..." }
     * }
     */
    const val METHOD_BROWSER_SESSION = "browserSession"

    /**
     * Request payload:
     * {
     *   "sourceId": "...",
     *   "url": "https://source.example/...",
     *   "cookieHeader": "name=value; ...",
     *   "userAgent": "...",
     *   "visitorData": "optional page-owned client/session id",
     *   "dataSyncId": "optional page-owned account sync id",
     *   "authUser": "optional account/session index"
     * }
     *
     * The cookie header is intentionally limited to the browser's current
     * source URL; Core never exposes unrelated app cookies or private state.
     * Page/session fields are optional and are read only from the source-owned
     * page itself, allowing extensions such as YouTube Music to keep an API
     * session aligned with the browser session without Core understanding it.
     */
    const val METHOD_STORE_SESSION = "storeSession"
}
