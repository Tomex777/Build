package com.night.sora.download

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import com.night.sora.data.CoreRepository
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MusicDownloadManager(context: Context, private val repository: CoreRepository, private val manager: ExtensionManager) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    @Volatile private var extensions: List<InstalledExtension> = emptyList()
    private val root = File(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(app.filesDir, "music"), "downloads").apply { mkdirs() }

    init {
        repository.downloads.filter { it.contentType == ContentType.MUSIC && it.status == DownloadStatus.COMPLETED }
            .filter { it.filePath.isNullOrBlank() || File(it.filePath).let { f -> !f.isFile || f.length() <= 0L } }
            .map { it.id }.forEach(repository::removeDownload)
    }

    fun updateExtensions(value: List<InstalledExtension>) { extensions = value }
    fun canUseSource(track: ExtensionMediaSelection): Boolean {
        val selected = app.getSharedPreferences("sora_preferred_sources_v1", Context.MODE_PRIVATE)
            .getString("preferred_music", null)
            ?: return true
        return selected == "${track.extensionPackage}|${track.sourceId}"
    }
    fun entryFor(track: ExtensionMediaSelection) = repository.downloads.firstOrNull { it.id == id(track) }
    fun localFileFor(track: ExtensionMediaSelection): String? {
        val e = entryFor(track) ?: return null
        if (e.status != DownloadStatus.COMPLETED) return null
        val path = e.filePath ?: return null
        val f = File(path)
        return path.takeIf { f.isFile && f.length() > 0L }
    }

    fun download(track: ExtensionMediaSelection) {
        if (track.type != ContentType.MUSIC || !canUseSource(track)) return
        val key = id(track)
        if (localFileFor(track) != null || jobs[key]?.isActive == true) return
        val source = extensions.firstOrNull { it.packageName == track.extensionPackage }?.descriptor?.sources?.firstOrNull { it.id == track.sourceId }
        val seed = DownloadEntry(key, track.title, track.subtitle.ifBlank { "Track" }, ContentType.MUSIC, track.artworkUrl,
            source?.name.orEmpty(), track.id, track.sourceId, track.extensionPackage, track.subtitle, status = DownloadStatus.QUEUED)
        repository.upsertDownload(seed)
        jobs[key] = scope.launch { runDownload(track, seed) }.also { job -> job.invokeOnCompletion { jobs.remove(key, job) } }
    }

    fun retry(key: String) {
        val e = repository.downloads.firstOrNull { it.id == key } ?: return
        val mediaId = e.mediaId ?: return; val sourceId = e.sourceId ?: return; val pkg = e.extensionPackage ?: return
        download(ExtensionMediaSelection(mediaId, sourceId, pkg, ContentType.MUSIC, e.title, e.subtitle.ifBlank { e.itemLabel }, e.artworkUrl))
    }

    fun remove(key: String) {
        jobs.remove(key)?.cancel()
        repository.downloads.firstOrNull { it.id == key }?.filePath?.let { runCatching { File(it).delete() } }
        runCatching { part(key).delete() }
        repository.removeDownload(key)
    }
    fun remove(track: ExtensionMediaSelection) = remove(id(track))
    fun clearCompleted() = repository.downloads.filter { it.status == DownloadStatus.COMPLETED }.map { it.id }.forEach(::remove)

    private suspend fun runDownload(track: ExtensionMediaSelection, seed: DownloadEntry) {
        val temp = part(seed.id)
        try {
            publish(seed.copy(status = DownloadStatus.DOWNLOADING, bytesDownloaded = 0, totalBytes = 0, filePath = null))
            val ext = extensions.firstOrNull { it.packageName == track.extensionPackage } ?: error("Selected music extension is not installed")
            val src = ext.descriptor?.sources?.firstOrNull { it.id == track.sourceId } ?: error("Selected music source is unavailable")
            require("streams" in src.capabilities) { "Selected music source cannot resolve audio" }
            val stream = parse(request(ext, track)).firstOrNull() ?: error("Source returned no downloadable audio stream")
            val final = File(root, "${hash(seed.id)}.${suffix(stream)}")
            temp.delete(); final.delete()
            val c = (URL(stream.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true; connectTimeout = 20_000; readTimeout = 30_000; requestMethod = "GET"
                stream.headers.forEach { (k,v) -> setRequestProperty(k,v) }
            }
            try {
                c.connect(); if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                val total = c.contentLengthLong.coerceAtLeast(0); var got = 0L; var lastBytes = 0L; var lastAt = SystemClock.elapsedRealtime()
                c.inputStream.use { input -> FileOutputStream(temp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf,0,n); got += n
                        val now = SystemClock.elapsedRealtime()
                        if (got-lastBytes >= 256*1024 || now-lastAt >= 350) {
                            publish(seed.copy(status=DownloadStatus.DOWNLOADING, bytesDownloaded=got, totalBytes=total, mimeType=stream.mimeType, updatedAt=System.currentTimeMillis()))
                            lastBytes=got; lastAt=now
                        }
                    }
                    out.fd.sync()
                }}
                if (got <= 0) error("Empty audio file")
                if (!temp.renameTo(final)) { temp.copyTo(final, overwrite=true); temp.delete() }
                publish(seed.copy(status=DownloadStatus.COMPLETED, bytesDownloaded=got, totalBytes=if(total>0) total else got,
                    mimeType=stream.mimeType, filePath=final.absolutePath, updatedAt=System.currentTimeMillis()))
            } finally { c.disconnect() }
        } catch (e: CancellationException) { temp.delete(); throw e }
        catch (_: Throwable) {
            temp.delete(); val latest = repository.downloads.firstOrNull { it.id == seed.id } ?: seed
            publish(latest.copy(status=DownloadStatus.FAILED, filePath=null, updatedAt=System.currentTimeMillis()))
        }
    }

    private suspend fun request(ext: InstalledExtension, track: ExtensionMediaSelection): String = suspendCancellableCoroutine { cont ->
        manager.call(ext, ExtensionContract.Method.STREAMS, JSONObject().put("sourceId",track.sourceId).put("id",track.id).toString()) { r ->
            if (!cont.isActive) return@call
            r.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
        }
    }
    private suspend fun publish(e: DownloadEntry) = withContext(Dispatchers.Main.immediate) { repository.upsertDownload(e) }
    private fun parse(raw: String): List<PlaybackStream> = runCatching {
        val a = JSONArray(raw); buildList { for (i in 0 until a.length()) {
            val o=a.optJSONObject(i)?:continue; val url=o.optString("url"); if(!url.startsWith("http")) continue
            val headers=buildMap { o.optJSONObject("headers")?.let { h -> val keys=h.keys(); while(keys.hasNext()){ val k=keys.next(); h.optString(k).takeIf(String::isNotBlank)?.let{put(k,it)} } } }
            add(PlaybackStream(o.optString("label","Audio"),url,headers,o.optString("mimeType").takeIf(String::isNotBlank)))
        }}
    }.getOrDefault(emptyList())
    private fun suffix(s: PlaybackStream) = when(s.mimeType?.lowercase()) { "audio/mpeg","audio/mp3"->"mp3"; "audio/mp4","audio/aac","audio/x-m4a"->"m4a"; "audio/ogg"->"ogg"; "audio/webm"->"webm"; else->"audio" }
    private fun id(t: ExtensionMediaSelection) = "music:${t.extensionPackage}:${t.sourceId}:${t.id}"
    private fun part(key:String)=File(root,"${hash(key)}.part")
    private fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}.take(32)
}
