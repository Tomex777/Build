package com.example.whatsapp.presentation.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.browser.NightBrowserSessionStore
import com.example.whatsapp.data.browser.NightBrowserSpec
import com.example.whatsapp.data.browser.NightBrowserSpecCodec
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.net.URLEncoder
import java.util.UUID

private val BrowserBackground = ComposeColor(0xFF0B141A)
private val BrowserChrome = ComposeColor(0xFF172126)
private val BrowserPanel = ComposeColor(0xFF202C33)
private val BrowserText = ComposeColor(0xFFE9EDEF)
private val BrowserMuted = ComposeColor(0xFF8696A0)
private val BrowserAccent = ComposeColor(0xFFD44368)

private data class BrowserTab(
    val id: String,
    val url: String,
    val title: String,
)

class NightBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val encoded = intent.getStringExtra(EXTRA_SPEC).orEmpty()
        val spec =
            NightBrowserSpecCodec.decode(encoded)
                ?: intent
                    .getStringExtra(EXTRA_GENERAL_URL)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { NightBrowserSpec.general(initialUrl = normalizeBrowserInput(it)) }
                ?: if (intent.getBooleanExtra(EXTRA_GENERAL_BROWSER, false)) {
                    NightBrowserSpec.general()
                } else {
                    null
                }

        if (spec == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightBrowserFullScreen(
                    spec = spec,
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_SPEC = "night_browser_spec"
        private const val EXTRA_GENERAL_BROWSER = "night_general_browser"
        private const val EXTRA_GENERAL_URL = "night_general_browser_url"

        fun createIntent(
            context: Context,
            spec: NightBrowserSpec,
        ): Intent =
            Intent(context, NightBrowserActivity::class.java)
                .putExtra(
                    EXTRA_SPEC,
                    NightBrowserSpecCodec.encode(spec).toString(),
                )

        fun createGeneralIntent(
            context: Context,
            initialUrl: String? = null,
        ): Intent =
            Intent(context, NightBrowserActivity::class.java)
                .putExtra(EXTRA_GENERAL_BROWSER, true)
                .apply {
                    initialUrl
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { putExtra(EXTRA_GENERAL_URL, it) }
                }
    }
}

