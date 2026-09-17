package com.night.sora.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Core-owned Bible reader state. Scripture is fetched on demand and cached by chapter for offline rereads. */
class BibleRepository(context: Context) {
    private val prefs = context.getSharedPreferences("sora_bible", Context.MODE_PRIVATE)

    fun loadChapter(book: BibleBook, chapter: Int, forceRefresh: Boolean = false): Result<BiblePassage> = runCatching {
        require(chapter in 1..book.chapters) { "Chapter is outside ${book.name}." }
        val cacheKey = chapterCacheKey(book.name, chapter)
        if (!forceRefresh) {
            prefs.getString(cacheKey, null)?.let { cached ->
                return@runCatching parsePassage(cached, fromCache = true)
            }
        }

        val raw = fetchReference("${book.name} $chapter")
        prefs.edit().putString(cacheKey, raw).apply()
        parsePassage(raw, fromCache = false)
    }

    fun lookup(reference: String): Result<BiblePassage> = runCatching {
        val clean = reference.trim()
        require(clean.isNotBlank()) { "Enter a Bible reference." }
        parsePassage(fetchReference(clean), fromCache = false)
    }

    fun lastReading(): BibleReadingPosition? {
        val raw = prefs.getString(KEY_LAST_READING, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val book = obj.getString("book")
            val chapter = obj.getInt("chapter")
            val verse = obj.optInt("verse", 1).coerceAtLeast(1)
            BibleReadingPosition(book, chapter, verse)
        }.getOrNull()
    }

    fun saveLastReading(position: BibleReadingPosition) {
        prefs.edit().putString(
            KEY_LAST_READING,
            JSONObject()
                .put("book", position.book)
                .put("chapter", position.chapter)
                .put("verse", position.verse)
                .toString(),
        ).apply()
    }

    fun bookmarks(): List<BibleBookmark> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        BibleBookmark(
                            key = item.getString("key"),
                            book = item.getString("book"),
                            chapter = item.getInt("chapter"),
                            verse = item.getInt("verse"),
                            text = item.getString("text"),
                            savedAt = item.optLong("savedAt", 0L),
                        )
                    )
                }
            }.sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())
    }

    fun toggleBookmark(verse: BibleVerse): List<BibleBookmark> {
        val current = bookmarks().toMutableList()
        val key = bookmarkKey(verse.book, verse.chapter, verse.number)
        val index = current.indexOfFirst { it.key == key }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(
                0,
                BibleBookmark(
                    key = key,
                    book = verse.book,
                    chapter = verse.chapter,
                    verse = verse.number,
                    text = verse.text,
                    savedAt = System.currentTimeMillis(),
                ),
            )
        }
        persistBookmarks(current)
        return current.sortedByDescending { it.savedAt }
    }

    fun isBookmarked(verse: BibleVerse, bookmarks: List<BibleBookmark>): Boolean =
        bookmarks.any { it.key == bookmarkKey(verse.book, verse.chapter, verse.number) }

    private fun persistBookmarks(entries: List<BibleBookmark>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("book", entry.book)
                    .put("chapter", entry.chapter)
                    .put("verse", entry.verse)
                    .put("text", entry.text)
                    .put("savedAt", entry.savedAt)
            )
        }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    private fun fetchReference(reference: String): String {
        val encoded = URLEncoder.encode(reference, StandardCharsets.UTF_8.toString())
        val url = "https://bible-api.com/$encoded?translation=kjv&single_chapter_book_matching=indifferent"
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Sora/0.1 Android Bible Reader")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                error(message.ifBlank { "Bible service returned HTTP $code." })
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePassage(raw: String, fromCache: Boolean): BiblePassage {
        val root = JSONObject(raw)
        val error = root.optString("error")
        if (error.isNotBlank()) error(error)
        val array = root.optJSONArray("verses") ?: JSONArray()
        val verses = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val text = item.optString("text").trim()
                if (text.isBlank()) continue
                add(
                    BibleVerse(
                        book = item.optString("book_name").ifBlank { "Bible" },
                        chapter = item.optInt("chapter", 1),
                        number = item.optInt("verse", i + 1),
                        text = text,
                    )
                )
            }
        }
        if (verses.isEmpty()) error("No verses were returned for this reference.")
        return BiblePassage(
            reference = root.optString("reference").ifBlank {
                val first = verses.first()
                "${first.book} ${first.chapter}"
            },
            translation = root.optString("translation_name").ifBlank { "King James Version" },
            verses = verses,
            fromCache = fromCache,
        )
    }

    companion object {
        private const val KEY_LAST_READING = "last_reading"
        private const val KEY_BOOKMARKS = "bookmarks"

        private fun chapterCacheKey(book: String, chapter: Int): String =
            "chapter_kjv_${book.lowercase().replace(Regex("[^a-z0-9]+"), "_")}_$chapter"

        private fun bookmarkKey(book: String, chapter: Int, verse: Int): String =
            "${book.trim().lowercase()}:$chapter:$verse"
    }
}

