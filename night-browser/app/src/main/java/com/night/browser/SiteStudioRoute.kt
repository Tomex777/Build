package com.night.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.mozilla.geckoview.GeckoSession

/**
 * The single Site Studio UI integration surface.
 *
 * Keeping Preview/Save/Reset/Undo/Cancel behavior together prevents callers
 * from accidentally leaving temporary JavaScript side effects active after
 * closing the dialog. Agent 1 only needs one route in the existing More menu.
 * Site Studio remains unavailable in private browsing; the caller must not
 * render this route for a private tab or call bridge.attach(private=true).
 */
@Composable
fun SiteStudioRoute(
    session: GeckoSession,
    pageUrl: String,
    store: SiteStudioStore,
    bridge: SiteStudioBridge,
    onClose: () -> Unit,
    onNotice: (String) -> Unit
) {
    val initial = remember(session, pageUrl) {
        bridge.editorRule(session, pageUrl)
    }

    fun finish(reload: Boolean) {
        if (reload && session.isOpen) session.reload()
        onClose()
    }

    fun discardPreviewAndFinish() {
        // Reload ONLY when a preview was active. Reloading removes its CSS and
        // any one-off JS effects; restoring the previous DOM in-place cannot.
        finish(reload = bridge.discardPreview(session))
    }

    SiteStudioEditor(
        pageUrl = pageUrl,
        initial = initial,
        canUndo = store.canUndo(pageUrl),
        onPreview = { draft ->
            if (!bridge.isReady()) {
                onNotice("Site Studio is not ready. Please try again.")
            } else if (bridge.preview(session, draft)) {
                finish(reload = true)
            } else {
                onNotice("This page cannot be previewed.")
            }
        },
        onSave = { rule ->
            if (store.save(rule)) {
                bridge.discardPreview(session)
                if (!bridge.isReady()) {
                    onNotice("Saved. Site Studio will apply when available.")
                }
                finish(reload = true)
            } else {
                onNotice("Site changes could not be saved.")
            }
        },
        onReset = {
            if (store.remove(pageUrl)) {
                bridge.discardPreview(session)
                finish(reload = true)
            } else {
                onNotice("Site changes could not be reset.")
            }
        },
        onUndo = {
            if (store.undo(pageUrl)) {
                bridge.discardPreview(session)
                finish(reload = true)
            } else {
                onNotice("No previous site changes could be restored.")
            }
        },
        onDismiss = ::discardPreviewAndFinish
    )
}
