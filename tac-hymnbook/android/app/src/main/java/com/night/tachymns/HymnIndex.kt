package com.night.tachymns

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * First-line indexes are useful even before every hymn is transcribed.
 * Index-only records are never substituted for full lyric records.
 */
data class HymnIndexEntry(
    val collection: String,
    val number: Int,
    val titleYo: String?,
    val titleEn: String?,
    val alternateLines: List<String>
) {
    val key: String get() = "$collection:$number"
    val displayTitle: String get() = titleYo ?: titleEn ?: ""

    fun matches(query: String): Boolean {
        val term = normalize(query.trim())
        return term.isEmpty() || number.toString().contains(term) ||
            listOfNotNull(titleYo, titleEn).any { normalize(it).contains(term) } ||
            alternateLines.any { normalize(it).contains(term) }
    }
}

private fun normalize(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('’', '\'')
        .replace('‘', '\'')

object IndexRepository {
    fun load(context: Context): List<HymnIndexEntry> {
        val redemption = JSONObject(
            context.assets.open("redemption_index.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        ).getJSONArray("entries")
        val akokun = JSONObject(
            context.assets.open("akokun_index.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        ).getJSONArray("hymns")

        val all = ArrayList<HymnIndexEntry>(485)
        for (i in 0 until redemption.length()) {
            val item = redemption.getJSONObject(i)
            val english = item.stringOrNull("title_en")
            val yoruba = item.stringOrNull("title_yo")
            // Never publish empty number slots as if they had hymn content.
            if (english.isNullOrBlank() && yoruba.isNullOrBlank()) continue
            all.add(HymnIndexEntry(
                collection = "main",
                number = item.getInt("number"),
                titleYo = yoruba,
                titleEn = english,
                alternateLines = emptyList()
            ))
        }
        for (i in 0 until akokun.length()) {
            val item = akokun.getJSONObject(i)
            val lines = item.getJSONArray("opening_lines")
            val aliases = (0 until lines.length()).map { j ->
                lines.getJSONObject(j).getString("text")
            }.distinct()
            val title = item.stringOrNull("title_yo")
            if (title.isNullOrBlank()) continue
            all.add(HymnIndexEntry(
                collection = "akokun",
                number = item.getInt("number"),
                titleYo = title,
                titleEn = null,
                alternateLines = aliases.filter { it != title }
            ))
        }
        require(all.map { it.key }.distinct().size == all.size) {
            "Overlapping index numbers must remain unique within their own collection."
        }
        return all.sortedWith(compareBy({ if (it.collection == "akokun") 1 else 0 }, { it.number }))
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).trim().ifBlank { null }
    }
}
