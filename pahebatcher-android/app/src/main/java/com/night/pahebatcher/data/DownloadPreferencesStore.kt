package com.night.pahebatcher.data

import android.content.Context
import java.util.Locale

data class DownloadPreferences(
    val quality: Int = 1080,
    val audio: String = "jpn",
    val parallelDownloads: Int = 2,
)

class DownloadPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("pahe_download_preferences", Context.MODE_PRIVATE)

    fun global(): DownloadPreferences = DownloadPreferences(
        quality = sanitizeQuality(prefs.getInt(KEY_GLOBAL_QUALITY, 1080)),
        audio = sanitizeAudio(prefs.getString(KEY_GLOBAL_AUDIO, "jpn")),
        parallelDownloads = sanitizeParallel(prefs.getInt(KEY_PARALLEL_DOWNLOADS, 2)),
    )

    fun setGlobalQuality(quality: Int) {
        prefs.edit().putInt(KEY_GLOBAL_QUALITY, sanitizeQuality(quality)).apply()
    }

    fun setGlobalAudio(audio: String) {
        prefs.edit().putString(KEY_GLOBAL_AUDIO, sanitizeAudio(audio)).apply()
    }

    fun setParallelDownloads(value: Int) {
        prefs.edit().putInt(KEY_PARALLEL_DOWNLOADS, sanitizeParallel(value)).apply()
    }

    fun overrideFor(anime: AnimeSearchResult): DownloadPreferences? {
        val key = animeKey(anime)
        if (!prefs.getBoolean("${key}_enabled", false)) return null
        val fallback = global()
        return DownloadPreferences(
            quality = sanitizeQuality(prefs.getInt("${key}_quality", fallback.quality)),
            audio = sanitizeAudio(prefs.getString("${key}_audio", fallback.audio)),
            parallelDownloads = fallback.parallelDownloads,
        )
    }

    fun saveOverride(anime: AnimeSearchResult, value: DownloadPreferences) {
        val key = animeKey(anime)
        prefs.edit()
            .putBoolean("${key}_enabled", true)
            .putInt("${key}_quality", sanitizeQuality(value.quality))
            .putString("${key}_audio", sanitizeAudio(value.audio))
            .apply()
    }

    fun clearOverride(anime: AnimeSearchResult) {
        val key = animeKey(anime)
        prefs.edit()
            .remove("${key}_enabled")
            .remove("${key}_quality")
            .remove("${key}_audio")
            .apply()
    }

    private fun animeKey(anime: AnimeSearchResult): String {
        anime.aniListId?.let { return "anilist_${it}" }
        anime.animeId?.let { return "anime_${it}" }
        val normalized = anime.title
            .lowercase(Locale.US)
            .replace(Regex("""[^\p{L}\p{N}]+"""), "_")
            .trim('_')
            .take(80)
        return "title_${normalized.ifBlank { anime.session.take(24) }}"
    }

    private fun sanitizeQuality(value: Int): Int =
        when (value) {
            1080, 720, 360 -> value
            else -> 1080
        }

    private fun sanitizeAudio(value: String?): String =
        if (value.equals("eng", true)) "eng" else "jpn"

    private fun sanitizeParallel(value: Int): Int =
        when (value) {
            1, 2, 4, 6, 8 -> value
            else -> 2
        }

    companion object {
        private const val KEY_GLOBAL_QUALITY = "global_quality"
        private const val KEY_GLOBAL_AUDIO = "global_audio"
        private const val KEY_PARALLEL_DOWNLOADS = "parallel_downloads"
    }
}
