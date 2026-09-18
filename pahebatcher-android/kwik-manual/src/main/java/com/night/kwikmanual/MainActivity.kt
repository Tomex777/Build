package com.night.kwikmanual

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val Bg = Color(0xFF0B0B0D)
private val Card = Color(0xFF151518)
private val Card2 = Color(0xFF1D1D22)
private val TextMain = Color(0xFFF4F1ED)
private val TextMuted = Color(0xFFA5A1A0)
private val Accent = Color(0xFFFF675B)
private val Success = Color(0xFF78D99A)
private val Failure = Color(0xFFFF7A70)

class MainActivity : ComponentActivity() {
    private val vm: KwikManualViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg,
                    surface = Card,
                    primary = Accent,
                    onPrimary = Color.Black,
                    onBackground = TextMain,
                    onSurface = TextMain,
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Bg,
                ) {
                    if (vm.browserOpen) {
                        ManualBrowser(vm)
                    } else {
                        ManualScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualScreen(vm: KwikManualViewModel) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val session = vm.session

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Kwik Manual", color = TextMain, fontSize = 30.sp)
        Text(
            "Paste a real Kwik release link and test everything after AnimePahe discovery. This app never runs the AnimePahe pipeline.",
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Card,
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = if (session.cookieSaved) Color(0xFF15231A) else Card2,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (session.cookieSaved) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Success,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text("-", color = TextMuted)
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kwik session", color = TextMain, fontSize = 15.sp)
                    Text(
                        session.kwikHost.ifBlank { "Not saved" },
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                    Text(
                        if (session.cookieSaved) session.cookieSummary else "No saved cookies",
                        color = TextMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = vm.kwikUrl,
                onValueChange = vm::setKwikUrl,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Kwik release URL") },
                placeholder = { Text("https://kwik...") },
            )
            TextButton(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val value = clipboard.primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    if (value.isNotBlank()) vm.setKwikUrl(value)
                },
            ) {
                Text("Paste")
            }
        }

        OutlinedTextField(
            value = vm.referer,
            onValueChange = vm::setReferer,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Referer") },
            supportingText = { Text("Usually the AnimePahe host that produced this Kwik link.") },
        )

        Button(
            onClick = vm::openVerification,
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Card2),
        ) {
            Icon(Icons.Rounded.Language, null)
            Spacer(Modifier.size(8.dp))
            Text("Open Kwik verification")
        }

        Button(
            onClick = vm::runTest,
            enabled = !vm.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.Black,
                )
            } else {
                Icon(Icons.Rounded.PlayArrow, null)
            }
            Spacer(Modifier.size(8.dp))
            Text("Test Kwik -> HLS")
        }

        vm.status?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Card2,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(14.dp),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        if (vm.checks.isNotEmpty()) {
            Text("Results", color = TextMain, fontSize = 20.sp)
            vm.checks.forEach { check ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Card,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        val tint = when (check.outcome) {
                            ManualOutcome.PASS -> Success
                            ManualOutcome.FAIL -> Failure
                            ManualOutcome.INFO -> TextMuted
                        }
                        Icon(
                            if (check.outcome == ManualOutcome.PASS) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.Error
                            },
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(check.label, color = TextMain, fontSize = 13.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                check.detail,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }

        if (vm.report.isNotBlank()) {
            Button(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Kwik Manual diagnostics", vm.report)
                    )
                    vm.clearStatus()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Card2),
            ) {
                Icon(Icons.Rounded.ContentCopy, null)
                Spacer(Modifier.size(8.dp))
                Text("Copy report")
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF101012),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    vm.report,
                    modifier = Modifier.padding(16.dp),
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        TextButton(onClick = vm::clearSession, enabled = !vm.busy) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = Failure)
            Spacer(Modifier.size(6.dp))
            Text("Clear Kwik session", color = Failure)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ManualBrowser(vm: KwikManualViewModel) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val view = webView
        if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            vm.closeBrowser()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::closeBrowser) {
                Icon(Icons.Rounded.Close, null, tint = TextMain)
            }
            Column(Modifier.weight(1f)) {
                Text("Kwik verification", color = TextMain, fontSize = 15.sp)
                Text(
                    "Finish any browser challenge, then save the session.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Rounded.Refresh, null, tint = TextMuted)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webView = this
                    }
                },
                update = { view ->
                    val target = vm.kwikUrl.trim()
                    if (target.isNotBlank() && view.tag != target) {
                        view.tag = target
                        val ref = vm.referer.trim()
                        if (ref.isBlank()) {
                            view.loadUrl(target)
                        } else {
                            view.loadUrl(target, mapOf("Referer" to ref))
                        }
                    }
                },
            )
        }

        Surface(color = Card) {
            Button(
                onClick = {
                    val page = webView?.url.orEmpty().ifBlank { vm.kwikUrl.trim() }
                    val cookie = CookieManager.getInstance().getCookie(page).orEmpty()
                    val ua = webView?.settings?.userAgentString.orEmpty()
                    CookieManager.getInstance().flush()
                    vm.saveBrowserSession(page, cookie, ua)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(14.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Text("Save Kwik session")
            }
        }
    }
}
