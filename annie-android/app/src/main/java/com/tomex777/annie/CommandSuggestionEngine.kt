package com.tomex777.annie

import android.content.Context
import org.json.JSONObject
import kotlin.math.min

internal data class ConversationContext(
    val activeScriptId: String? = null,
    val activeCommand: String? = null,
    val mediaType: String? = null,
    val capabilities: Set<String> = emptySet(),
    val suggestedActions: List<ScriptSuggestedAction> = emptyList(),
)

internal data class ScriptSuggestedAction(
    val label: String,
    val input: String,
)

internal data class CommandCandidate(
    val command: String,
    val label: String,
    val aliases: List<String> = emptyList(),
    val keywords: Set<String> = emptySet(),
    val scriptId: String? = null,
    val contextTags: Set<String> = emptySet(),
)

internal data class CommandUsage(
    val count: Int = 0,
    val lastUsedAtMillis: Long = 0L,
)

internal data class RankedCommandSuggestion(
    val candidate: CommandCandidate,
    val score: Int,
)

internal object CommandSuggestionEngine {
    fun rank(
        query: String,
        candidates: List<CommandCandidate>,
        usage: Map<String, CommandUsage> = emptyMap(),
        context: ConversationContext = ConversationContext(),
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = 6,
    ): List<RankedCommandSuggestion> {
        val raw = query.trimStart()
        if (!raw.startsWith("/") || raw.contains('\n')) return emptyList()
        val normalized = normalizeCommand(raw)

        return candidates.asSequence()
            .distinctBy { normalizeCommand(it.command) }
            .mapIndexedNotNull { index, candidate ->
                val base = matchScore(normalized, candidate) ?: return@mapIndexedNotNull null
                val key = normalizeCommand(candidate.command)
                val history = usage[key]
                var score = base - index
                if (history != null) {
                    score += min(history.count * 8, 64)
                    val age = (nowMillis - history.lastUsedAtMillis).coerceAtLeast(0L)
                    score += when {
                        age <= 10 * 60_000L -> 90
                        age <= 24 * 60 * 60_000L -> 70
                        age <= 7 * 24 * 60 * 60_000L -> 45
                        age <= 30L * 24 * 60 * 60_000L -> 20
                        else -> 0
                    }
                }
                if (candidate.scriptId != null && candidate.scriptId == context.activeScriptId) score += 180
                if (normalizeCommand(candidate.command).removePrefix("/") == context.activeCommand?.lowercase()) score += 120
                if (candidate.contextTags.any { it in context.capabilities }) score += 110
                context.mediaType?.lowercase()?.let { if (it in candidate.contextTags) score += 80 }
                RankedCommandSuggestion(candidate, score)
            }
            .sortedWith(
                compareByDescending<RankedCommandSuggestion> { it.score }
                    .thenBy { it.candidate.command.length }
                    .thenBy { it.candidate.command.lowercase() }
            )
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun matchScore(query: String, candidate: CommandCandidate): Int? {
        val command = normalizeCommand(candidate.command)
        val aliases = candidate.aliases.map(::normalizeCommand)
        if (query == "/") {
            return if (command.removePrefix("/").contains(' ')) null else 360
        }
        if (command == query) return 1_000
        if (query in aliases) return 930
        if (command.startsWith(query)) return 840
        if (aliases.any { it.startsWith(query) }) return 780

        val term = query.removePrefix("/").trim()
        if (term.isBlank()) return null
        val words = buildList {
            addAll(command.removePrefix("/").split(Regex("[\\s_-]+")))
            aliases.forEach { addAll(it.removePrefix("/").split(Regex("[\\s_-]+"))) }
            addAll(candidate.label.lowercase().split(Regex("[^a-z0-9]+")))
            candidate.keywords.forEach { addAll(it.lowercase().split(Regex("[^a-z0-9]+"))) }
        }.filter(String::isNotBlank)

        if (words.any { it == term }) return 650
        if (words.any { it.startsWith(term) }) return 610
        if (words.any { it.contains(term) }) return 540

        if (term.length < 2) return null
        val distance = words.minOfOrNull { boundedEditDistance(term, it, 3) } ?: Int.MAX_VALUE
        if (distance <= if (term.length <= 4) 1 else 2) return 450 - distance * 55
        if (words.any { isSubsequence(term, it) }) return 360
        return null
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var at = 0
        for (char in haystack) {
            if (at < needle.length && needle[at] == char) at++
        }
        return at == needle.length
    }

    private fun boundedEditDistance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            var rowMin = current[0]
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1,
                )
                rowMin = minOf(rowMin, current[j + 1])
            }
            if (rowMin > limit) return limit + 1
            previous = current
        }
        return previous[b.length]
    }

    private fun normalizeCommand(value: String): String {
        val compact = value.trim().lowercase().replace(Regex("\\s+"), " ")
        return if (compact.startsWith("/")) compact else "/$compact"
    }
}

