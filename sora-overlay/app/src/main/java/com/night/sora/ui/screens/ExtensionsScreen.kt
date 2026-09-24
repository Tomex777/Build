@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionSessionContract
import com.night.sora.ui.components.DenseRow
import org.json.JSONObject

@Composable
fun ExtensionsScreen(
    extensions: List<InstalledExtension>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (InstalledExtension) -> Unit,
) {
    val externalExtensions = extensions.filterNot { extension ->
        extension.packageName == "com.night.sora" && extension.declaredId == "sora.core.jikan"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh") } },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (externalExtensions.isEmpty()) {
                item { DenseRow("No compatible extensions", "Sora Core is running by itself.", Icons.Rounded.ExtensionOff) }
            }
            items(externalExtensions.size) { index ->
                val extension = externalExtensions[index]
                DenseRow(
                    extension.declaredName,
                    extension.error ?: "${extension.descriptor?.sources?.size ?: 0} sources · API ${extension.apiVersion}",
                    if (extension.error == null) Icons.Rounded.Extension else Icons.Rounded.Warning,
                    trailing = extension.descriptor?.version,
                    onClick = { onOpen(extension) },
                )
            }
        }
    }
}

@Composable
fun ExtensionDetailScreen(extension: InstalledExtension, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { ExtensionManager(context.applicationContext) }
    val descriptor = extension.descriptor
    val isRedditSource = extension.packageName == "com.night.sora.ext.memes.reddit"
    val isLiveSource = extension.packageName == "com.night.sora.ext.live"
    val redditPreferences = remember(context) { context.getSharedPreferences("sora_reddit_source_v1", android.content.Context.MODE_PRIVATE) }
    val tmdbPreferences = remember(context) { context.getSharedPreferences("sora_tmdb_source_v1", android.content.Context.MODE_PRIVATE) }
    var redditClientId by remember(extension.packageName) {
        mutableStateOf(redditPreferences.getString("client_id", "").orEmpty())
    }
    var tmdbAccessToken by remember(extension.packageName) {
        mutableStateOf(tmdbPreferences.getString("read_access_token", "").orEmpty())
    }
    var browserSession by remember(extension.packageName) { mutableStateOf<SourceBrowserSession?>(null) }
    var browserBusySourceId by remember(extension.packageName) { mutableStateOf<String?>(null) }
    var browserError by remember(extension.packageName) { mutableStateOf<String?>(null) }

    val openSession = browserSession
    if (openSession != null) {
        SourceWebViewScreen(
            session = openSession,
            extension = extension,
            manager = manager,
            onBack = { browserSession = null },
        )
        return
    }

    fun openBrowserSession(sourceId: String, sourceName: String) {
        if (browserBusySourceId != null) return
        browserBusySourceId = sourceId
        browserError = null
        val payload = JSONObject()
            .put("sourceId", sourceId)
            .put("id", "")
            .toString()
        manager.call(extension, ExtensionSessionContract.METHOD_BROWSER_SESSION, payload) { result ->
            browserBusySourceId = null
            result.fold(
                onSuccess = { raw ->
                    val session = parseExtensionBrowserSession(
                        raw = raw,
                        sourceId = sourceId,
                        extensionPackage = extension.packageName,
                        fallbackTitle = sourceName,
                    )
                    if (session != null) {
                        browserSession = session
                    } else {
                        browserError = "$sourceName returned an invalid browser session."
                    }
                },
                onFailure = { error ->
                    browserError = error.message ?: "$sourceName could not open its browser session."
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(descriptor?.name ?: extension.declaredName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 10.dp)) {
            item { DenseRow("Package", extension.packageName, Icons.Rounded.Inventory2) }
            if (isRedditSource) {
                item {
                    RedditSourceConfiguration(
                        initialClientId = redditClientId,
                        onSave = { clientId ->
                            redditPreferences.edit().putString("client_id", clientId.trim()).apply()
                            redditClientId = clientId.trim()
                        },
                        onClear = {
                            redditPreferences.edit().remove("client_id").apply()
                            redditClientId = ""
                        },
                    )
                }
            }
            if (isLiveSource) {
                item {
                    TmdbSourceConfiguration(
                        initialToken = tmdbAccessToken,
                        onSave = { token ->
                            tmdbPreferences.edit().putString("read_access_token", token.trim()).apply()
                            tmdbAccessToken = token.trim()
                        },
                        onClear = {
                            tmdbPreferences.edit().remove("read_access_token").apply()
                            tmdbAccessToken = ""
                        },
                    )
                }
            }
            item { DenseRow("API", "${extension.apiVersion}", Icons.Rounded.Code) }
            descriptor?.let { d ->
                item { DenseRow("Version", d.version, Icons.Rounded.Update) }
                item { DenseRow("Content", d.contentTypes.sorted().joinToString(), Icons.Rounded.Category) }
                item { DenseRow("Capabilities", d.capabilities.sorted().joinToString(), Icons.Rounded.Bolt) }
                item { DenseRow("Permissions", if (d.permissions.isEmpty()) "None" else d.permissions.joinToString { it.type }, Icons.Rounded.Security) }
                items(d.sources.size) { index ->
                    val source = d.sources[index]
                    DenseRow(source.name, source.contentTypes.joinToString(), Icons.Rounded.Source)
                    if (ExtensionSessionContract.CAPABILITY_WEBVIEW in source.capabilities) {
                        DenseRow(
                            if (browserBusySourceId == source.id) "Opening ${source.name}…" else "Open ${source.name}",
                            "Sign in or refresh this source session in Sora's browser.",
                            Icons.Rounded.OpenInBrowser,
                            trailing = if (browserBusySourceId == source.id) "Opening" else "Browser",
                            onClick = { openBrowserSession(source.id, source.name) },
                        )
                    }
                }
            }
            browserError?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            }
            extension.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) } }
        }
    }
}

