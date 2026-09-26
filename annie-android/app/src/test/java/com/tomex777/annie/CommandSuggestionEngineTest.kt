package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSuggestionEngineTest {
    @Test fun suggestionsRequireAnActualSlashPrefix() {
        assertTrue(CommandSuggestionEngine.rank("", builtInCommandCandidates()).isEmpty())
        assertTrue(CommandSuggestionEngine.rank("anime", builtInCommandCandidates()).isEmpty())
        assertTrue(CommandSuggestionEngine.rank("  hello", builtInCommandCandidates()).isEmpty())
    }

    @Test fun exactPrefixMakesAnimeTheObviousAniSuggestion() {
        val ranked = CommandSuggestionEngine.rank("/ani", builtInCommandCandidates())
        assertEquals("/anime", ranked.first().candidate.command)
    }

    @Test fun aliasPrefixCanFindARegisteredScriptWithoutInventingCommands() {
        val candidates = listOf(
            CommandCandidate("/lookup", "Search", aliases = listOf("/find")),
            CommandCandidate("/other", "Other"),
        )
        val ranked = CommandSuggestionEngine.rank("/fi", candidates)
        assertEquals(listOf("/lookup"), ranked.map { it.candidate.command })
        assertTrue(ranked.all { it.candidate in candidates })
    }

    @Test fun recentUsageBreaksOtherwiseEquivalentMatches() {
        val now = 10_000_000L
        val candidates = listOf(
            CommandCandidate("/alpha", "One", keywords = setOf("tool")),
            CommandCandidate("/alpine", "Two", keywords = setOf("tool")),
        )
        val ranked = CommandSuggestionEngine.rank(
            "/al",
            candidates,
            usage = mapOf("/alpine" to CommandUsage(count = 4, lastUsedAtMillis = now - 1000)),
            nowMillis = now,
        )
        assertEquals("/alpine", ranked.first().candidate.command)
    }

    @Test fun activeScriptContextBoostsOnlyCommandsThatActuallyExist() {
        val candidates = listOf(
            CommandCandidate("/chess", "Chess", scriptId = "chess", contextTags = setOf("game")),
            CommandCandidate("/checkers", "Checkers", scriptId = "checkers", contextTags = setOf("game")),
        )
        val ranked = CommandSuggestionEngine.rank(
            "/ch",
            candidates,
            context = ConversationContext(activeScriptId = "chess", capabilities = setOf("game")),
        )
        assertEquals("/chess", ranked.first().candidate.command)
        assertEquals(candidates.toSet(), ranked.map { it.candidate }.toSet())
    }

    @Test fun fuzzyMatchIsUsefulButNeverCreatesAnUnsupportedCommand() {
        val candidates = listOf(CommandCandidate("/downloads", "Downloads"))
        val ranked = CommandSuggestionEngine.rank("/downlods", candidates)
        assertEquals(listOf("/downloads"), ranked.map { it.candidate.command })
    }
}
