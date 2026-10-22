/*
 * Annie integration of upstream Omni Browser engine services.
 * Upstream: https://github.com/REBEL-ROOT/omni-browser
 * Original engine classes: Copyright (C) 2026 RebelRoot Ltd, GPL-3.0-or-later.
 * This adapter is a modification integrating those services into Annie's existing
 * standalone GeckoRuntime. No second runtime or independent browser APK is used.
 */
package com.tomex777.annie.browser.engine

import android.content.Context
import com.tomex777.annie.browser.engine.extensions.BuiltInExtensionManager
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import java.io.File

/**
 * Stable engine-side handoff for the Annie Browser UI workstream.
 * Owns Omni-derived session durability and a real bundled WebExtension.
 * Does not own Compose browser chrome, tab controls or user-facing labels.
 *
 * Private tabs are deliberately excluded from persistence; an extension is
 * NEVER installed or enabled without an explicit UI preference/action.
 */
internal class AnnieBrowserEngineBridge(context: Context, runtime: GeckoRuntime) {
    private val sessions = SessionStatePersistence(
        File(context.applicationContext.filesDir, "annie_browser_sessions_v2").apply { mkdirs() }
    )
    private val universalCopy = BuiltInExtensionManager(
        runtime = runtime,
        assetPath = "web_extensions/annie_universal_copy/",
        extensionId = "annie-universal-copy@annie.browser",
        label = "Universal Copy"
    )

    fun restoreNormalTab(tabId: String, expectedUrl: String): GeckoSession.SessionState? =
        sessions.readDurableState(tabId, expectedUrl)

    fun checkpoint(
        tabId: String,
        state: GeckoSession.SessionState,
        title: String,
        url: String,
        isPrivate: Boolean,
        canBack: Boolean,
        canForward: Boolean
    ) {
        if (isPrivate || !(url.startsWith("https://") || url.startsWith("http://"))) return
        sessions.requestPersist(
            tabId, state,
            AnnieSessionState.TabMetadata(
                title = title,
                url = url,
                isIncognito = false,
                lastActiveTime = System.currentTimeMillis(),
                canGoBack = canBack,
                canGoForward = canForward
            )
        )
    }

    fun forgetNormalTab(tabId: String) = sessions.removeDurableState(tabId)

    /** Optional on-demand page tool, available to Agent 2's UI. Default OFF. */
    fun setUniversalCopy(enabled: Boolean, onFinished: () -> Unit = {}) {
        universalCopy.installAndSync(enabled, onFinished)
    }

    fun isUniversalCopyInstalled(): Boolean = universalCopy.isInstalled()

    /** Drain accepted session writes without blocking browser close. */
    fun close() = sessions.shutdownWhenIdle()
}
