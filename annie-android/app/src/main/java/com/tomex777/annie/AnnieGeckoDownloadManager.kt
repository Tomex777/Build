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
import org.json.JSONArray
import org.json.JSONObject

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

/** Completed-file metadata only: never persist source URLs, request headers or cookies. */
internal class AnnieGeckoDownloadHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("annie_gecko_downloads_v1", Context.MODE_PRIVATE)

    fun read(): List<AnnieGeckoDownloadEntry> = try {
        val saved = JSONArray(prefs.getString("completed", "[]"))
        buildList {
            for (index in 0 until minOf(saved.length(), 75)) {
                val item = saved.optJSONObject(index) ?: continue
                val uri = item.optString("uri")
                val id = item.optString("id")
                if (id.isBlank() || Uri.parse(uri).scheme != "content") continue
                add(AnnieGeckoDownloadEntry(
                    id = id.take(100), name = item.optString("name").take(150),
                    mimeType = item.optString("mime", "application/octet-stream").take(150),
                    totalBytes = item.optLong("total", -1).takeIf { it > 0 },
                    bytesDone = item.optLong("bytes", 0).coerceAtLeast(0),
                    status = AnnieGeckoDownloadStatus.COMPLETED, contentUri = uri,
                ))
            }
        }
    } catch (problem: org.json.JSONException) {
        android.util.Log.w("AnnieGeckoDownloads", "Invalid saved download history", problem)
        emptyList()
    }

    fun save(entries: List<AnnieGeckoDownloadEntry>) {
        val saved = JSONArray()
        entries.asSequence().filter {
            it.status == AnnieGeckoDownloadStatus.COMPLETED &&
                it.contentUri?.let(Uri::parse)?.scheme == "content"
        }.take(75).forEach { item ->
            saved.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("mime", item.mimeType).put("total", item.totalBytes ?: -1)
                .put("bytes", item.bytesDone).put("uri", item.contentUri))
        }
        prefs.edit().putString("completed", saved.toString()).apply()
    }
}

/** Live Gecko response downloads plus persistent completed files; all UI updates run on main. */
internal object AnnieGeckoDownloads {
    private val handler = Handler(Looper.getMainLooper())
    private val entries = mutableStateListOf<AnnieGeckoDownloadEntry>()
    private var history: AnnieGeckoDownloadHistory? = null

    fun initialize(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (history != null) return
        val store = AnnieGeckoDownloadHistory(context)
        history = store
        val existing = entries.map { it.id }.toSet()
        entries.addAll(store.read().filterNot { it.id in existing })
    }
    private val cancellation = ConcurrentHashMap<String, () -> Unit>()
    // Cancellation is queried by IO workers; Compose's SnapshotStateList must
    // not be read from those threads for concurrency decisions.
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()

    val current: List<AnnieGeckoDownloadEntry> get() = entries

    private fun update(id: String, persist: Boolean = false, transform: (AnnieGeckoDownloadEntry) -> AnnieGeckoDownloadEntry) {
        handler.post {
            val index = entries.indexOfFirst { it.id == id }
            if (index >= 0) {
                entries[index] = transform(entries[index])
                if (persist) history?.save(entries)
            }
        }
    }

    fun started(id: String, name: String, mime: String, total: Long?) {
        cancelledIds.remove(id)
        handler.post {
            entries.add(0, AnnieGeckoDownloadEntry(id, name, mime, total))
            while (entries.size > 75) {
                val removable = entries.indexOfLast { it.status != AnnieGeckoDownloadStatus.DOWNLOADING }
                if (removable == -1) break
                val removed = entries.removeAt(removable)
                cancelledIds.remove(removed.id)
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
        update(id, persist = true) {
            if (cancelledIds.contains(id)) it
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

    /** Remove a finished item from the panel without deleting the saved file. */
    fun forget(id: String) {
        handler.post {
            val entry = entries.firstOrNull { it.id == id } ?: return@post
            if (entry.status == AnnieGeckoDownloadStatus.DOWNLOADING) return@post
            entries.remove(entry)
            cancelledIds.remove(id)
            history?.save(entries)
        }
    }

    fun isCancelled(id: String): Boolean = cancelledIds.contains(id)

    fun cancelled(id: String) {
        cancellation.remove(id)
        cancelledIds.add(id)
        update(id) { it.copy(status = AnnieGeckoDownloadStatus.CANCELLED) }
    }

    fun cancel(id: String) {
        val action = cancellation.remove(id) ?: return
        cancelledIds.add(id) // Set first, before coroutine cancellation races IO.
        update(id) { it.copy(status = AnnieGeckoDownloadStatus.CANCELLED) }
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
            Text("Saved browser downloads remain available after reopening Annie.",
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
                if (download.status != AnnieGeckoDownloadStatus.DOWNLOADING) {
                    TextButton(onClick = { AnnieGeckoDownloads.forget(download.id) },
                        modifier = Modifier.testTag("annie_gecko_download_forget_${download.id}")) {
                        Text("Remove from list")
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
