package app.mira.android

import android.content.Context
import app.mira.domain.ContentKind
import app.mira.domain.ContentRef
import app.mira.domain.ContentSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class MiraPlaybackIdentity(
    val sourceId: String,
    val contentId: String,
    val episodeId: String? = null,
    val kind: ContentKind,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val sourceState: String? = null,
    val episodeSourceState: String? = null,
) {
    val stableKey: String
        get() = sourceId + "\u0000" + contentId + "\u0000" + episodeId.orEmpty()

    fun asSearchResult(): ContentSearchResult = ContentSearchResult(
        ref = ContentRef(
            sourceId = sourceId,
            sourceContentId = contentId,
            kind = kind,
        ),
        title = title,
        posterUrl = posterUrl,
        description = subtitle,
        sourceState = sourceState,
    )
}

data class MiraWatchProgress(
    val identity: MiraPlaybackIdentity,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastWatchedAtEpochMillis: Long,
)

class MiraWatchProgressStore(
    context: Context,
) {
    private val settings = MiraSettingsStore(context)
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableEntries = MutableStateFlow(load())
    val entries: StateFlow<List<MiraWatchProgress>> = mutableEntries.asStateFlow()

    fun get(identity: MiraPlaybackIdentity): MiraWatchProgress? =
        mutableEntries.value.firstOrNull { it.identity.stableKey == identity.stableKey }

    fun continueWatching(limit: Int = 20): List<MiraWatchProgress> =
        mutableEntries.value
            .asSequence()
            .filter { !it.completed && it.positionMs > 0L }
            .sortedByDescending { it.lastWatchedAtEpochMillis }
            .take(limit.coerceAtLeast(1))
            .toList()

    fun save(
        identity: MiraPlaybackIdentity,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (settings.incognito) return
        val position = positionMs.coerceAtLeast(0L)
        val duration = durationMs.coerceAtLeast(0L)
        if (position <= 0L && duration <= 0L) return

        val updated = MiraWatchProgress(
            identity = identity,
            positionMs = position,
            durationMs = duration,
            completed = isCompleted(position, duration),
            lastWatchedAtEpochMillis = System.currentTimeMillis(),
        )
        val next = (mutableEntries.value.filterNot {
            it.identity.stableKey == identity.stableKey
        } + updated).sortedByDescending { it.lastWatchedAtEpochMillis }
        mutableEntries.value = next
        persist(next)
    }

    fun remove(identity: MiraPlaybackIdentity) {
        val next = mutableEntries.value.filterNot {
            it.identity.stableKey == identity.stableKey
        }
        mutableEntries.value = next
        persist(next)
    }

    private fun load(): List<MiraWatchProgress> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val kind = runCatching {
                        ContentKind.valueOf(json.getString("kind"))
                    }.getOrNull() ?: continue
                    val sourceId = json.optString("sourceId").trim()
                    val contentId = json.optString("contentId").trim()
                    val title = json.optString("title").trim()
                    if (sourceId.isBlank() || contentId.isBlank() || title.isBlank()) continue
                    add(
                        MiraWatchProgress(
                            identity = MiraPlaybackIdentity(
                                sourceId = sourceId,
                                contentId = contentId,
                                episodeId = json.optNullableString("episodeId"),
                                kind = kind,
                                title = title,
                                subtitle = json.optNullableString("subtitle"),
                                posterUrl = json.optNullableString("posterUrl"),
                                sourceState = json.optNullableString("sourceState"),
                                episodeSourceState = json.optNullableString("episodeSourceState"),
                            ),
                            positionMs = json.optLong("positionMs").coerceAtLeast(0L),
                            durationMs = json.optLong("durationMs").coerceAtLeast(0L),
                            completed = json.optBoolean("completed", false),
                            lastWatchedAtEpochMillis =
                                json.optLong("lastWatchedAtEpochMillis").coerceAtLeast(0L),
                        ),
                    )
                }
            }.sortedByDescending { it.lastWatchedAtEpochMillis }
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<MiraWatchProgress>) {
        val array = JSONArray()
        entries.forEach { progress ->
            array.put(
                JSONObject().apply {
                    put("sourceId", progress.identity.sourceId)
                    put("contentId", progress.identity.contentId)
                    put("episodeId", progress.identity.episodeId)
                    put("kind", progress.identity.kind.name)
                    put("title", progress.identity.title)
                    put("subtitle", progress.identity.subtitle)
                    put("posterUrl", progress.identity.posterUrl)
                    put("sourceState", progress.identity.sourceState)
                    put("episodeSourceState", progress.identity.episodeSourceState)
                    put("positionMs", progress.positionMs)
                    put("durationMs", progress.durationMs)
                    put("completed", progress.completed)
                    put("lastWatchedAtEpochMillis", progress.lastWatchedAtEpochMillis)
                },
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val remaining = (durationMs - positionMs).coerceAtLeast(0L)
        return positionMs >= durationMs ||
            remaining <= 60_000L ||
            positionMs.toDouble() / durationMs.toDouble() >= 0.95
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private const val PREFERENCES_NAME = "mira_watch_progress"
        private const val KEY_ENTRIES = "entries"
    }
}
