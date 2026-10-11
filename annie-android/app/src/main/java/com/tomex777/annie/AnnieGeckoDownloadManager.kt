package com.tomex777.annie

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

internal enum class AnnieGeckoDownloadStatus {
    DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

internal data class AnnieGeckoDownloadEntry(
    val id: String,
    val name: String,
    val mimeType: String,
    val totalBytes: Long?,
    val bytesDone: Long = 0,
    val status: AnnieGeckoDownloadStatus = AnnieGeckoDownloadStatus.DOWNLOADING,
    val contentUri: String? = null,
    val reason: String? = null,
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0L }
            ?.let { (bytesDone.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}

/**
 * In-process download registry for Gecko response streams. This is a live activity
 * panel, not a persisted system download history; completed files remain on disk.
 * UI state is always updated on the Android main thread.
 */
internal object AnnieGeckoDownloads {
    private val handler = Handler(Looper.getMainLooper())
    private val entries = mutableStateListOf<AnnieGeckoDownloadEntry>()
    private val cancellation = ConcurrentHashMap<String, () -> Unit>()

    val current: List<AnnieGeckoDownloadEntry> get() = entries

    private fun update(id: String, transform: (AnnieGeckoDownloadEntry) -> AnnieGeckoDownloadEntry) {
        handler.post {
            val index = entries.indexOfFirst { it.id == id }
            if (index >= 0) entries[index] = transform(entries[index])
        }
    }

    fun started(id: String, name: String, mime: String, total: Long?) {
        handler.post {
            entries.add(0, AnnieGeckoDownloadEntry(id, name, mime, total))
            while (entries.size > 75) {
                val removable = entries.indexOfLast { it.status != AnnieGeckoDownloadStatus.DOWNLOADING }
                if (removable == -1) break
                entries.removeAt(removable)
            }
        }
    }

    fun track(id: String, cancel: () -> Unit) {
        cancellation[id] = cancel
    }

    fun untrack(id: String) { cancellation.remove(id) }

    fun progress(id: String, bytes: Long) {
        update(id) {
            if (it.status != AnnieGeckoDownloadStatus.DOWNLOADING) it
            else it.copy(bytesDone = bytes.coerceAtLeast(it.bytesDone))
        }
    }

    fun completed(id: String, uri: Uri) {
        cancellation.remove(id)
        update(id) {
            if (it.status == AnnieGeckoDownloadStatus.CANCELLED) it
            else it.copy(status = AnnieGeckoDownloadStatus.COMPLETED,
                bytesDone = maxOf(it.bytesDone, it.totalBytes ?: 0L),
                contentUri = uri.toString())
        }
    }

    fun failed(id: String, problem: String) {
        cancellation.remove(id)
        update(id) {
            if (it.status == AnnieGeckoDownloadStatus.CANCELLED) it
            else it.copy(status = AnnieGeckoDownloadStatus.FAILED, reason = problem.take(160))
        }
    }

    fun isCancelled(id: String): Boolean =
        entries.firstOrNull { it.id == id }?.status == AnnieGeckoDownloadStatus.CANCELLED

    fun cancelled(id: String) {
        cancellation.remove(id)
        update(id) { it.copy(status = AnnieGeckoDownloadStatus.CANCELLED) }
    }

    fun cancel(id: String) {
        val action = cancellation.remove(id) ?: return
        cancelled(id)
        action()
    }
}

@Composable
internal fun AnnieGeckoDownloadPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tasks = AnnieGeckoDownloads.current
    if (tasks.isEmpty()) {
        Column(modifier.padding(18.dp)) {
            Text("No downloads yet", color = BrowserBrightText)
            Text("Files downloaded in this browser session will appear here.",
                color = BrowserSoftText)
        }
        return
    }
    LazyColumn(modifier.testTag("annie_gecko_downloads")) {
        items(tasks, key = { it.id }) { download ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(download.name, color = BrowserBrightText)
                val details = when (download.status) {
                    AnnieGeckoDownloadStatus.DOWNLOADING -> {
                        val total = download.totalBytes
                        if (total != null) "${download.bytesDone / 1024} / ${total / 1024} KB"
                        else "${download.bytesDone / 1024} KB downloaded"
                    }
                    AnnieGeckoDownloadStatus.COMPLETED -> "Saved"
                    AnnieGeckoDownloadStatus.FAILED -> "Failed: ${download.reason.orEmpty()}"
                    AnnieGeckoDownloadStatus.CANCELLED -> "Cancelled"
                }
                Text(details, color = BrowserSoftText)
                if (download.status == AnnieGeckoDownloadStatus.DOWNLOADING) {
                    val fraction = download.progress
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    TextButton(
                        onClick = { AnnieGeckoDownloads.cancel(download.id) },
                        modifier = Modifier.testTag("annie_gecko_download_cancel_${download.id}"),
                    ) { Text("Cancel") }
                } else if (download.status == AnnieGeckoDownloadStatus.COMPLETED) {
                    download.contentUri?.let { uri ->
                        TextButton(
                            onClick = { openAnnieGeckoDownload(context, uri, download.mimeType) },
                            modifier = Modifier.testTag("annie_gecko_download_open_${download.id}"),
                        ) { Text("Open") }
                    }
                }
            }
        }
    }
}

private fun openAnnieGeckoDownload(context: Context, rawUri: String, mime: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(rawUri), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file type.", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Unable to share this downloaded file.", Toast.LENGTH_LONG).show()
    }
}