data class BibleBook(
    val name: String,
    val chapters: Int,
    val testament: Testament,
)

enum class Testament { OLD, NEW }

data class BibleVerse(
    val book: String,
    val chapter: Int,
    val number: Int,
    val text: String,
)

data class BiblePassage(
    val reference: String,
    val translation: String,
    val verses: List<BibleVerse>,
    val fromCache: Boolean,
)

data class BibleReadingPosition(
    val book: String,
    val chapter: Int,
    val verse: Int = 1,
)

data class BibleBookmark(
    val key: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val savedAt: Long,
)

val SoraBibleBooks: List<BibleBook> = listOf(
    BibleBook("Genesis", 50, Testament.OLD),
    BibleBook("Exodus", 40, Testament.OLD),
    BibleBook("Leviticus", 27, Testament.OLD),
    BibleBook("Numbers", 36, Testament.OLD),
    BibleBook("Deuteronomy", 34, Testament.OLD),
    BibleBook("Joshua", 24, Testament.OLD),
    BibleBook("Judges", 21, Testament.OLD),
    BibleBook("Ruth", 4, Testament.OLD),
    BibleBook("1 Samuel", 31, Testament.OLD),
    BibleBook("2 Samuel", 24, Testament.OLD),
    BibleBook("1 Kings", 22, Testament.OLD),
    BibleBook("2 Kings", 25, Testament.OLD),
    BibleBook("1 Chronicles", 29, Testament.OLD),
    BibleBook("2 Chronicles", 36, Testament.OLD),
    BibleBook("Ezra", 10, Testament.OLD),
    BibleBook("Nehemiah", 13, Testament.OLD),
    BibleBook("Esther", 10, Testament.OLD),
    BibleBook("Job", 42, Testament.OLD),
    BibleBook("Psalms", 150, Testament.OLD),
    BibleBook("Proverbs", 31, Testament.OLD),
    BibleBook("Ecclesiastes", 12, Testament.OLD),
    BibleBook("Song of Solomon", 8, Testament.OLD),
    BibleBook("Isaiah", 66, Testament.OLD),
    BibleBook("Jeremiah", 52, Testament.OLD),
    BibleBook("Lamentations", 5, Testament.OLD),
    BibleBook("Ezekiel", 48, Testament.OLD),
    BibleBook("Daniel", 12, Testament.OLD),
    BibleBook("Hosea", 14, Testament.OLD),
    BibleBook("Joel", 3, Testament.OLD),
    BibleBook("Amos", 9, Testament.OLD),
    BibleBook("Obadiah", 1, Testament.OLD),
    BibleBook("Jonah", 4, Testament.OLD),
    BibleBook("Micah", 7, Testament.OLD),
    BibleBook("Nahum", 3, Testament.OLD),
    BibleBook("Habakkuk", 3, Testament.OLD),
    BibleBook("Zephaniah", 3, Testament.OLD),
    BibleBook("Haggai", 2, Testament.OLD),
    BibleBook("Zechariah", 14, Testament.OLD),
    BibleBook("Malachi", 4, Testament.OLD),
    BibleBook("Matthew", 28, Testament.NEW),
    BibleBook("Mark", 16, Testament.NEW),
    BibleBook("Luke", 24, Testament.NEW),
    BibleBook("John", 21, Testament.NEW),
    BibleBook("Acts", 28, Testament.NEW),
    BibleBook("Romans", 16, Testament.NEW),
    BibleBook("1 Corinthians", 16, Testament.NEW),
    BibleBook("2 Corinthians", 13, Testament.NEW),
    BibleBook("Galatians", 6, Testament.NEW),
    BibleBook("Ephesians", 6, Testament.NEW),
    BibleBook("Philippians", 4, Testament.NEW),
    BibleBook("Colossians", 4, Testament.NEW),
    BibleBook("1 Thessalonians", 5, Testament.NEW),
    BibleBook("2 Thessalonians", 3, Testament.NEW),
    BibleBook("1 Timothy", 6, Testament.NEW),
    BibleBook("2 Timothy", 4, Testament.NEW),
    BibleBook("Titus", 3, Testament.NEW),
    BibleBook("Philemon", 1, Testament.NEW),
    BibleBook("Hebrews", 13, Testament.NEW),
    BibleBook("James", 5, Testament.NEW),
    BibleBook("1 Peter", 5, Testament.NEW),
    BibleBook("2 Peter", 3, Testament.NEW),
    BibleBook("1 John", 5, Testament.NEW),
    BibleBook("2 John", 1, Testament.NEW),
    BibleBook("3 John", 1, Testament.NEW),
    BibleBook("Jude", 1, Testament.NEW),
    BibleBook("Revelation", 22, Testament.NEW),
)

fun bibleBookByName(name: String): BibleBook? {
    val normalized = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "")
    return SoraBibleBooks.firstOrNull {
        it.name.lowercase().replace(Regex("[^a-z0-9]+"), "") == normalized
    }
}
