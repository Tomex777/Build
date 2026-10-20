package com.night.browser

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.util.Collections
import java.util.WeakHashMap

/**
 * Site Studio's GeckoView bridge. Only trusted top-level content-script
 * messages from registered GeckoSessions can retrieve a rule; the origin
 * comes from Gecko's MessageSender.url, never from a script-supplied string.
 *
 * Integration point for Agent 4: instantiate once with the existing runtime,
 * then attach(session, isPrivate = false) for normal sessions BEFORE loading
 * the URL; never attach private or disposable Peek sessions. detach(session)
 * on close. Agent 1 wires editor actions into MainActivity.
 */
class SiteStudioBridge(
    private val runtime: GeckoRuntime,
    private val store: SiteStudioStore
) {
    // Gecko callbacks and the Compose editor may reach the bridge from
    // different threads. Weak keys avoid retaining closed preview sessions;
    // synchronized containers prevent concurrent WeakHashMap corruption.
    private val attached = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<GeckoSession, Boolean>())
    )
    private val previews = Collections.synchronizedMap(
        WeakHashMap<GeckoSession, SiteRule>()
    )
    private var extension: WebExtension? = null
    private var ready = false

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP ||
                sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT ||
                !sender.isTopLevel) return null
            val session = sender.session ?: return null
            if (!attached.contains(session)) return null
            val request = message as? JSONObject ?: return null
            if (request.optString("type") != "rulesForPage") return null
            val origin = SiteOrigins.fromUrl(sender.url) ?: return null
            val rule = previews[session]?.takeIf { it.origin == origin }
                ?: store.get(sender.url)
            val response = JSONObject()
                .put("origin", origin)
                .put("css", if (rule?.cssEnabled == true) rule.css else "")
                .put("javascript", if (rule?.javascriptEnabled == true) rule.javascript else "")
            return GeckoResult.fromValue(response as Any)
        }
    }

    init {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept({ installed ->
                extension = installed
                ready = true
                synchronized(attached) { attached.toList() }.forEach { register(it) }
            }, { _ ->
                // No script injection if the extension cannot be installed.
                Log.w(TAG, "Site Studio extension could not be enabled.")
            })
    }

    fun isReady(): Boolean = ready

    /**
     * The caller MUST explicitly disclose whether this GeckoSession is private.
     * Deny-by-default: a private tab can never inherit saved normal-profile
     * rules even if another agent accidentally calls attach during creation.
     * Peek is also disposable; do not attach it unless the user explicitly
     * enables a separate session-only rule model.
     */
    fun attach(session: GeckoSession, isPrivate: Boolean) {
        if (isPrivate) {
            detach(session)
            return
        }
        attached.add(session)
        register(session)
    }

    fun detach(session: GeckoSession) {
        extension?.let {
            session.webExtensionController.setMessageDelegate(it, null, NATIVE_APP)
        }
        previews.remove(session)
        attached.remove(session)
    }

    /**
     * Preview only applies to this GeckoSession, for exactly the draft origin.
     * The caller explicitly reloads the session to apply changes; preview
     * never persists and never silently alters other tabs.
     */
    fun preview(session: GeckoSession, draft: SiteRule): Boolean {
        if (SiteOrigins.fromUrl(draft.origin) != draft.origin ||
            draft.css.length > SiteStudioStore.MAX_CSS ||
            draft.javascript.length > SiteStudioStore.MAX_JS ||
            !attached.contains(session)) return false
        previews[session] = draft
        return true
    }

    fun editorRule(session: GeckoSession, pageUrl: String): SiteRule? {
        val origin = SiteOrigins.fromUrl(pageUrl) ?: return null
        return previews[session]?.takeIf { it.origin == origin }
            ?: store.get(pageUrl) ?: SiteRule(origin)
    }

    fun discardPreview(session: GeckoSession) {
        previews.remove(session)
    }

    private fun register(session: GeckoSession) {
        extension?.let {
            session.webExtensionController.setMessageDelegate(it, delegate, NATIVE_APP)
        }
    }

    companion object {
        const val EXTENSION_ID = "site-studio@night.browser"
        const val EXTENSION_LOCATION = "resource://android/assets/site_studio/"
        const val NATIVE_APP = "night.site_studio"
        private const val TAG = "NightSiteStudio"
    }
}
