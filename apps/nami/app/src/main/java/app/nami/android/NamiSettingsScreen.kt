@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nami.android

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nami.runtime.NamiSourceRegistry
import app.nami.runtime.SourceEnablementStore
import app.nami.source.NamiAnimeSource

@Composable
internal fun NamiSettingsScreen(
    installedSourceRegistry: NamiSourceRegistry,
    sourceEnablementStore: SourceEnablementStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var sources by remember { mutableStateOf<List<NamiAnimeSource>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val enabled = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(installedSourceRegistry) {
        runCatching { installedSourceRegistry.installedSources() }
            .onSuccess { loaded ->
                val sorted = loaded.sortedWith(
                    compareBy<NamiAnimeSource> { it.metadata.name.lowercase() }
                        .thenBy { it.metadata.language.orEmpty() },
                )
                sources = sorted
                enabled.clear()
                sorted.forEach { source ->
                    enabled[source.metadata.id] =
                        sourceEnablementStore.isEnabled(source.metadata.id)
                }
                loadError = null
            }
            .onFailure { failure ->
                sources = emptyList()
                loadError = failure.message ?: "Unable to load installed extensions."
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            sources == null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            loadError != null -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(loadError.orEmpty())
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Extensions",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Only enabled extensions participate in global search and runtime work.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }

                    items(
                        items = sources.orEmpty(),
                        key = { it.metadata.id },
                    ) { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                            ) {
                                Text(
                                    text = source.metadata.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val secondary = buildList {
                                    source.metadata.language
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let(::add)
                                    source.metadata.extensionVersion
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { add("v$it") }
                                    source.metadata.extensionApiVersion
                                        ?.let { add("API $it") }
                                }.joinToString(" • ")
                                if (secondary.isNotBlank()) {
                                    Text(
                                        text = secondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (source.metadata.capabilities.configurable) {
                                IconButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                context,
                                                AniyomiSourcePreferencesActivity::class.java,
                                            ).putExtra(
                                                AniyomiSourcePreferencesActivity.EXTRA_SOURCE_ID,
                                                source.metadata.id,
                                            ),
                                        )
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.Settings,
                                        contentDescription = "Source settings",
                                    )
                                }
                            }

                            Switch(
                                checked = enabled[source.metadata.id] ?: true,
                                onCheckedChange = { checked ->
                                    enabled[source.metadata.id] = checked
                                    sourceEnablementStore.setEnabled(
                                        source.metadata.id,
                                        checked,
                                    )
                                },
                            )
                        }
                        HorizontalDivider()
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Downloads",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Saved under Movies/Nami/<Extension>/<Anime>/…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Up to 2 downloads per extension run at once; additional episodes stay queued.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Downloaded episodes play in Nami's built-in VLC player.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
