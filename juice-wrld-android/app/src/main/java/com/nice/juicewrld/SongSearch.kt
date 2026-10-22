package com.nice.juicewrld

import java.util.Locale

/**
 * Offline catalog search: matches ordinary typing variants ("im still"
 * for "I'm Still", "come and go" for "Come & Go") and partial terms in
 * any order without changing the selected mood or favorites filtering.
 */
internal object SongSearch {
    private val apostrophe = Regex("(?<=[a-z])['’](?=[a-z])")
    private val punctuation = Regex("[^a-z0-9]+")

    private fun words(value: String): List<String> {
        val canonical = value.lowercase(Locale.ROOT)
            .replace(apostrophe, "")
            .replace("&", " and ")
            .split(punctuation)
        return canonical.filter { it.isNotBlank() }
    }

    fun matches(song: FanSong, query: String): Boolean {
        val requested = words(query)
        if (requested.isEmpty()) return false
        val candidate = words(song.title + " " + song.album + " " + song.year)
        return requested.all { prefix -> candidate.any { term -> term.startsWith(prefix) } }
    }
}
