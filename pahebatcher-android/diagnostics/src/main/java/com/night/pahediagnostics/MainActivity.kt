package com.night.pahediagnostics

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
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.viewmodel.compose.viewModel

private val Bg = Color(0xFF0B0B0D)
private val Card = Color(0xFF151518)
private val Card2 = Color(0xFF1D1D22)
private val TextMain = Color(0xFFF4F1ED)
private val TextMuted = Color(0xFFA5A1A0)
private val Accent = Color(0xFFFF675B)
private val Success = Color(0xFF78D99A)
private val Failure = Color(0xFFFF7A70)

class MainActivity : ComponentActivity() {
    private val vm: DiagnosticViewModel by viewModels()

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
                        BrowserScreen(vm)
                    } else {
                        DiagnosticsScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(vm: DiagnosticViewModel) {
    val context = LocalContext.current
    val sessions = vm.sessions
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Pahe Diagnostics",
            fontSize = 30.sp,
            color = TextMain,
        )
        Text(
            text = "Trace the real AnimePahe -> Kwik path and capture the exact failing stage. No Tor is packaged in this APK.",
            fontSize = 13.sp,
            color = TextMuted,
        )

        SessionCard(
            title = "AnimePahe session",
            saved = sessions.animeCookieSaved,
            host = sessions.animeHost.ifBlank { "Not saved" },
            cookieSummary = sessions.animeCookieSummary,
        )
        SessionCard(
            title = "Kwik session",
            saved = sessions.kwikCookieSaved,
            host = sessions.kwikHost.ifBlank { "Not saved" },
            cookieSummary = sessions.kwikCookieSummary,
        )

        Button(
            onClick = vm::openAnimeBrowser,
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Card2),
        ) {
            Icon(Icons.Rounded.Language, null)
            Spacer(Modifier.size(8.dp))
            Text("Open AnimePahe verification")
        }

        Button(
            onClick = vm::traceKwikDiscovery,
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Card2),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.size(8.dp))
            Text("Trace AnimePahe -> Kwik")
        }

        Button(
            onClick = vm::openKwikBrowser,
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Card2),
        ) {
            Icon(Icons.Rounded.Language, null)
            Spacer(Modifier.size(8.dp))
            Text("Open discovered Kwik verification")
        }

        Text(
            text = "The trace follows the same AnimePahe -> release -> play-page path the real app uses and stops at the exact failing stage. If it succeeds, the Kwik browser opens automatically.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )

        Spacer(Modifier.height(2.dp))

        Button(
            onClick = vm::runFullPipeline,
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
            Text("Run full pipeline")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = vm::testAnimePahe,
                enabled = !vm.busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
            ) {
                Text("Test AnimePahe")
            }
            OutlinedButton(
                onClick = vm::testKwik,
                enabled = !vm.busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
            ) {
                Text("Test Kwik")
            }
        }

        vm.statusMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Card2,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = message,
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        if (vm.checks.isNotEmpty()) {
            Text(
                text = "Results",
                color = TextMain,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            vm.checks.forEach { check ->
                CheckRow(check)
            }
        }

        if (vm.report.isNotBlank()) {
            Button(
                onClick = {
                    copyReport(context, vm.report)
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
                    text = vm.report,
                    modifier = Modifier.padding(16.dp),
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        TextButton(
            onClick = vm::clearSessions,
            enabled = !vm.busy,
        ) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = Failure)
            Spacer(Modifier.size(6.dp))
            Text("Clear diagnostic sessions", color = Failure)
        }
    }
}

@Composable
private fun SessionCard(
    title: String,
    saved: Boolean,
    host: String,
    cookieSummary: String,
) {
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
                color = if (saved) Color(0xFF15231A) else Card2,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (saved) {
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
                Text(title, color = TextMain, fontSize = 15.sp)
                Text(host, color = TextMuted, fontSize = 11.sp)
                Text(
                    text = if (saved) cookieSummary else "No saved cookies",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: DiagnosticCheck) {
    val tint = when (check.outcome) {
        Outcome.PASS -> Success
        Outcome.FAIL -> Failure
        Outcome.INFO -> TextMuted
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Card,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (check.outcome == Outcome.PASS) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(vm: DiagnosticViewModel) {
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
                Text(
                    text = if (vm.browserMode == BrowserMode.ANIMEPAHE) {
                        "AnimePahe verification"
                    } else {
                        "Kwik verification"
                    },
                    color = TextMain,
                    fontSize = 15.sp,
                )
                Text(
                    text = "Finish any browser challenge, then save the session below.",
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
                    if (view.tag != vm.browserUrl && vm.browserUrl.isNotBlank()) {
                        view.tag = vm.browserUrl
                        if (vm.browserMode == BrowserMode.KWIK) {
                            view.loadUrl(vm.browserUrl, mapOf("Referer" to vm.animeReferer()))
                        } else {
                            view.loadUrl(vm.browserUrl)
                        }
                    }
                },
            )
        }

        Surface(color = Card) {
            Button(
                onClick = {
                    val page = webView?.url.orEmpty().ifBlank { vm.browserUrl }
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
                Text(
                    if (vm.browserMode == BrowserMode.ANIMEPAHE) {
                        "Save AnimePahe session"
                    } else {
                        "Save Kwik session"
                    }
                )
            }
        }
    }
}

private fun copyReport(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pahe diagnostics", report))
}
