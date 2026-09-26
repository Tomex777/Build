package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnieCharacterAndStarterMigrationTest {
    @Test fun characterRosterIsLargeAndIdsAreUnique() {
        assertTrue(AnnieCharacters.all.size >= 20)
        assertEquals(AnnieCharacters.all.size, AnnieCharacters.all.map { it.id }.toSet().size)
    }

    @Test fun existingChatCharacterMigrationIsStable() {
        val first = AnnieCharacters.stableIdForExistingChat("chat-123")
        val second = AnnieCharacters.stableIdForExistingChat("chat-123")
        assertEquals(first, second)
        assertTrue(first in AnnieCharacters.all.map { it.id })
    }

    @Test fun randomNewChatAssignmentCanExcludeCurrentCharacter() {
        val excluded = AnnieCharacters.all.first().id
        repeat(100) {
            assertNotEquals(excluded, AnnieCharacters.randomId(excluded))
        }
    }

    @Test fun legacyStarterChessGetsCapabilitiesWithoutReplacingCustomScripts() {
        val legacy = """
            before();
            annie.commands.register({
              name: "chess",
              description: "Play local chess with Annie",
              usage: "/chess new",
              async execute(ctx) {
                return { type: "text", text: "legacy" };
              }
            });
            after();
        """.trimIndent()
        val migrated = StarterScripts.migrateChess(legacy)
        assertTrue("capabilities:" in migrated)
        assertTrue("suggestions:" in migrated)
        assertTrue("before();" in migrated)
        assertTrue("after();" in migrated)

        val custom = """
            annie.commands.register({
              name: "chess",
              description: "My custom chess",
              async execute(ctx) { return { type: "text", text: "mine" }; }
            });
        """.trimIndent()
        assertEquals(custom, StarterScripts.migrateChess(custom))
    }

    @Test fun legacyChessSessionGetsOnlySupportedContextActions() {
        val legacy = """
            async onMessage(ctx) {
                if (String(ctx.text).trim().toLowerCase() === "resign") {
                  ctx.session.end();
                  await annie.storage.set("game:" + ctx.chatId, null);
                  return { type: "text", text: "Game ended. Use /chess new whenever you want another one." };
                }
                const user = parseMove(state, ctx.text);
            }
        """.trimIndent()
        val migrated = StarterScripts.migrateChess(legacy)
        assertTrue("""action === "board"""" in migrated)
        assertTrue("""action === "hint"""" in migrated)
        assertTrue("""action === "resign"""" in migrated)
    }
}
