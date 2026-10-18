package com.night.tachymns

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Number is the number printed in the user's TAC hymnbook, NOT a third-party hymnal ID.
 * Draft verses must never be presented as verified just because an online title matches.
 */
data class Hymn(
    val number: Int,
    val collection: String,
    val titleYo: String,
    val titleEn: String?,
    val sourcePages: List<Int>,
    val versesYo: List<List<String>>,
    val versesEn: List<List<String>>,
    val textStatus: String,
    val sourceUrl: String?
) {
    val hasYoruba: Boolean get() = versesYo.isNotEmpty()
    val hasEnglish: Boolean get() = versesEn.isNotEmpty()

    fun matches(query: String): Boolean {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return true
        if (number.toString().contains(needle)) return true
        val corpus = buildString {
            append(titleYo)
            append(' ')
            append(titleEn.orEmpty())
            for (verse in versesYo) for (line in verse) { append(' '); append(line) }
            for (verse in versesEn) for (line in verse) { append(' '); append(line) }
        }
        return fold(corpus).contains(needle)
    }
}

private fun fold(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace("’", "'")
        .replace("‘", "'")
        .replace("ṣ", "s")
        .trim()

object HymnRepository {
    fun load(context: Context): List<Hymn> {
        val raw = context.assets.open("hymns.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val entries = JSONObject(raw).getJSONArray("hymns")
        return (0 until entries.length()).map { i ->
            val item = entries.getJSONObject(i)
            Hymn(
                number = item.getInt("number"),
                collection = item.optString("collection", "main"),
                titleYo = item.getString("title_yo"),
                titleEn = item.optString("title_en").ifBlank { null },
                sourcePages = item.optJSONArray("source_pages")?.intList().orEmpty(),
                versesYo = item.optJSONArray("verses_yo")?.verseList().orEmpty(),
                versesEn = item.optJSONArray("verses_en")?.verseList().orEmpty(),
                textStatus = item.optString("text_status", "missing"),
                sourceUrl = item.optString("source_url").ifBlank { null }
            )
        }.sortedBy { it.number }
            .also { hymns ->
                require(hymns.map { it.number }.distinct().size == hymns.size) {
                    "Duplicate TAC hymn numbers in the main collection"
                }
            }
    }

    private fun JSONArray.intList(): List<Int> =
        (0 until length()).map { i -> getInt(i) }

    private fun JSONArray.verseList(): List<List<String>> =
        (0 until length()).map { i ->
            val lines = getJSONArray(i)
            (0 until lines.length()).map { j -> lines.getString(j) }
        }
}
