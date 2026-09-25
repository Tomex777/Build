package app.nami.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamiDownloadsScreen(
    downloadManager: NamiDownloadManager,
    onBack: () -> Unit,
) {
    val statuses by downloadManager.statuses.collectAsState()
    val context = LocalContext.current
    val downloads = statuses.values.sortedWith(
        compareBy<NamiDownloadStatus>(
            { it.extensionName.lowercase() },
            { it.animeTitle.lowercase() },
            { it.episodeTitle.lowercase() },
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No downloads yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(
                    items = downloads,
                    key = { downloadManager.key(it.sourceId, it.sourceEpisodeId) },
                ) { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = status.state == NamiDownloadState.DOWNLOADED,
                                onClick = { downloadManager.openDownloaded(context, status) },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DownloadStateIcon(status)

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = status.episodeTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = status.extensionName + " • " + status.animeTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (status.state == NamiDownloadState.DOWNLOADING) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { status.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                )
                            }
                            status.errorMessage?.takeIf { status.state == NamiDownloadState.ERROR }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (status.state == NamiDownloadState.ERROR) {
                            IconButton(onClick = { downloadManager.retry(status) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Retry download",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStateIcon(status: NamiDownloadStatus) {
    when (status.state) {
        NamiDownloadState.QUEUED -> Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = "Queued",
            modifier = Modifier.size(24.dp),
        )
        NamiDownloadState.DOWNLOADING -> CircularProgressIndicator(
            progress = { status.progress / 100f },
            modifier = Modifier.size(24.dp),
            strokeWidth = 3.dp,
        )
        NamiDownloadState.DOWNLOADED -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Downloaded",
            modifier = Modifier.size(24.dp),
        )
        NamiDownloadState.ERROR -> Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
    }
}
