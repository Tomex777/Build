/*
 * Annie adapter for authentic Omni Browser permission prompt models.
 * Upstream models: Copyright (C) 2026 RebelRoot Ltd, GPLv3.
 * Source: https://github.com/REBEL-ROOT/omni-browser
 *
 * No permission is silently granted: absent an explicit foreground UI handler,
 * Gecko must receive a definite denial rather than an unresolved request.
 */
package com.tomex777.annie.browser.engine.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.net.IDN
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Stores normal-tab decisions by exact scheme/host/port. Private tabs never write. */
internal class AnnieSitePermissionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "annie_browser_site_permissions_v1", Context.MODE_PRIVATE
    )

    fun origin(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it == "https" || it == "http" }
            ?: return null
        if (uri.rawUserInfo != null) return null
        val host = IDN.toASCII(uri.host?.trimEnd('.') ?: return null)
            .lowercase(Locale.ROOT).takeIf(String::isNotBlank) ?: return null
        val port = uri.port
        if (port < -1 || port > 65535) return null
        val suffix = when {
            port == -1 || (scheme == "https" && port == 443) ||
                (scheme == "http" && port == 80) -> ""
            else -> ":$port"
        }
        "$scheme://$host$suffix"
    }.getOrNull()

    private fun key(url: String, permission: String): String? {
        if (!permission.matches(Regex("[a-z][a-z0-9_-]{0,50}"))) return null
        return origin(url)?.let { "$it|$permission" }
    }

    fun decision(url: String, permission: String): Boolean? =
        when (key(url, permission)?.let { prefs.getString(it, null) }) {
            "allow" -> true
            "deny" -> false
            else -> null
        }

    fun remember(url: String, permission: String, allow: Boolean) {
        val key = key(url, permission) ?: return
        prefs.edit().putString(key, if (allow) "allow" else "deny").apply()
    }

    fun clear(url: String, permission: String) {
        key(url, permission)?.let { prefs.edit().remove(it).apply() }
    }
}

/** Attaches real Gecko PermissionDelegate callbacks while preserving user consent. */
internal class AnnieBrowserPermissionBroker(context: Context) {
    private val appContext = context.applicationContext
    private val store = AnnieSitePermissionStore(context)
    private val main = Handler(Looper.getMainLooper())

    var requestContentPrompt: ((ContentPermissionPrompt) -> Unit)? = null
    var requestMediaPrompt: ((MediaPermissionPrompt) -> Unit)? = null
    var requestSystemPrompt: ((SystemPermissionRequest) -> Unit)? = null

    /** The UI must not emit an allow action after a different tab is selected. */
    fun attach(session: GeckoSession, isPrivate: Boolean, isForeground: () -> Boolean) {
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                permission: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                val allow = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                if (!isForeground()) return GeckoResult.fromValue(deny)

                // From Omni: PERMISSION_TRACKING = exemption from anti-tracking;
                // granting it would turn off protection for the site.
                if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING) {
                    return GeckoResult.fromValue(deny)
                }
                val uri = permission.uri
                val permKey = "content_${permission.permission}"
                if (store.origin(uri) == null) return GeckoResult.fromValue(deny)
                if (!isPrivate) store.decision(uri, permKey)?.let {
                    return GeckoResult.fromValue(if (it) allow else deny)
                }
                val receiver = requestContentPrompt ?: return GeckoResult.fromValue(deny)
                val result = GeckoResult<Int>()
                val completed = AtomicBoolean(false)
                fun complete(granted: Boolean, remember: Boolean) {
                    if (!completed.compareAndSet(false, true)) return
                    val actuallyAllowed = granted && isForeground()
                    if (remember && !isPrivate && isForeground()) {
                        store.remember(uri, permKey, actuallyAllowed)
                    }
                    result.complete(if (actuallyAllowed) allow else deny)
                }
                val prompt = ContentPermissionPrompt(
                    siteUri = uri, permissionType = permission.permission,
                    onAllow = { complete(true, true) },
                    onAllowOnce = { complete(true, false) },
                    onDeny = { complete(false, true) }
                )
                runCatching { receiver(prompt) }.onFailure { complete(false, false) }
                main.postDelayed({ complete(false, false) }, 30_000L)
                return result
            }

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                val requested = permissions.orEmpty()
                val receiver = requestSystemPrompt
                if (!isForeground() || requested.isEmpty() || receiver == null) {
                    callback.reject()
                    return
                }
                val completed = AtomicBoolean(false)
                fun deny() {
                    if (completed.compareAndSet(false, true)) callback.reject()
                }
                val prompt = SystemPermissionRequest(
                    permissions = permissions,
                    rationaleTitle = "Website permission",
                    rationaleBody = "Review this website's Android permission request.",
                    onGranted = {
                        if (completed.compareAndSet(false, true)) {
                            if (isForeground() && requested.all {
                                    appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                                }) callback.grant() else callback.reject()
                        }
                    },
                    onDenied = { deny() }
                )
                runCatching { receiver(prompt) }.onFailure { deny() }
                main.postDelayed({ deny() }, 30_000L)
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                val receiver = requestMediaPrompt
                if (!isForeground() || store.origin(uri) == null ||
                    (video.isNullOrEmpty() && audio.isNullOrEmpty()) || receiver == null) {
                    callback.reject()
                    return
                }
                val videoNeeded = !video.isNullOrEmpty()
                val audioNeeded = !audio.isNullOrEmpty()
                val completed = AtomicBoolean(false)
                fun deny() {
                    if (completed.compareAndSet(false, true)) callback.reject()
                }
                fun grant(
                    chosenVideo: GeckoSession.PermissionDelegate.MediaSource?,
                    chosenAudio: GeckoSession.PermissionDelegate.MediaSource?,
                    remember: Boolean
                ) {
                    if (!completed.compareAndSet(false, true)) return
                    if (!isForeground() ||
                        (videoNeeded && (chosenVideo == null || video?.contains(chosenVideo) != true)) ||
                        (audioNeeded && (chosenAudio == null || audio?.contains(chosenAudio) != true))) {
                        callback.reject()
                        return
                    }
                    if (remember && !isPrivate) {
                        if (videoNeeded) store.remember(uri, "camera", true)
                        if (audioNeeded) store.remember(uri, "microphone", true)
                    }
                    callback.grant(chosenVideo, chosenAudio)
                }
                val prompt = MediaPermissionPrompt(
                    siteUri = uri, hasVideo = videoNeeded, hasAudio = audioNeeded,
                    videoSources = video, audioSources = audio,
                    onAllow = { v, a -> grant(v, a, true) },
                    onAllowOnce = { v, a -> grant(v, a, false) },
                    onDeny = { deny() }
                )
                runCatching { receiver(prompt) }.onFailure { deny() }
                main.postDelayed({ deny() }, 30_000L)
            }
        }
    }
}