@Composable
fun NightBrowserFullScreen(
    spec: NightBrowserSpec,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val safeSpec = remember(spec) { spec.sanitized() }
    val controller = rememberNightBrowserController()
    val generalMode = !safeSpec.restrictedToAllowedHosts
    val tabs =
        remember(safeSpec.sessionId) {
            mutableStateListOf(
                BrowserTab(
                    id = UUID.randomUUID().toString(),
                    url = safeSpec.initialUrl,
                    title = safeSpec.title,
                )
            )
        }
    var selectedTabId by remember(safeSpec.sessionId) {
        mutableStateOf(tabs.first().id)
    }
    var addressText by remember { mutableStateOf(safeSpec.initialUrl) }
    var showMore by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }

    val selectedTab =
        tabs.firstOrNull { it.id == selectedTabId }
            ?: tabs.first()
    val activeSpec =
        remember(selectedTab.id, selectedTab.url, safeSpec) {
            if (generalMode) {
                safeSpec.copy(
                    sessionId = safeSpec.sessionId + ".tab." + selectedTab.id,
                    initialUrl = selectedTab.url,
                    restrictedToAllowedHosts = false,
                    allowedHosts = emptyList(),
                ).sanitized()
            } else {
                safeSpec
            }
        }

    fun navigate(raw: String) {
        val target = normalizeBrowserInput(raw)
        if (controller.loadUrl(activeSpec, target)) {
            addressText = target
        }
    }

    fun newTab() {
        if (!generalMode || tabs.size >= 8) return
        val tab =
            BrowserTab(
                id = UUID.randomUUID().toString(),
                url = "https://www.google.com/",
                title = "New tab",
            )
        tabs += tab
        selectedTabId = tab.id
        addressText = tab.url
        navigate(tab.url)
    }

    fun closeCurrentTab() {
        if (!generalMode || tabs.size <= 1) {
            onClose()
            return
        }
        val index = tabs.indexOfFirst { it.id == selectedTabId }
        if (index < 0) return
        tabs.removeAt(index)
        val next = tabs[index.coerceAtMost(tabs.lastIndex)]
        selectedTabId = next.id
        addressText = next.url
        navigate(next.url)
    }

    LaunchedEffect(selectedTabId) {
        addressText = selectedTab.url
        if (generalMode && controller.currentUrl != selectedTab.url) {
            controller.loadUrl(activeSpec, selectedTab.url)
        }
    }

    LaunchedEffect(controller.currentUrl, controller.pageTitle) {
        val current = controller.currentUrl
        if (current.isNotBlank()) {
            addressText = current
            val index = tabs.indexOfFirst { it.id == selectedTabId }
            if (index >= 0) {
                tabs[index] =
                    tabs[index].copy(
                        url = current,
                        title = controller.pageTitle.ifBlank { current },
                    )
            }
        }
    }

    BackHandler {
        if (showFind) {
            showFind = false
            controller.clearFind()
        } else if (controller.canGoBack()) {
            controller.goBack()
        } else {
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrowserBackground)
            .semantics {
                contentDescription = "Night full browser"
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrowserChrome)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics {
                        contentDescription = "Close browser"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = BrowserText,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text =
                            controller.pageTitle.ifBlank {
                                if (generalMode) "Night Browser" else safeSpec.title
                            },
                        color = BrowserText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (generalMode) {
                                currentHost(controller.currentUrl)
                                    .ifBlank { "Private Night browser" }
                            } else {
                                "Restricted session • " +
                                    safeSpec.normalizedAllowedHosts().joinToString(", ")
                            },
                        color = BrowserMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (generalMode) {
                    Box {
                        Surface(
                            color = BrowserPanel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { showTabs = true },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tabs.size.toString(),
                                    color = BrowserText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTabs,
                            onDismissRequest = { showTabs = false },
                            containerColor = BrowserChrome,
                        ) {
                            DropdownMenuItem(
                                text = { Text("New tab", color = BrowserText) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = BrowserText,
                                    )
                                },
                                onClick = {
                                    showTabs = false
                                    newTab()
                                },
                            )
                            tabs.forEachIndexed { index, tab ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = tab.title.ifBlank { "Tab " + (index + 1) },
                                                color = BrowserText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = currentHost(tab.url).ifBlank { tab.url },
                                                color = BrowserMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedTabId = tab.id
                                        showTabs = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box {
                    IconButton(
                        onClick = { showMore = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Browser menu"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = BrowserText,
                        )
                    }

                    DropdownMenu(
                        expanded = showMore,
                        onDismissRequest = { showMore = false },
                        containerColor = BrowserChrome,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Find in page", color = BrowserText) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = BrowserText,
                                )
                            },
                            onClick = {
                                showMore = false
                                showFind = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (controller.desktopMode) {
                                        "Mobile site"
                                    } else {
                                        "Desktop site"
                                    },
                                    color = BrowserText,
                                )
                            },
                            onClick = {
                                showMore = false
                                controller.setDesktopMode(!controller.desktopMode)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share page", color = BrowserText) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    tint = BrowserText,
                                )
                            },
                            onClick = {
                                showMore = false
                                shareUrl(context, controller.currentUrl)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Open externally", color = BrowserText) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = BrowserText,
                                )
                            },
                            onClick = {
                                showMore = false
                                openExternal(context, controller.currentUrl)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear site session", color = BrowserText) },
                            onClick = {
                                showMore = false
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                WebStorage.getInstance().deleteAllData()
                                NightBrowserSessionStore.clear(
                                    context = context,
                                    sessionId = activeSpec.sessionId,
                                )
                                controller.reload()
                            },
                        )
                        if (generalMode) {
                            DropdownMenuItem(
                                text = { Text("Close tab", color = ComposeColor(0xFFFF8A92)) },
                                onClick = {
                                    showMore = false
                                    closeCurrentTab()
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it.take(4096) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { navigate(addressText) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .semantics {
                        contentDescription = "Browser address"
                    },
            )

            if (showFind) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = {
                            findText = it.take(160)
                            controller.findInPage(it)
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { controller.findInPage(findText) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            showFind = false
                            findText = ""
                            controller.clearFind()
                        },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close find",
                            tint = BrowserText,
                        )
                    }
                }
            }

            if (controller.isLoading) {
                LinearProgressIndicator(
                    progress = { controller.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = BrowserAccent,
                    trackColor = BrowserPanel,
                )
            }

            controller.blockedUrl?.let {
                Text(
                    text =
                        if (safeSpec.restrictedToAllowedHosts) {
                            "Night blocked navigation outside this verification session."
                        } else {
                            "Night blocked an unsafe browser URL."
                        },
                    color = ComposeColor(0xFFFF8A92),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ComposeColor.White),
        ) {
            NightBrowserWebView(
                spec = activeSpec,
                controller = controller,
                modifier = Modifier.fillMaxSize(),
            )

            controller.errorText?.let { error ->
                Text(
                    text = error,
                    color = BrowserText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .background(
                            BrowserChrome.copy(alpha = 0.94f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrowserChrome)
                .navigationBarsPadding()
                .height(58.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = controller::goBack,
                enabled = controller.canGoBack(),
                modifier = Modifier.semantics {
                    contentDescription = "Browser back"
                },
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = if (controller.canGoBack()) BrowserText else BrowserMuted,
                )
            }

            IconButton(
                onClick = controller::goForward,
                enabled = controller.canGoForward(),
                modifier = Modifier.semantics {
                    contentDescription = "Browser forward"
                },
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = if (controller.canGoForward()) BrowserText else BrowserMuted,
                )
            }

            IconButton(
                onClick = { navigate(safeSpec.initialUrl) },
                modifier = Modifier.semantics {
                    contentDescription = "Browser home"
                },
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = BrowserText,
                )
            }

            IconButton(
                onClick = {
                    if (controller.isLoading) {
                        controller.stop()
                    } else {
                        controller.reload()
                    }
                },
                modifier = Modifier.semantics {
                    contentDescription =
                        if (controller.isLoading) "Stop loading" else "Reload browser"
                },
            ) {
                Icon(
                    if (controller.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = null,
                    tint = BrowserText,
                )
            }
        }
    }
}

private fun normalizeBrowserInput(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return "https://www.google.com/"

    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.contains(" ") ||
            (!trimmed.contains(".") && !trimmed.startsWith("localhost")) -> {
            "https://www.google.com/search?q=" +
                URLEncoder.encode(trimmed, "UTF-8")
        }
        else -> "https://" + trimmed
    }
}

private fun currentHost(rawUrl: String): String =
    runCatching {
        Uri.parse(rawUrl).host.orEmpty()
    }.getOrDefault("")

private fun shareUrl(
    context: Context,
    rawUrl: String,
) {
    if (rawUrl.isBlank()) return
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, rawUrl)
    context.startActivity(
        Intent.createChooser(intent, "Share page")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openExternal(
    context: Context,
    rawUrl: String,
) {
    if (rawUrl.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
