package com.night.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Peek is disposable: navigation/backstack remains inside its own GeckoSession.
 * Opening a tab uses the *current preview URL*, not the original long-pressed
 * address. The session is always released and closed on dismissal or promotion.
 *
 * Integration point (Agent 1/2): replace old private PeekDialog with
 * PeekOverlay(browser, url, onDismiss, onOpen = { lastUrl ->
 *   browser.newTab(lastUrl); closeDialogAndShowWeb()
 * }).
 */
@Composable
fun PeekOverlay(
    browser: BrowserController,
    url: String,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    // The browser's context menu filters initial links, but enforce the
    // contract again at the overlay boundary. Do not start a Gecko request,
    // create a session or trigger external intents for an unsupported scheme.
    if (!PeekNavigationPolicy.allows(url)) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                Modifier.fillMaxWidth().background(
                    Color(0xFF171D27), RoundedCornerShape(18.dp)
                ).padding(20.dp)
            ) {
                Text("This link cannot be previewed.", color = Color.White)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val peek = remember(url) { browser.newPeekSession(url) }
    val view = remember(url) { GeckoView(context) }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var title by remember(url) { mutableStateOf("Link preview") }
    var canGoBack by remember(url) { mutableStateOf(false) }
    // The current BrowserController starts the first request before Peek can
    // install delegates; defaulting to true could leave a permanent spinner
    // when an extremely fast/cached page finishes before delegate wiring.
    // A4 should change the controller to defer loadUri until delegates exist.
    var isLoading by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf<String?>(null) }

    DisposableEffect(peek) {
        peek.navigationDelegate = object : GeckoSession.NavigationDelegate {
            // GeckoSession has a single navigationDelegate. Preserve Agent 4's
            // scheme-blocking guarantee when replacing the Peek delegate;
            // previews must never dispatch intent:, file:, javascript:, etc.
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(
                if (PeekNavigationPolicy.allows(request.uri)) AllowOrDeny.ALLOW
                else AllowOrDeny.DENY
            )

            override fun onLocationChange(
                session: GeckoSession,
                pageUrl: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (pageUrl != null && PeekNavigationPolicy.allows(pageUrl)) {
                    if (currentUrl != pageUrl) title = "Link preview"
                    currentUrl = pageUrl
                }
            }
            override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                canGoBack = value
            }
        }
        peek.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, pageUrl: String) {
                isLoading = true
                error = null
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                isLoading = false
                if (!success) error = "Preview could not load."
            }
        }
        peek.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, pageTitle: String?) {
                if (!pageTitle.isNullOrBlank()) title = pageTitle
            }
            override fun onCrash(session: GeckoSession) {
                isLoading = false
                error = "Preview closed unexpectedly."
            }
        }
        onDispose {
            view.releaseSession()
            if (peek.isOpen) peek.close()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.91f)
                .background(Color(0xFF171D27), RoundedCornerShape(20.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().height(60.dp)
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoBack) {
                    IconButton(onClick = { peek.goBack() }) {
                        Icon(Icons.Rounded.ArrowBack, "Back within preview",
                            tint = Color.White)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 7.dp)) {
                    Text(title, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(currentUrl, color = Color(0xFFABB8CE), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 6.dp)
                            .height(17.dp),
                        strokeWidth = 2.dp
                    )
                }
                IconButton(
                    enabled = !isLoading && error == null &&
                        PeekNavigationPolicy.allows(currentUrl),
                    onClick = { onOpen(currentUrl) }
                ) {
                    Icon(Icons.Rounded.OpenInNew, "Open current preview in a tab",
                        tint = Color.White)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Dismiss preview", tint = Color.White)
                }
            }
            HorizontalDivider(color = Color(0xFF303A49))
            Box(Modifier.fillMaxSize().background(Color.White)) {
                AndroidView(
                    factory = { view.apply { setSession(peek) } },
                    modifier = Modifier.fillMaxSize()
                )
                error?.let { message ->
                    Row(
                        Modifier.align(Alignment.TopCenter)
                            .background(Color.White).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, color = Color(0xFFB00020),
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            error = null
                            isLoading = true
                            peek.reload()
                        }) { Text("Retry") }
                    }
                }
            }
        }
    }
}