@Composable
private fun RedditSourceConfiguration(
    initialClientId: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var value by remember(initialClientId) { mutableStateOf(initialClientId) }
    var message by remember(initialClientId) {
        mutableStateOf(if (initialClientId.isBlank()) "Not configured" else "Reddit source configured")
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Reddit access", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create an installed app at reddit.com/prefs/apps and enter its public client ID. Do not enter a script or web-app secret.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; message = "Unsaved changes" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Installed-app client ID") },
                singleLine = true,
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = value.isNotBlank(),
                    onClick = { onSave(value); message = "Saved. Return to Memes and retry." },
                ) { Text("Save ID") }
                TextButton(onClick = { onClear(); value = ""; message = "Not configured" }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun TmdbSourceConfiguration(
    initialToken: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var value by remember(initialToken) { mutableStateOf(initialToken) }
    var reveal by remember { mutableStateOf(false) }
    var message by remember(initialToken) {
        mutableStateOf(if (initialToken.isBlank()) "Not configured" else "TMDB catalog configured")
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("TMDB movie catalog", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create a TMDB account and copy your API Read Access Token from themoviedb.org/settings/api. It stays in Sora's private app storage and is sent only to the TMDB catalog. This enables discovery and details; playback requires a separate source. TMDB data is provided by The Movie Database (TMDB).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; message = "Unsaved changes" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TMDB API Read Access Token") },
                singleLine = true,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide token" else "Show token")
                    }
                },
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = value.isNotBlank(),
                    onClick = { onSave(value); message = "Saved. Return to Movies and retry." },
                ) { Text("Save token") }
                TextButton(onClick = { onClear(); value = ""; message = "Not configured" }) { Text("Clear") }
            }
        }
    }
}

private fun parseExtensionBrowserSession(
    raw: String,
    sourceId: String,
    extensionPackage: String,
    fallbackTitle: String,
): SourceBrowserSession? = runCatching {
    val json = JSONObject(raw)
    val url = json.optString("url").trim()
    if (url.isBlank()) return@runCatching null
    val headersJson = json.optJSONObject("headers")
    val headers = buildMap {
        headersJson?.keys()?.forEach { key ->
            val value = headersJson.optString(key).trim()
            if (key.isNotBlank() && value.isNotBlank()) put(key, value)
        }
    }
    val scriptsJson = json.optJSONObject("sessionScripts")
    val sessionScripts = buildMap {
        scriptsJson?.keys()?.forEach { key ->
            val script = scriptsJson.optString(key).trim()
            if (key.isNotBlank() && script.isNotBlank()) put(key, script)
        }
    }
    SourceBrowserSession(
        sourceId = sourceId,
        extensionPackage = extensionPackage,
        url = url,
        title = json.optString("title").ifBlank { fallbackTitle },
        headers = headers,
        sessionScripts = sessionScripts,
    )
}.getOrNull()
