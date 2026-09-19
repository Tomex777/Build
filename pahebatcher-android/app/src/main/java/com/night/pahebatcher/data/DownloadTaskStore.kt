package com.night.pahebatcher.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

data class StoredDownloadTask(
    val id: String,
    val workId: String,
    val animeTitle: String,
    val aniListId: Int = 0,
    val sourceQueries: List<String> = emptyList(),
    val sourceAnimeId: Int = 0,
    val sourceAnimeSession: String = "",
    val episodeNumber: Double,
    val episodeSession: String,
    val episodeTitle: String,
    val episodeFansub: String,
    val episodeAudio: String,
    val playUrl: String,
    val requestedQuality: Int,
    val requestedAudio: String,
    val resolvedUrl: String = "",
    val resolvedUserAgent: String = "",
    val resolvedReferer: String = "",
    val resolvedQuality: Int = 0,
    val resolvedAudio: String = "",
    val resolvedFansub: String = "",
    val resolvedAt: Long = 0L,
    val progress: Float = 0f,
    val status: String = "Queued",
    val state: String = STATE_QUEUED,
    val manualPaused: Boolean = false,
    val uri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun episode(): EpisodeInfo = EpisodeInfo(
        number = episodeNumber,
        session = episodeSession,
        title = episodeTitle,
        fansub = episodeFansub,
        audio = episodeAudio,
        playUrl = playUrl,
    )

    fun catalog(): AnimeSearchResult = AnimeSearchResult(
        session = sourceAnimeSession,
        title = animeTitle,
        poster = "",
        type = "",
        episodes = 0,
        status = "",
        animeId = sourceAnimeId.takeIf { it > 0 },
        aniListId = aniListId.takeIf { it > 0 },
        sourceQueries = sourceQueries.ifEmpty { listOf(animeTitle) },
    )

    fun resolvedStreamIfFresh(now: Long = System.currentTimeMillis()): StreamInfo? {
        if (resolvedUrl.isBlank() || resolvedAt <= 0L) return null
        if (now - resolvedAt > RESOLVED_TTL_MS) return null
        return StreamInfo(
            url = resolvedUrl,
            cookie = "",
            userAgent = resolvedUserAgent.ifBlank { SessionStore.DEFAULT_UA },
            referer = resolvedReferer,
            quality = resolvedQuality.takeIf { it > 0 } ?: requestedQuality,
            audio = resolvedAudio.ifBlank { requestedAudio },
            fansub = resolvedFansub,
        )
    }

    companion object {
        const val STATE_QUEUED = "queued"
        const val STATE_RUNNING = "running"
        const val STATE_PAUSED = "paused"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"
        const val RESOLVED_TTL_MS = 72L * 60L * 60L * 1000L
    }
}

class DownloadTaskStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("pahe_download_tasks", Context.MODE_PRIVATE)

    @Synchronized
    fun put(task: StoredDownloadTask) {
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids += task.id
        prefs.edit()
            .putString(KEY_TASK_PREFIX + task.id, task.toJson().toString())
            .putStringSet(KEY_IDS, ids)
            .apply()
    }

    @Synchronized
    fun get(id: String): StoredDownloadTask? {
        val raw = prefs.getString(KEY_TASK_PREFIX + id, null) ?: return null
        return runCatching { JSONObject(raw).toTask() }.getOrNull()
    }

    @Synchronized
    fun all(): List<StoredDownloadTask> =
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
            .mapNotNull(::get)
            .sortedByDescending { it.createdAt }

    @Synchronized
    fun update(id: String, transform: (StoredDownloadTask) -> StoredDownloadTask): StoredDownloadTask? {
        val current = get(id) ?: return null
        val updated = transform(current)
        put(updated)
        return updated
    }

    fun markRunning(id: String, status: String) =
        update(id) {
            it.copy(
                state = StoredDownloadTask.STATE_RUNNING,
                manualPaused = false,
                status = status,
            )
        }

    fun markProgress(id: String, progress: Float, status: String) =
        update(id) {
            it.copy(
                state = StoredDownloadTask.STATE_RUNNING,
                progress = progress.coerceIn(0f, 1f),
                status = status,
            )
        }

    fun markPaused(id: String, status: String, manual: Boolean = false) =
        update(id) {
            it.copy(
                state = StoredDownloadTask.STATE_PAUSED,
                manualPaused = manual,
                status = status,
            )
        }

    fun markFailed(id: String, status: String) =
        update(id) { it.copy(state = StoredDownloadTask.STATE_FAILED, status = status) }

    fun markCompleted(id: String, uri: Uri) =
        update(id) {
            it.copy(
                state = StoredDownloadTask.STATE_COMPLETED,
                progress = 1f,
                status = "Saved to Downloads/PaheBatcher",
                uri = uri.toString(),
            )
        }

    fun saveEpisodeSource(id: String, episode: EpisodeInfo) =
        update(id) {
            it.copy(
                episodeSession = episode.session,
                episodeTitle = episode.title,
                episodeFansub = episode.fansub,
                episodeAudio = episode.audio,
                playUrl = episode.playUrl,
            )
        }

    fun updateWorkId(id: String, workId: String, status: String = "Queued") =
        update(id) {
            it.copy(
                workId = workId,
                state = StoredDownloadTask.STATE_QUEUED,
                manualPaused = false,
                status = status,
            )
        }

    fun saveResolved(id: String, stream: StreamInfo) =
        update(id) {
            it.copy(
                resolvedUrl = stream.url,
                resolvedUserAgent = stream.userAgent,
                resolvedReferer = stream.referer,
                resolvedQuality = stream.quality,
                resolvedAudio = stream.audio,
                resolvedFansub = stream.fansub,
                resolvedAt = System.currentTimeMillis(),
            )
        }

    fun clearResolved(id: String) =
        update(id) {
            it.copy(
                resolvedUrl = "",
                resolvedUserAgent = "",
                resolvedReferer = "",
                resolvedQuality = 0,
                resolvedAudio = "",
                resolvedFansub = "",
                resolvedAt = 0L,
            )
        }

    @Synchronized
    fun remove(id: String) {
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids -= id
        prefs.edit()
            .remove(KEY_TASK_PREFIX + id)
            .putStringSet(KEY_IDS, ids)
            .apply()
        File(context.filesDir, "pahe_downloads/$id").deleteRecursively()
    }

    private fun StoredDownloadTask.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("workId", workId)
        .put("animeTitle", animeTitle)
        .put("aniListId", aniListId)
        .put("sourceQueries", org.json.JSONArray(sourceQueries))
        .put("sourceAnimeId", sourceAnimeId)
        .put("sourceAnimeSession", sourceAnimeSession)
        .put("episodeNumber", episodeNumber)
        .put("episodeSession", episodeSession)
        .put("episodeTitle", episodeTitle)
        .put("episodeFansub", episodeFansub)
        .put("episodeAudio", episodeAudio)
        .put("playUrl", playUrl)
        .put("requestedQuality", requestedQuality)
        .put("requestedAudio", requestedAudio)
        .put("resolvedUrl", resolvedUrl)
        .put("resolvedUserAgent", resolvedUserAgent)
        .put("resolvedReferer", resolvedReferer)
        .put("resolvedQuality", resolvedQuality)
        .put("resolvedAudio", resolvedAudio)
        .put("resolvedFansub", resolvedFansub)
        .put("resolvedAt", resolvedAt)
        .put("progress", progress.toDouble())
        .put("status", status)
        .put("state", state)
        .put("manualPaused", manualPaused)
        .put("uri", uri)
        .put("createdAt", createdAt)

    private fun JSONObject.toTask(): StoredDownloadTask = StoredDownloadTask(
        id = optString("id"),
        workId = optString("workId"),
        animeTitle = optString("animeTitle"),
        aniListId = optInt("aniListId", 0),
        sourceQueries = optJSONArray("sourceQueries")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.orEmpty(),
        sourceAnimeId = optInt("sourceAnimeId", 0),
        sourceAnimeSession = optString("sourceAnimeSession"),
        episodeNumber = optDouble("episodeNumber", 0.0),
        episodeSession = optString("episodeSession"),
        episodeTitle = optString("episodeTitle"),
        episodeFansub = optString("episodeFansub"),
        episodeAudio = optString("episodeAudio", "jpn"),
        playUrl = optString("playUrl"),
        requestedQuality = optInt("requestedQuality", 1080),
        requestedAudio = optString("requestedAudio", "jpn"),
        resolvedUrl = optString("resolvedUrl"),
        resolvedUserAgent = optString("resolvedUserAgent"),
        resolvedReferer = optString("resolvedReferer"),
        resolvedQuality = optInt("resolvedQuality", 0),
        resolvedAudio = optString("resolvedAudio"),
        resolvedFansub = optString("resolvedFansub"),
        resolvedAt = optLong("resolvedAt", 0L),
        progress = optDouble("progress", 0.0).toFloat(),
        status = optString("status", "Queued"),
        state = optString("state", StoredDownloadTask.STATE_QUEUED),
        manualPaused = optBoolean("manualPaused", false),
        uri = optString("uri"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
    )

    companion object {
        private const val KEY_IDS = "ids"
        private const val KEY_TASK_PREFIX = "task_"
    }
}
