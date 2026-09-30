package com.veya.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veya.app.VeyaApplication
import com.veya.app.download.VeyaDownloadManager
import com.veya.app.model.DownloadItem
import com.veya.app.model.DownloadStatus
import com.veya.app.model.ResolvedMedia
import com.veya.app.resolver.MediaResolver
import com.veya.app.resolver.ResolveResult
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Tab { Home, Downloads, Settings }

@Composable
fun VeyaApp(initialUrl: String) {
    VeyaTheme {
        val context = LocalContext.current
        val store = (context.applicationContext as VeyaApplication).downloads
        val downloads by store.items.collectAsState()
        var tab by remember { mutableStateOf(Tab.Home) }

        Scaffold(
            bottomBar = {
                NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                    NavigationBarItem(
                        selected = tab == Tab.Home,
                        onClick = { tab = Tab.Home },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Downloads,
                        onClick = { tab = Tab.Downloads },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        label = { Text("Downloads") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Settings,
                        onClick = { tab = Tab.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { innerPadding ->
            AnimatedContent(targetState = tab, label = "veya-tab") { current ->
                when (current) {
                    Tab.Home -> HomeScreen(
                        initialUrl = initialUrl,
                        downloads = downloads,
                        modifier = Modifier.padding(innerPadding),
                        openDownloads = { tab = Tab.Downloads }
                    )
                    Tab.Downloads -> DownloadsScreen(
                        downloads = downloads,
                        modifier = Modifier.padding(innerPadding)
                    )
                    Tab.Settings -> SettingsScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    initialUrl: String,
    downloads: List<DownloadItem>,
    modifier: Modifier,
    openDownloads: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val resolver = remember { MediaResolver() }
    val scope = rememberCoroutineScope()

    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var resolving by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ResolveResult?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    fun resolve() {
        val candidate = url.trim()
        if (candidate.isBlank() || resolving) return
        resolving = true
        result = null
        scope.launch {
            result = resolver.resolve(candidate)
            resolving = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Veya",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Fast downloads. Clean queue.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Drop a link",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            result = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        placeholder = { Text("https://…") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    clipboard.getText()?.text?.trim()?.let {
                                        url = it
                                        result = null
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste"
                                )
                            }
                        }
                    )

                    Button(
                        onClick = ::resolve,
                        enabled = url.isNotBlank() && !resolving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (resolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(10.dp))
                            Text("Checking…")
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Find media")
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = result != null) {
                when (val current = result) {
                    is ResolveResult.Success -> ResultGroup(
                        options = current.options,
                        onDownload = { media ->
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            VeyaDownloadManager.enqueue(context, media)
                            result = null
                            url = ""
                            openDownloads()
                        }
                    )
                    is ResolveResult.Error -> ErrorCard(current.message)
                    null -> Unit
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = openDownloads) {
                        Text("View all")
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item { EmptyState("Your downloads will appear here.") }
        } else {
            items(downloads.take(3), key = { it.id }) { item ->
                DownloadRow(item = item, compact = true)
            }
        }
    }
}

@Composable
private fun ResultGroup(
    options: List<ResolvedMedia>,
    onDownload: (ResolvedMedia) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (options.size == 1) "Ready to download" else "${options.size} files found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        options.forEach { media ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (media.mimeType.startsWith("video/")) {
                                        Icons.Default.VideoFile
                                    } else {
                                        Icons.Default.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.size(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = media.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = listOfNotNull(
                                    media.host.takeIf { it.isNotBlank() },
                                    humanSize(media.sizeBytes).takeIf { media.sizeBytes > 0 }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { onDownload(media) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun DownloadsScreen(
    downloads: List<DownloadItem>,
    modifier: Modifier,
) {
    var filter by remember { mutableStateOf("All") }
    val visible = remember(downloads, filter) {
        when (filter) {
            "Active" -> downloads.filter {
                it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.PAUSED
            }
            "Done" -> downloads.filter { it.status == DownloadStatus.COMPLETED }
            else -> downloads
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Active", "Done").forEach { name ->
                    AssistChip(
                        onClick = { filter = name },
                        label = {
                            Text(if (filter == name) "✓ $name" else name)
                        }
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item { EmptyState("Nothing here yet.") }
        } else {
            items(visible, key = { it.id }) { item ->
                DownloadRow(item = item, compact = false)
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    compact: Boolean,
) {
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(item.status)
                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusLine(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!compact) {
                    when (item.status) {
                        DownloadStatus.QUEUED,
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(
                                onClick = {
                                    VeyaDownloadManager.pause(context, item.id)
                                }
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(
                                onClick = {
                                    VeyaDownloadManager.resume(context, item.id)
                                }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }
                        }
                        DownloadStatus.FAILED,
                        DownloadStatus.CANCELLED -> {
                            IconButton(
                                onClick = {
                                    VeyaDownloadManager.retry(context, item.id)
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            }
                        }
                        DownloadStatus.COMPLETED -> Unit
                    }
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.QUEUED
            ) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!compact &&
                item.status == DownloadStatus.COMPLETED &&
                item.publicUri != null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(item.publicUri), item.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    ) {
                        Text("Open")
                    }
                }
            }

            if (!compact && item.status != DownloadStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { confirmCancel = true }) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Cancel")
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel download?") },
            text = { Text("You can retry it later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        VeyaDownloadManager.cancel(context, item.id)
                        confirmCancel = false
                    }
                ) {
                    Text("Cancel download")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
private fun StatusIcon(status: DownloadStatus) {
    val icon = when (status) {
        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
        DownloadStatus.FAILED -> Icons.Default.ErrorOutline
        DownloadStatus.PAUSED -> Icons.Default.Pause
        DownloadStatus.CANCELLED -> Icons.Default.Stop
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING -> Icons.Default.Download
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (status == DownloadStatus.FAILED) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    )
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("veya_settings", Context.MODE_PRIVATE)
    }
    var wifiOnly by remember {
        mutableStateOf(prefs.getBoolean("wifi_only", false))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wi-Fi only", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Wait for an unmetered network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            prefs.edit().putBoolean("wifi_only", it).apply()
                        }
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Storage",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (Build.VERSION.SDK_INT >= 29) {
                            "Completed files go to Downloads/Veya."
                        } else {
                            "Completed files stay in Veya's app download folder."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Privacy",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "No contacts, location, camera, microphone, overlay, app inventory, package install, or all-files access.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun statusLine(item: DownloadItem): String = when (item.status) {
    DownloadStatus.DOWNLOADING -> {
        if (item.totalBytes > 0) {
            "${humanSize(item.downloadedBytes)} of ${humanSize(item.totalBytes)}"
        } else {
            humanSize(item.downloadedBytes)
        }
    }
    DownloadStatus.COMPLETED -> "Saved"
    DownloadStatus.FAILED -> item.error ?: "Failed"
    DownloadStatus.PAUSED -> "Paused • ${humanSize(item.downloadedBytes)}"
    DownloadStatus.CANCELLED -> "Cancelled"
    DownloadStatus.QUEUED -> "Waiting to start"
}

private fun humanSize(bytes: Long): String {
    if (bytes < 0) return "Unknown size"
    if (bytes < 1024) return "$bytes B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var index = 0

    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }

    return String.format(
        Locale.US,
        if (value >= 10) "%.1f %s" else "%.2f %s",
        value,
        units[index]
    )
}
