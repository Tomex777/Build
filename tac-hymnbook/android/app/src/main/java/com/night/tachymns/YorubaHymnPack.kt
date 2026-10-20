package com.night.tachymns

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * Imports a user-provided private Yorùbá Redemption lyric pack.
 *
 * The app contains no external scraping or hidden translations: content comes
 * from a user-selected JSON file and stays in the app-private directory.
 * A full 800-hymn pack is supported without Internet access.
 */
data class YorubaPackEntry(
    val number: Int,
    val title: String,
    val verses: List<List<String>>
)

data class YorubaImportResult(
    val count: Int,
    val replaced: Int
)

object YorubaHymnPack {
    private const val FILE_NAME = "private_redemption_yoruba.json"
    private const val MAX_BYTES = 4 * 1024 * 1024

    fun read(context: Context): Map<Int, YorubaPackEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return emptyMap()
        return parse(file.readText(Charsets.UTF_8))
    }

    fun importFromUri(context: Context, uri: Uri): YorubaImportResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytesLimited(MAX_BYTES)
        } ?: throw IllegalArgumentException("Unable to open selected file")
        val raw = bytes.toString(Charsets.UTF_8)
        return importText(context, raw)
    }

    /**
     * Checked, atomic import. Bad input cannot wipe an existing offline pack.
     * One file can include hymns 1–800; subsequent imports replace only the
     * explicitly supplied hymn numbers and leave previous imports intact.
     */
    fun importText(context: Context, input: String): YorubaImportResult {
        require(input.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Yorùbá hymn file is larger than 4 MB"
        }
        val incoming = parse(input)
        require(incoming.isNotEmpty()) { "No Yorùbá hymn verses found in file" }
        val existing = read(context)
        val merged = existing.toMutableMap().apply { putAll(incoming) }
        val array = org.json.JSONArray()
        merged.toSortedMap().values.forEach { hymn ->
            val row = JSONObject()
            row.put("number", hymn.number)
            row.put("title_yo", hymn.title)
            val verses = org.json.JSONArray()
            hymn.verses.forEach { stanza ->
                val lines = org.json.JSONArray()
                stanza.forEach(lines::put)
                verses.put(lines)
            }
            row.put("verses_yo", verses)
            array.put(row)
        }
        val payload = JSONObject()
            .put("schema_version", 1)
            .put("collection", "main")
            .put("hymns", array)
        val dest = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            temp.writeText(payload.toString(), Charsets.UTF_8)
            require(temp.renameTo(dest)) { "Cannot save imported hymns" }
        } finally {
            temp.delete()
        }
        return YorubaImportResult(incoming.size, incoming.keys.count(existing::containsKey))
    }

    fun combine(
        base: List<Hymn>,
        redemptionIndex: List<HymnIndexEntry>,
        pack: Map<Int, YorubaPackEntry>
    ): List<Hymn> {
        val byKey = base.associateBy { it.key }.toMutableMap()
        val known = redemptionIndex.filter { it.collection == "main" }.associateBy { it.number }
        pack.values.forEach { added ->
            val key = "main:${added.number}"
            val original = byKey[key]
            val index = known[added.number]
            byKey[key] = if (original == null) {
                Hymn(
                    number = added.number,
                    collection = "main",
                    titleYo = added.title,
                    titleEn = index?.titleEn,
                    sourcePages = emptyList(),
                    versesYo = added.verses,
                    versesEn = emptyList(),
                    textStatus = "user_private_import",
                    sourceUrl = null
                )
            } else {
                original.copy(
                    titleYo = added.title,
                    versesYo = added.verses,
                    textStatus = "user_private_import"
                )
            }
        }
        return byKey.values.sortedWith(
            compareBy<Hymn>({ if (it.collection == "akokun") 1 else 0 }, { it.number })
        )
    }

    /** Independent bilingual index; imported Yorùbá first lines take priority. */
    fun overlayIndex(
        base: List<HymnIndexEntry>,
        pack: Map<Int, YorubaPackEntry>
    ): List<HymnIndexEntry> =
        base.map { entry ->
            if (entry.collection == "main" && pack.containsKey(entry.number)) {
                entry.copy(titleYo = pack.getValue(entry.number).title)
            } else entry
        }

    /** Schema: {"schema_version":1,"collection":"main","hymns":[
     * {"number":123,"title_yo":"...","verses_yo":[["line"],["line"]]} ]} */
    fun parse(input: String): Map<Int, YorubaPackEntry> {
        require(input.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "File exceeds 4 MB" }
        val root = JSONObject(input)
        require(root.optInt("schema_version") == 1) { "Unsupported Yorùbá hymn pack version" }
        require(root.optString("collection") == "main") {
            "Only Redemption 1–800 can be imported here"
        }
        val arr = root.getJSONArray("hymns")
        require(arr.length() <= 800) { "Too many Redemption hymn entries" }
        val out = linkedMapOf<Int, YorubaPackEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            require(obj.has("number") && obj.get("number") is Int) {
                "Invalid hymn number at entry ${i+1}"
            }
            val number = obj.getInt("number")
            require(number in 1..800 && number !in out) {
                "Duplicate or invalid Redemption hymn number $number"
            }
            val title = requireLine(obj.getString("title_yo"))
            val stanzas = obj.getJSONArray("verses_yo")
            require(stanzas.length() in 1..30) { "Hymn $number has no valid verses" }
            val verses = (0 until stanzas.length()).map { j ->
                val stanza = stanzas.getJSONArray(j)
                require(stanza.length() in 1..32) { "Invalid stanza in hymn $number" }
                (0 until stanza.length()).map { k ->
                    requireLine(stanza.getString(k))
                }
            }
            out[number] = YorubaPackEntry(number, title, verses)
        }
        return out
    }

    private fun requireLine(value: String): String {
        val clean = value.trim()
        require(clean.isNotEmpty() && clean.length <= 500) {
            "A Yorùbá hymn line is missing or too long"
        }
        require(clean.none { it.code < 32 }) {
            "Split stanzas into individual lines instead of embedded line breaks"
        }
        require(Normalizer.isNormalized(clean, Normalizer.Form.NFC)) {
            "Yorùbá hymn text must be Unicode NFC"
        }
        return clean
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = read(buffer)
            if (n == -1) break
            require(out.size() + n <= limit) { "Yorùbá hymn file is larger than 4 MB" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
