package com.nexa.echospace.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Private device storage; no login, storage permission or network access. */
class SceneStore(context: Context) {
    private val prefs = context.getSharedPreferences("echospace_scene", Context.MODE_PRIVATE)

    fun restore(): Pair<String, List<SoundSource>>? {
        return try {
            val raw = prefs.getString("scene", null) ?: return null
            val json = JSONObject(raw)
            val entries = json.getJSONArray("sources")
            val sources = buildList {
                for (i in 0 until entries.length().coerceAtMost(16)) {
                    val item = entries.getJSONObject(i)
                    add(SoundSource(
                        id = item.getInt("id"),
                        kind = SoundKind.valueOf(item.getString("kind")),
                        x = item.getDouble("x").toFloat().coerceIn(-1f, 1f),
                        y = item.getDouble("y").toFloat().coerceIn(-1f, 1f),
                        gain = item.optDouble("gain", 0.8).toFloat().coerceIn(0f, 1f),
                        muted = item.optBoolean("muted", false)
                    ))
                }
            }
            json.optString("title", "My Soundscape") to sources
        } catch (_: Exception) { null }
    }

    /** A separate user-saved mix survives preset switching and process death. */
    fun restoreSavedMix(): List<SoundSource>? {
        val json = prefs.getString("saved_mix", null) ?: return null
        return parseSources(json)
    }

    fun saveMyMix(sources: List<SoundSource>) {
        prefs.edit().putString("saved_mix", encodeSources(sources).toString()).apply()
    }

    private fun parseSources(raw: String): List<SoundSource>? = try {
        val items = JSONArray(raw)
        buildList {
            for (i in 0 until items.length().coerceAtMost(12)) {
                val entry = items.getJSONObject(i)
                add(SoundSource(
                    entry.getInt("id"), SoundKind.valueOf(entry.getString("kind")),
                    entry.getDouble("x").toFloat().coerceIn(-1f, 1f),
                    entry.getDouble("y").toFloat().coerceIn(-1f, 1f),
                    entry.optDouble("gain", 0.8).toFloat().coerceIn(0f, 1f),
                    entry.optBoolean("muted", false)
                ))
            }
        }
    } catch (_: Exception) { null }

    private fun encodeSources(sources: List<SoundSource>): JSONArray = JSONArray().apply {
        sources.take(12).forEach { source ->
            put(JSONObject().apply {
                put("id", source.id)
                put("kind", source.kind.name)
                put("x", source.x.toDouble())
                put("y", source.y.toDouble())
                put("gain", source.gain.toDouble())
                put("muted", source.muted)
            })
        }
    }

    fun volume(): Float = prefs.getFloat("volume", 0.76f).coerceIn(0f, 1f)
    fun living(): Boolean = prefs.getBoolean("living", true)
    fun setLiving(living: Boolean) { prefs.edit().putBoolean("living", living).apply() }
    fun heading(): Float = prefs.getFloat("heading", 0f).coerceIn(-180f, 180f)

    fun save(title: String, sources: List<SoundSource>, volume: Float, heading: Float) {
        val array = encodeSources(sources)
        val json = JSONObject().put("title", title).put("sources", array)
        prefs.edit()
            .putString("scene", json.toString())
            .putFloat("volume", volume.coerceIn(0f, 1f))
            .putFloat("heading", heading.coerceIn(-180f, 180f))
            .apply()
    }
}
