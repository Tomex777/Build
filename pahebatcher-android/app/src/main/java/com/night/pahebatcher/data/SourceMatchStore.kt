package com.night.pahebatcher.data

import android.content.Context
import org.json.JSONObject

data class SourceMatch(
    val aniListId: Int,
    val animeId: Int,
    val session: String,
    val host: String,
    val sourceTitle: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

class SourceMatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("pahe_source_matches", Context.MODE_PRIVATE)

    fun get(aniListId: Int): SourceMatch? {
        if (aniListId <= 0) return null
        val raw = prefs.getString("match_$aniListId", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SourceMatch(
                aniListId = aniListId,
                animeId = json.optInt("animeId", 0),
                session = json.optString("session"),
                host = json.optString("host"),
                sourceTitle = json.optString("sourceTitle"),
                updatedAt = json.optLong("updatedAt", 0L),
            )
        }.getOrNull()?.takeIf {
            it.animeId > 0 && it.session.isNotBlank() && it.host.isNotBlank()
        }
    }

    fun put(match: SourceMatch) {
        if (match.aniListId <= 0 || match.animeId <= 0 || match.session.isBlank()) return
        val json = JSONObject()
            .put("animeId", match.animeId)
            .put("session", match.session)
            .put("host", match.host)
            .put("sourceTitle", match.sourceTitle)
            .put("updatedAt", match.updatedAt)
        prefs.edit().putString("match_${match.aniListId}", json.toString()).apply()
    }
}
