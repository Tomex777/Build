package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L4 item 5 (JVM part): command collisions keep every command and never go silent. */
class CommandCollisionTest {
    private val installed = mapOf("old" to 100L, "mid" to 200L, "new" to 300L)
    private fun at(id: String) = installed[id] ?: 0L

    private fun command(script: String, pkg: String, name: String, aliases: List<String> = emptyList()) = ScriptCommand(
        scriptId = script, name = name, aliases = aliases, description = "d", usage = "/$name <q>", packageId = pkg,
    )

    @Test fun uniqueCommandsAreUntouched() {
        val out = CommandCanonicalizer.canonicalize(listOf(command("old", "com.a", "x"), command("new", "com.b", "y")), ::at)
        assertEquals(listOf("x", "y"), out.map { it.name })
        assertTrue(out.none { it.collision })
    }

    @Test fun bothSurviveOldestKeepsPlainNameNewerGetsSlug() {
        val out = CommandCanonicalizer.canonicalize(
            listOf(command("new", "com.Example.New", "x"), command("old", "com.example.old", "x")), ::at,
        )
        assertEquals(2, out.size)
        val newer = out.first { it.scriptId == "new" }
        val older = out.first { it.scriptId == "old" }
        assertEquals("x", older.name)
        assertEquals("com_example_new:x", newer.name)
        assertEquals("/com_example_new:x <q>", newer.usage)
        assertTrue(older.collision && newer.collision)
        assertNull(older.collidesWith)
        assertEquals("com.example.old", newer.collidesWith)
    }

    @Test fun collisionIsCaseInsensitive() {
        val out = CommandCanonicalizer.canonicalize(listOf(command("old", "com.a", "Pin"), command("new", "com.b", "pin")), ::at)
        assertEquals(setOf("Pin", "com_b:pin"), out.map { it.name }.toSet())
    }

    @Test fun threeWayCollisionRenamesBothNewer() {
        val out = CommandCanonicalizer.canonicalize(
            listOf(command("old", "com.a", "x"), command("mid", "com.b", "x"), command("new", "com.c", "x")), ::at,
        )
        assertEquals(setOf("x", "com_b:x", "com_c:x"), out.map { it.name }.toSet())
        assertTrue(out.all { it.collision })
    }

    @Test fun sameSlugFallsBackToScriptIdSoNamesStayUnique() {
        // Two newer packages whose ids normalise to the same slug.
        val out = CommandCanonicalizer.canonicalize(
            listOf(command("old", "com.a", "x"), command("mid", "com.b", "x"), command("new", "com_b", "x")), ::at,
        )
        assertEquals(3, out.map { it.name.lowercase() }.toSet().size)
    }

    @Test fun newerCommandLosesAliasesButKeepsHandler() {
        val out = CommandCanonicalizer.canonicalize(
            listOf(command("old", "com.a", "x", listOf("ex")), command("new", "com.b", "x", listOf("ex2"))), ::at,
        )
        val newer = out.first { it.scriptId == "new" }
        assertTrue(newer.aliases.isEmpty())
        assertEquals("x", newer.handlerName)
        assertEquals(listOf("ex"), out.first { it.scriptId == "old" }.aliases)
    }

    @Test fun aliasEqualToAnotherCommandNameIsRemovedAndFlagged() {
        val out = CommandCanonicalizer.canonicalize(
            listOf(command("old", "com.a", "play"), command("new", "com.b", "song", listOf("play", "tune"))), ::at,
        )
        val song = out.first { it.name == "song" }
        assertEquals(listOf("tune"), song.aliases)
        assertTrue(song.collision)
        assertFalse(out.first { it.name == "play" }.collision)
    }

    @Test fun aliasAlreadyClaimedByOlderPackageGoesToTheOlder() {
        val out = CommandCanonicalizer.canonicalize(
            listOf(command("new", "com.b", "b", listOf("go")), command("old", "com.a", "a", listOf("go"))), ::at,
        )
        assertEquals(listOf("go"), out.first { it.name == "a" }.aliases)
        assertTrue(out.first { it.name == "b" }.aliases.isEmpty())
    }

    @Test fun slugRules() {
        assertEquals("com_example_x", CommandCanonicalizer.packageSlug("Com.Example.X", "fb"))
        assertEquals("a_b", CommandCanonicalizer.packageSlug("--a//b__", "fb"))
        assertEquals("fb", CommandCanonicalizer.packageSlug("...", "fb"))
        assertEquals("fb", CommandCanonicalizer.packageSlug(null, "fb"))
        assertEquals(48, CommandCanonicalizer.packageSlug("a".repeat(80), "fb").length)
    }
}
