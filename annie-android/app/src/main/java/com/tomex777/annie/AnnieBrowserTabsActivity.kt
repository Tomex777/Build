package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.UUID

/**
 * Standalone browser launched from Quick Actions.
 *
 * Each tab owns a distinct controller/WebView and scroll history; tabs intentionally share a
 * cookie profile. Script browser messages use their own session identifiers and cannot be
 * commandeered by this standalone browser.
 */
internal class AnnieBrowserTabsActivity : ComponentActivity() {
    private val ownedInstances = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnnieTheme {
                AnnieGeckoStandaloneBrowser(
                    onExit = ::finish,
                    onOwn = { ownedInstances.add(it) },
                    onRelease = { ownedInstances.remove(it) },
                )
            }
        }
    }

    override fun onDestroy() {
        ownedInstances.toList().forEach { id ->
            runCatching { AnnieBrowserControllers.get("annie.standalone.browser", id).destroy() }
        }
        ownedInstances.clear()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, AnnieBrowserTabsActivity::class.java)
    }
}

private const val STANDALONE_SESSION = "annie.standalone.browser"
private const val BROWSER_START_URL = "https://www.google.com/"

private data class StandaloneTab(val id: String, val startUrl: String = BROWSER_START_URL)

@Composable
internal fun AnnieStandaloneTabs(
    onExit: () -> Unit,
    onOwn: (String) -> Unit,
    onRelease: (String) -> Unit,
) {
    val tabs = remember {
        mutableStateListOf(StandaloneTab(UUID.randomUUID().toString()))
    }
    var selectedId by remember { mutableStateOf(tabs.first().id) }

    val tab = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
    val spec = remember {
        AnnieBrowserSpec(
            sessionId = STANDALONE_SESSION,
            url = BROWSER_START_URL,
            title = "Browser",
            restricted = false,
        ).sanitized()
    }
    val controller = remember(tab.id) {
        onOwn(tab.id)
        AnnieBrowserControllers.get(spec.sessionId, tab.id)
    }
    var address by remember(tab.id) { mutableStateOf(tab.startUrl) }
    LaunchedEffect(controller.currentUrl) {
        if (controller.currentUrl.isNotBlank()) address = controller.currentUrl
    }
    BackHandler(enabled = controller.canGoBack) { controller.goBack() }

    Column(Modifier.fillMaxSize().background(BrowserNight)
        .statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TextButton(onClick = onExit, modifier = Modifier.semantics { contentDescription = "Close browser" }) { Text("✕", color = BrowserBrightText) }
            tabs.forEachIndexed { index, item ->
                TextButton(
                    modifier = Modifier.testTag("annie_browser_tab_" + item.id),
                    onClick = { selectedId = item.id },
                ) {
                    Text(if (item.id == selectedId) "● ${index + 1}" else "${index + 1}", color = BrowserBrightText)
                }
                TextButton(onClick = {
                    AnnieBrowserControllers.get(spec.sessionId, item.id).destroy()
                    onRelease(item.id)
                    tabs.remove(item)
                    if (tabs.isEmpty()) {
                        val next = StandaloneTab(UUID.randomUUID().toString())
                        tabs.add(next)
                        selectedId = next.id
                    } else if (selectedId == item.id) {
                        selectedId = tabs.last().id
                    }
                }) { Text("×", color = BrowserSoftText) }
            }
            TextButton(
                modifier = Modifier.testTag("annie_browser_new_tab"),
                onClick = {
                    val next = StandaloneTab(UUID.randomUUID().toString())
                    tabs.add(next)
                    selectedId = next.id
                },
            ) { Text("+", color = BrowserTeal) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.take(AnnieBrowserSpec.MAX_URL_CHARS) },
                modifier = Modifier.weight(1f).testTag("annie_browser_tabs_address"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { loadAddress(controller, spec, address) }),
                singleLine = true,
                placeholder = { Text("Search or enter address") },
            )
            Button(onClick = { loadAddress(controller, spec, address) }, modifier = Modifier.semantics { contentDescription = "Go to address" }) { Text("↗") }
        }
        if (controller.message != null) {
            Text(controller.message.orEmpty(), color = BrowserSoftText)
        }
        AnnieBrowserWebView(
            spec = spec,
            controller = controller,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("annie_browser_tabs_webview"),
            startUrl = tab.startUrl,
            persistSession = false,
            keepAlive = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = controller::goBack, enabled = controller.canGoBack,
                modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹") }
            TextButton(onClick = controller::goForward, enabled = controller.canGoForward,
                modifier = Modifier.semantics { contentDescription = "Forward" }) { Text("›") }
            TextButton(onClick = controller::reload,
                modifier = Modifier.semantics { contentDescription = "Reload" }) { Text("⟳") }
        }
    }
}
