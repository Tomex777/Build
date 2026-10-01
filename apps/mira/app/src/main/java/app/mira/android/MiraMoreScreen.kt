@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.mira.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mira.domain.ContentSearchResult

@Composable
internal fun MiraMoreScreen(onOpen: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mira") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(listOf("Downloads", "Sources & extensions", "History", "Statistics", "Settings", "About Mira")) { page ->
                ListItem(headlineContent = { Text(page) }, modifier = Modifier.clickable { onOpen(page) })
            }
        }
    }
}

@Composable
internal fun MiraInformationScreen(page: String, application: MiraApplication, onBack: () -> Unit, onOpen: (ContentSearchResult) -> Unit) {
    val watched by application.watchProgressStore.entries.collectAsState()
    val library by application.libraryStore.items.collectAsState()
    val context = LocalContext.current
    val settings = remember { MiraSettingsStore(context) }
    var incognito by remember { mutableStateOf(settings.incognito) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(page) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "Settings" -> item {
                    ListItem(headlineContent = { Text("Incognito") }, supportingContent = { Text("Pause saving watch history and resume progress") }, trailingContent = {
                        Switch(checked = incognito, onCheckedChange = { incognito = it; settings.incognito = it })
                    })
                }
                "History" -> {
                    if (watched.isEmpty()) item { Text("No watch history yet", Modifier.padding(16.dp)) }
                    items(watched, key = { it.identity.stableKey }) { progress ->
                        ListItem(headlineContent = { Text(progress.identity.title) }, supportingContent = { Text(progress.identity.subtitle.orEmpty()) }, modifier = Modifier.clickable { onOpen(progress.identity.asSearchResult()) })
                    }
                }
                "Statistics" -> {
                    item { ListItem(headlineContent = { Text("Library titles") }, trailingContent = { Text(library.size.toString()) }) }
                    item { ListItem(headlineContent = { Text("Watched items") }, trailingContent = { Text(watched.count { it.completed }.toString()) }) }
                    item { ListItem(headlineContent = { Text("In progress") }, trailingContent = { Text(watched.count { !it.completed && it.positionMs > 0L }.toString()) }) }
                }
                "About Mira" -> item {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Mira", style = MaterialTheme.typography.headlineSmall)
                        val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
                        Text("Version $version")
                        Text("Your movie and TV library")
                        Text("Playback powered by libVLC. libVLC is licensed under LGPL 2.1 or later.")
                    }
                }
            }
        }
    }
}
