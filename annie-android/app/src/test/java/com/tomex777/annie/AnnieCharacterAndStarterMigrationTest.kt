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
    @Test fun canvasSnakeRendersStaticIdleStateWithoutPermanentFrameLoop() {
        val source = StarterScripts.canvasSnake
        assertTrue("Canvas must initially draw a playable board", "draw(1);" in source)
        assertTrue("Game should render after play", "scheduleFrame();" in source)
        assertTrue("Canvas startup must not run an endless animation",
            "reset();\n          requestAnimationFrame(frame);" !in source)
        assertTrue("Frame rate should be throttled on older Android WebViews",
            "setTimeout(() => requestAnimationFrame(frame), 32);" in source)
        assertTrue("Idle frames should stop after the game finishes",
            "if (!started || lost) return;" in source)
    }

    @Test fun canvasSnakeMigrationPreservesCustomEditedScripts() {
        val edited = StarterScripts.canvasSnake + "\n// user customization"
        assertEquals(edited, StarterScripts.migrateCanvasSnake(edited))
        val oldStarter = "old game seed"
        assertEquals(oldStarter, StarterScripts.migrateCanvasSnake(oldStarter))
    }

    @Test fun canvasSnakeHandlesNativeTouchAsWellAsPointerEvents() {
        val source = StarterScripts.canvasSnake
        assertTrue("Android 8 touchstart fallback missing", "addEventListener('touchstart'" in source)
        assertTrue("Touch move must call game steering", "swipe(t.clientX-touchOrigin.x,t.clientY-touchOrigin.y)" in source)
        assertTrue("Prevent WebView scrolling from intercepting game touches", "{passive:false}" in source)
        assertTrue("Modern pointer input must remain supported", "addEventListener('pointerdown'" in source)
    }

}
