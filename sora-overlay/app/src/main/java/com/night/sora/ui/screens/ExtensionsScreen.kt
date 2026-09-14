@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.night.sora.extension.InstalledExtension
import com.night.sora.ui.components.DenseRow

@Composable
fun ExtensionsScreen(
    extensions: List<InstalledExtension>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (InstalledExtension) -> Unit,
) {
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
            if (extensions.isEmpty()) {
                item { DenseRow("No compatible extensions", "Sora Core is running by itself.", Icons.Rounded.ExtensionOff) }
            }
            items(extensions.size) { index ->
                val extension = extensions[index]
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
    val descriptor = extension.descriptor
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
            item { DenseRow("API", "${extension.apiVersion}", Icons.Rounded.Code) }
            descriptor?.let { d ->
                item { DenseRow("Version", d.version, Icons.Rounded.Update) }
                item { DenseRow("Content", d.contentTypes.sorted().joinToString(), Icons.Rounded.Category) }
                item { DenseRow("Capabilities", d.capabilities.sorted().joinToString(), Icons.Rounded.Bolt) }
                item { DenseRow("Permissions", if (d.permissions.isEmpty()) "None" else d.permissions.joinToString { it.type }, Icons.Rounded.Security) }
                items(d.sources.size) { index ->
                    val source = d.sources[index]
                    DenseRow(source.name, source.contentTypes.joinToString(), Icons.Rounded.Source)
                }
            }
            extension.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) } }
        }
    }
}
