package com.night.browser

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit, local PDF captures. This does not save website HTML, session state,
 * scripts, cookies, or an interactive offline webpage.
 */
data class TimeMachineVersion(
    val id: String,
    val url: String,
    val title: String,
    val fileName: String,
    val capturedAt: Long,
    val sizeBytes: Long
)

internal object TimeMachineLimits {
    const val MAX_VERSIONS = 120
    const val MAX_BYTES = 150L * 1024L * 1024L
    const val MAX_PDF_BYTES = 30L * 1024L * 1024L

    fun canAdd(count: Int, usedBytes: Long, incomingBytes: Long): Boolean =
        incomingBytes in 1..MAX_PDF_BYTES &&
            count < MAX_VERSIONS &&
            usedBytes <= MAX_BYTES - incomingBytes

    fun pageKey(url: String): String =
        url.trim().substringBefore('#').removeSuffix("/")
}

/** Owns archive metadata and PDF file lifecycle. Never performs an automatic capture. */
class TimeMachineArchive(context: Context) {
    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, "saved-pages")
    private val prefs = appContext.getSharedPreferences("night_time_machine_v2", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val captureBusy = AtomicBoolean(false)
    private val validName = Regex("[A-Za-z0-9._-]{1,100}\\.pdf")

    var versions by mutableStateOf(readVersions())
        private set

    init {
        // Import the prototype's PDF copies once. Never re-import something deleted here.
        if (!prefs.getBoolean("legacy_migrated", false)) {
            val seen = versions.mapTo(mutableSetOf()) { it.fileName }
            val migrated = runCatching {
                val oldPrefs = appContext.getSharedPreferences("night_data_v1", Context.MODE_PRIVATE)
                val items = JSONArray(oldPrefs.getString("pages", "[]"))
                (0 until items.length()).mapNotNull { i ->
                    val item = items.optJSONObject(i) ?: return@mapNotNull null
                    val name = item.optString("file")
                    if (!isSafeFileName(name) || !seen.add(name)) return@mapNotNull null
                    TimeMachineVersion(
                        id = name,
                        url = item.optString("url"),
                        title = item.optString("title"),
                        fileName = name,
                        capturedAt = item.optLong("time"),
                        sizeBytes = resolveFile(name)?.takeIf { it.exists() }?.length() ?: 0
                    )
                }
            }.getOrDefault(emptyList())
            versions = (versions + migrated).sortedByDescending { it.capturedAt }
            persist(migrated = true)
        }
        recoverInterruptedCaptures()
    }

    /**
     * A process can die after the atomic PDF rename and before metadata is
     * committed. Expose such complete files instead of silently losing them.
     * Incomplete .part files are never valid captures and may be discarded.
     */
    private fun recoverInterruptedCaptures() {
        val entries = folder.listFiles() ?: return
        val known = versions.mapTo(mutableSetOf()) { it.fileName }
        val rescued = entries.asSequence()
            .filter { it.isFile && it.name.startsWith("page-") &&
                isSafeFileName(it.name) && known.add(it.name) &&
                it.length() > 0L }
            .map { file ->
                TimeMachineVersion(
                    id = UUID.randomUUID().toString(),
                    url = "",
                    title = "Recovered PDF copy",
                    fileName = file.name,
                    capturedAt = file.lastModified(),
                    sizeBytes = file.length()
                )
            }.toList()
        if (rescued.isNotEmpty()) {
            versions = (versions + rescued).sortedByDescending { it.capturedAt }
            persist()
        }
        entries.filter { it.isFile && it.name.startsWith("page-") &&
            it.name.endsWith(".pdf.part") }.forEach { it.delete() }
    }

    /** Count actual files, including complete orphan copies awaiting recovery. */
    val usedBytes: Long get() = folder.listFiles()?.asSequence()
        ?.filter { it.isFile && isSafeFileName(it.name) }
        ?.sumOf { it.length() } ?: 0L

    fun health(item: TimeMachineVersion): ArchiveFileHealth =
        TimeMachinePdfHealth.inspect(resolveFile(item.fileName), item.sizeBytes)

    fun fileForPreview(item: TimeMachineVersion): File? =
        resolveFile(item.fileName)?.takeIf {
            TimeMachinePdfHealth.inspect(it, item.sizeBytes) == ArchiveFileHealth.READY
        }

    fun exists(item: TimeMachineVersion): Boolean =
        health(item) == ArchiveFileHealth.READY

    /**
     * A fresh capture always becomes a new version, never overwrites an older one.
     * On all failures the partial file is deleted and no metadata is committed.
     */
    fun savePdf(
        session: GeckoSession,
        url: String,
        title: String,
        isPrivate: Boolean,
        onNotice: (String) -> Unit
    ) {
        if (isPrivate) {
            onNotice("Private tabs cannot be saved to Time Machine.")
            return
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            onNotice("Open a webpage before saving a PDF copy.")
            return
        }
        if (versions.size >= TimeMachineLimits.MAX_VERSIONS ||
            usedBytes >= TimeMachineLimits.MAX_BYTES
        ) {
            onNotice("Time Machine storage is full. Delete an older PDF copy first.")
            return
        }
        // A second tap cannot race the first copy through the storage quota.
        if (!captureBusy.compareAndSet(false, true)) {
            onNotice("A PDF copy is already being saved.")
            return
        }
        // Gecko's PDF capture is explicitly initiated from the browser page menu.
        runCatching {
            session.saveAsPdf().accept({ input ->
                Thread {
                    val name = "page-" + System.currentTimeMillis() + "-" +
                        UUID.randomUUID().toString().take(8) + ".pdf"
                    val finalFile = File(folder, name)
                    val part = File(folder, name + ".part")
                    var message: String
                    try {
                        check(folder.isDirectory || folder.mkdirs()) { "Archive directory unavailable" }
                        val bytes = requireNotNull(input) { "Empty PDF stream" }.use { source ->
                            FileOutputStream(part).use { target ->
                                val buffer = ByteArray(32 * 1024)
                                var copied = 0L
                                while (true) {
                                    val n = source.read(buffer)
                                    if (n < 0) break
                                    copied += n
                                    check(copied <= TimeMachineLimits.MAX_PDF_BYTES) {
                                        "This PDF exceeds the 30 MB file limit"
                                    }
                                    target.write(buffer, 0, n)
                                }
                                target.fd.sync()
                                copied
                            }
                        }
                        check(bytes > 0) { "Empty PDF" }
                        check(TimeMachinePdfHealth.inspect(part, bytes) == ArchiveFileHealth.READY) {
                            "The saved PDF was incomplete or unreadable"
                        }
                        // Count files already saved, not only previously cached UI state.
                        val used = usedBytes
                        check(TimeMachineLimits.canAdd(versions.size, used, bytes)) {
                            "Archive storage limit reached"
                        }
                        check(part.renameTo(finalFile)) { "Could not finalize PDF" }
                        val item = TimeMachineVersion(
                            id = UUID.randomUUID().toString(),
                            url = url,
                            title = title.ifBlank { url }.take(240),
                            fileName = name,
                            capturedAt = System.currentTimeMillis(),
                            sizeBytes = bytes
                        )
                        main.post {
                            val previous = versions
                            versions = (listOf(item) + versions).sortedByDescending { it.capturedAt }
                            if (!persist()) {
                                // Never report success when the metadata write was rejected.
                                versions = previous
                                // Keep the complete file for recovery on next launch.
                                captureBusy.set(false)
                                onNotice("PDF captured, but its record could not be saved. Reopen Time Machine to recover it.")
                            } else {
                                captureBusy.set(false)
                                onNotice("PDF version saved in Time Machine.")
                            }
                        }
                        return@Thread
                    } catch (e: Exception) {
                        message = if (e.message?.contains("limit", ignoreCase = true) == true ||
                            e.message?.contains("exceeds", ignoreCase = true) == true
                        ) "Time Machine storage limit reached. Delete older copies." else
                            "Couldn't save the PDF copy."
                    } finally {
                        part.delete()
                    }
                    finalFile.delete()
                    main.post {
                        captureBusy.set(false)
                        onNotice(message)
                    }
                }.start()
            }, {
                captureBusy.set(false)
                onNotice("Couldn't create a PDF copy of this page.")
            })
        }.onFailure {
            captureBusy.set(false)
            onNotice("Couldn't create a PDF copy of this page.")
        }
    }

    /** Remove an archive entry only when its backing file was removed or is missing. */
    fun delete(item: TimeMachineVersion): Boolean {
        val file = resolveFile(item.fileName) ?: return false
        if (file.exists() && !file.delete()) return false
        versions = versions.filterNot { it.id == item.id }
        persist()
        return true
    }

    /** SAF export: the user chooses the destination. The original is retained. */
    fun exportToUri(item: TimeMachineVersion, destination: Uri, onNotice: (String) -> Unit) {
        val file = fileForPreview(item)
        if (file == null) {
            onNotice("This PDF is missing or damaged. You can delete its record.")
            return
        }
        Thread {
            val success = runCatching {
                val stream = requireNotNull(appContext.contentResolver.openOutputStream(destination)) {
                    "Destination cannot be opened"
                }
                file.inputStream().use { input -> stream.use { output -> input.copyTo(output) } }
            }.isSuccess
            main.post {
                onNotice(if (success) "PDF exported." else "Couldn't export this PDF.")
            }
        }.start()
    }

    fun suggestedFileName(item: TimeMachineVersion): String {
        val readable = item.title.ifBlank { "NIGHT-page" }
            .replace(Regex("[^A-Za-z0-9_-]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "NIGHT-page" }
        return readable + "-" + item.capturedAt + ".pdf"
    }

    private fun isSafeFileName(name: String): Boolean = validName.matches(name)

    private fun resolveFile(name: String): File? =
        if (isSafeFileName(name)) File(folder, name) else null

    private fun readVersions(): List<TimeMachineVersion> = runCatching {
        val data = JSONArray(prefs.getString("versions", "[]"))
        (0 until data.length()).mapNotNull { i ->
            val item = data.optJSONObject(i) ?: return@mapNotNull null
            val file = item.optString("file")
            if (!isSafeFileName(file)) return@mapNotNull null
            TimeMachineVersion(
                id = item.optString("id", file),
                url = item.optString("url"),
                title = item.optString("title"),
                fileName = file,
                capturedAt = item.optLong("time"),
                sizeBytes = item.optLong("bytes")
            )
        }.sortedByDescending { it.capturedAt }
    }.getOrDefault(emptyList())

    private fun persist(migrated: Boolean? = null): Boolean {
        val array = JSONArray()
        versions.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("url", item.url)
                .put("title", item.title).put("file", item.fileName)
                .put("time", item.capturedAt).put("bytes", item.sizeBytes))
        }
        val edit = prefs.edit().putString("versions", array.toString())
        if (migrated != null) edit.putBoolean("legacy_migrated", migrated)
        return edit.commit()
    }
}
