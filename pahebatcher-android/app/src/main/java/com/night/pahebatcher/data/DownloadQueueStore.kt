package com.night.pahebatcher.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject

enum class DownloadState {
    QUEUED,
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
}

data class PersistentDownload(
    val id: String,
    val animeTitle: String,
    val episodeNumber: Double,
    val episodeSession: String,
    val episodeTitle: String,
    val fansub: String,
    val sourceAudio: String,
    val playUrl: String,
    val requestedQuality: Int,
    val requestedAudio: String,
    val progress: Float = 0f,
    val status: String = "Queued",
    val state: DownloadState = DownloadState.QUEUED,
    val uri: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

class DownloadQueueStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(): List<PersistentDownload> =
        prefs.all.entries
            .asSequence()
            .filter { it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { (_, value) -> (value as? String)?.let(::decode) }
            .sortedByDescending { it.updatedAt }
            .toList()

    fun get(id: String): PersistentDownload? =
        prefs.getString(KEY_PREFIX + id, null)?.let(::decode)

    fun put(record: PersistentDownload) {
        prefs.edit()
            .putString(KEY_PREFIX + record.id, encode(record.copy(updatedAt = System.currentTimeMillis())))
            .apply()
    }

    fun update(
        id: String,
        progress: Float? = null,
        status: String? = null,
        state: DownloadState? = null,
        uri: Uri? = null,
    ) {
        val current = get(id) ?: return
        put(
            current.copy(
                progress = progress?.coerceIn(0f, 1f) ?: current.progress,
                status = status ?: current.status,
                state = state ?: current.state,
                uri = uri?.toString() ?: current.uri,
            )
        )
    }

    fun remove(id: String) {
        prefs.edit().remove(KEY_PREFIX + id).apply()
    }

    fun preferences() = prefs

    private fun encode(record: PersistentDownload): String = JSONObject()
        .put("id", record.id)
        .put("animeTitle", record.animeTitle)
        .put("episodeNumber", record.episodeNumber)
        .put("episodeSession", record.episodeSession)
        .put("episodeTitle", record.episodeTitle)
        .put("fansub", record.fansub)
        .put("sourceAudio", record.sourceAudio)
        .put("playUrl", record.playUrl)
        .put("requestedQuality", record.requestedQuality)
        .put("requestedAudio", record.requestedAudio)
        .put("progress", record.progress.toDouble())
        .put("status", record.status)
        .put("state", record.state.name)
        .put("uri", record.uri)
        .put("updatedAt", record.updatedAt)
        .toString()

    private fun decode(raw: String): PersistentDownload? = runCatching {
        val json = JSONObject(raw)
        PersistentDownload(
            id = json.getString("id"),
            animeTitle = json.getString("animeTitle"),
            episodeNumber = json.getDouble("episodeNumber"),
            episodeSession = json.getString("episodeSession"),
            episodeTitle = json.optString("episodeTitle"),
            fansub = json.optString("fansub"),
            sourceAudio = json.optString("sourceAudio", "jpn"),
            playUrl = json.getString("playUrl"),
            requestedQuality = json.optInt("requestedQuality", 1080),
            requestedAudio = json.optString("requestedAudio", "jpn"),
            progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
            status = json.optString("status", "Queued"),
            state = runCatching {
                DownloadState.valueOf(json.optString("state", DownloadState.QUEUED.name))
            }.getOrDefault(DownloadState.QUEUED),
            uri = json.optString("uri"),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }.getOrNull()

    companion object {
        const val PREFS_NAME = "pahe_download_queue"
        private const val KEY_PREFIX = "record_"
    }
}