internal object CommandUsageStore {
    private const val PREFS = "annie_command_usage_v1"

    fun read(context: Context): Map<String, CommandUsage> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, raw) ->
            val json = runCatching { JSONObject(raw as? String ?: return@mapNotNull null) }.getOrNull()
                ?: return@mapNotNull null
            key to CommandUsage(
                count = json.optInt("count").coerceAtLeast(0),
                lastUsedAtMillis = json.optLong("lastUsedAtMillis").coerceAtLeast(0L),
            )
        }.toMap()
    }

    fun record(
        context: Context,
        current: Map<String, CommandUsage>,
        command: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, CommandUsage> {
        val key = command.trim().lowercase().substringBefore(' ').let { if (it.startsWith("/")) it else "/$it" }
        if (key.length <= 1) return current
        val previous = current[key] ?: CommandUsage()
        val next = previous.copy(count = previous.count + 1, lastUsedAtMillis = nowMillis)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, JSONObject().put("count", next.count).put("lastUsedAtMillis", next.lastUsedAtMillis).toString())
            .apply()
        return current + (key to next)
    }
}

internal fun builtInCommandCandidates(): List<CommandCandidate> = listOf(
    CommandCandidate("/anime", "Browse anime", keywords = setOf("anime", "episodes"), contextTags = setOf("anime")),
    CommandCandidate("/anime search", "Search the catalog", keywords = setOf("find", "title"), contextTags = setOf("anime")),
    CommandCandidate("/anime recent", "New episodes", keywords = setOf("aired", "recent"), contextTags = setOf("anime")),
    CommandCandidate("/anime downloads", "Downloads", keywords = setOf("saved"), contextTags = setOf("anime")),
    CommandCandidate("/anime recently aired", "Recently aired", keywords = setOf("aired", "recent"), contextTags = setOf("anime")),
    CommandCandidate("/anime continue", "Continue watching", keywords = setOf("resume"), contextTags = setOf("anime")),
    CommandCandidate("/anime continue watching", "Continue watching", keywords = setOf("resume"), contextTags = setOf("anime")),
    CommandCandidate("/manga", "Browse manga", keywords = setOf("manga", "chapters"), contextTags = setOf("manga")),
    CommandCandidate("/movie search", "Search movies", keywords = setOf("find", "film"), contextTags = setOf("movie")),
    CommandCandidate("/movie", "Browse movies", aliases = listOf("/movies"), keywords = setOf("film"), contextTags = setOf("movie")),
    CommandCandidate("/movie continue", "Continue watching", keywords = setOf("resume"), contextTags = setOf("movie")),
    CommandCandidate("/tv series", "Browse TV series", keywords = setOf("television", "series"), contextTags = setOf("tv")),
    CommandCandidate("/tv", "Search TV series", aliases = listOf("/series"), keywords = setOf("television", "series"), contextTags = setOf("tv")),
    CommandCandidate("/tv search", "Search TV series", keywords = setOf("find", "series"), contextTags = setOf("tv")),
    CommandCandidate("/tv continue", "Continue watching", keywords = setOf("resume", "series"), contextTags = setOf("tv")),
    CommandCandidate("/manga search", "Search manga", keywords = setOf("find"), contextTags = setOf("manga")),
    CommandCandidate("/manga continue", "Continue reading", keywords = setOf("resume"), contextTags = setOf("manga")),
    CommandCandidate("/manga downloads", "Downloads", keywords = setOf("saved"), contextTags = setOf("manga")),
    CommandCandidate("/music", "Music", keywords = setOf("song", "track"), contextTags = setOf("music")),
    CommandCandidate("/continue", "Continue watching", keywords = setOf("resume")),
    CommandCandidate("/downloads", "Downloads", keywords = setOf("saved", "offline")),
    CommandCandidate("/scripts", "JavaScript projects", keywords = setOf("studio", "code", "javascript")),
    CommandCandidate("/extensions", "Extensions", aliases = listOf("/settings"), keywords = setOf("providers", "sources")),
    CommandCandidate("/help", "Help", keywords = setOf("commands")),
)

internal fun ScriptCommand.toCommandCandidate(): CommandCandidate = CommandCandidate(
    command = "/$name",
    label = description.ifBlank { "JavaScript command" },
    aliases = aliases.map { if (it.startsWith("/")) it else "/$it" },
    keywords = keywords.toSet(),
    scriptId = scriptId,
    contextTags = capabilities.toSet(),
)
