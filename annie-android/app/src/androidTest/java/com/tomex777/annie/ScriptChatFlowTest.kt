package com.tomex777.annie

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptChatFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun scriptsCommandOpensTheInAppStudio() {
        compose.setContent { AnnieTheme { AnnieChat() } }
        compose.onNodeWithTag("composer_input").performTextInput("/scripts")
        compose.onNodeWithTag("send_message").performClick()
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("script_studio").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("script_studio").assertIsDisplayed()
        compose.onNodeWithTag("script_tab_files").assertIsDisplayed()
        compose.onNodeWithTag("script_tab_api").assertIsDisplayed()
        compose.onNodeWithText("chess.js", substring = false).assertIsDisplayed()
        assertEquals(true, compose.onAllNodesWithText("MAIN", substring = false).fetchSemanticsNodes().isNotEmpty())
        saveEmulatorScreenshot("annie-script-studio-files")
        assertEquals(0, compose.onAllNodesWithText("Console", substring = false).fetchSemanticsNodes().size)
        compose.onNodeWithTag("script_tab_editor").performClick()
        compose.onNodeWithTag("script_editor").assertIsDisplayed()
        compose.onNodeWithTag("script_console_drag_handle").assertIsDisplayed()
        compose.onNodeWithText("Output", substring = false).assertIsDisplayed()
        saveEmulatorScreenshot("annie-script-studio-editor")
    }

    @Test fun editorKeepsTypedTextVisibleAndSavesIt() {
        compose.setContent { AnnieTheme { AnnieChat() } }
        compose.onNodeWithTag("composer_input").performTextInput("/scripts")
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("script_studio").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("script_tab_editor").performClick()
        val files = ScriptFiles(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = files.readFile("chess", "chess.js")
        try {
            compose.onNodeWithTag("script_editor").performTouchInput { click(Offset(230f, 100f)) }
            InstrumentationRegistry.getInstrumentation().sendStringSync("//caret-proof")
            compose.waitForIdle()
            compose.onNodeWithText("Save", substring = false).performClick()
            compose.waitUntil(8_000) { files.readFile("chess", "chess.js") != original }
            assertEquals(true, files.readFile("chess", "chess.js").contains("//caret-proof"))
        } finally {
            files.writeFile("chess", "chess.js", original)
        }
    }

    @Test fun editorKeepsMonarchTokenColorsOpaqueAfterAnalysis() {
        compose.setContent { AnnieTheme { AnnieChat() } }
        compose.onNodeWithTag("composer_input").performTextInput("/scripts")
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("script_studio").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("script_tab_editor").performClick()
        compose.waitForIdle()

        // Give Monarch's asynchronous analyzer time to replace the initial plain-text spans.
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        compose.runOnIdle {
            val editor = checkNotNull(findCodeEditor(compose.activity.window.decorView)) {
                "Script Studio did not attach its native CodeEditor"
            }
            assertTrue("Script source unexpectedly became empty", editor.text.toString().isNotBlank())
            assertTrue(
                "Monarch syntax analysis must use MonarchColorScheme so token ids stay visible",
                editor.colorScheme is MonarchColorScheme,
            )
            for (dynamicColorId in 255..300) {
                assertTrue(
                    "Monarch token color $dynamicColorId became transparent",
                    Color.alpha(editor.colorScheme.getColor(dynamicColorId)) > 0,
                )
            }
        }
    }

    @Test fun scriptOptionTapRoutesBackToOwningJavaScriptAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "optionproof${System.nanoTime().toString().takeLast(8)}"
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(
            name, script.name, """
                |annie.actions.register("confirm", async (payload) => ({
                |  type: "text",
                |  text: "confirmed " + payload.value
                |}));
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    return {
                |      type: "options",
                |      title: "Choose",
                |      options: [{ id: "yes", label: "Yes", action: "confirm", payload: { value: "yes" } }]
                |    };
                |  }
                |});
            """.trimMargin()
        )
        try {
            compose.setContent { AnnieTheme { AnnieChat() } }
            compose.onNodeWithTag("composer_input").performTextInput("/$name")
            compose.waitUntil(8_000) {
                compose.onAllNodesWithTag("slash_command_/$name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("slash_command_/$name").performClick()
            compose.onNodeWithTag("send_message").performClick()
            compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
            compose.waitForIdle()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("script_options_message").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("script_options_message").assertIsDisplayed()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("script_option_yes").fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
            compose.waitForIdle()
            compose.onNodeWithTag("script_option_yes").assertIsDisplayed()
            compose.onNodeWithTag("script_option_yes").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("confirmed yes", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
            compose.waitForIdle()
            compose.onNodeWithText("confirmed yes", substring = false).assertIsDisplayed()
        } finally {
            runCatching { files.deleteProject(name) }
        }
    }

    @Test fun scriptCommandRunsThroughComposerAndAppearsAsAChatMessage() {
        compose.setContent { AnnieTheme { AnnieChat() } }
        compose.onNodeWithTag("composer_input").performTextInput("/echo")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("slash_command_/echo").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("slash_command_/echo").performClick()
        compose.onNodeWithTag("composer_input").performTextInput(" hello from the real chat")
        compose.onNodeWithTag("send_message").performClick()
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("hello from the real chat", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("hello from the real chat", substring = false).assertIsDisplayed()
        saveEmulatorScreenshot("annie-script-echo-chat")
    }

    @Test fun chessBoardAndPlainTextMoveUseTheRealChatPipeline() {
        compose.setContent { AnnieTheme { AnnieChat() } }
        compose.onNodeWithTag("composer_input").performTextInput("/chess")
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("slash_command_/chess").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("slash_command_/chess").performClick()
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(12_000) {
            compose.onAllNodesWithTag("script_image_message").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("script_image_message")[0].assertIsDisplayed()
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()
        saveEmulatorScreenshot("annie-script-chess-board")

        compose.onNodeWithTag("composer_input").performTextInput("e4")
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(12_000) {
            compose.onAllNodesWithTag("script_image_message").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("Black played", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("script_image_message")[0].assertIsDisplayed()
        compose.onNodeWithText("Black played", substring = true).assertExists()
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()
        saveEmulatorScreenshot("annie-script-chess-move")
        compose.onAllNodesWithTag("script_image_message")[0].performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("script_image_fullscreen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("script_image_fullscreen", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun chessSessionExposesOnlyDeclaredContextActionsAndAnimatesReply() {
        compose.setContent { AnnieTheme { AnnieChat() } }
        compose.onNodeWithTag("composer_input").performTextInput("/chess")
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("slash_command_/chess").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("slash_command_/chess").performClick()
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(12_000) {
            compose.onAllNodesWithTag("context_action_hint").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("context_action_show_board").assertIsDisplayed()
        compose.onNodeWithTag("context_action_hint").assertIsDisplayed()
        compose.onNodeWithTag("context_action_resign").assertIsDisplayed()

        compose.onNodeWithTag("context_action_hint").performClick()
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Try ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Try ", substring = true).assertExists()
        compose.onAllNodesWithTag("received_message_animation")[0].assertExists()

        compose.onNodeWithTag("context_action_resign").performClick()
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Game ended.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("context_suggestions").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun scriptVideoMessageOpensTheStandalonePlayerWithItsSourceConfig() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "videoproof${System.nanoTime().toString().takeLast(8)}"
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        val videoUrl = "http://127.0.0.1:1/annie-test.mp4"
        files.writeFile(
            name, script.name, """
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    return { type: "video", title: "Script video proof", uri: "$videoUrl", quality: "Test" };
                |  }
                |});
            """.trimMargin()
        )
        val monitor = instrumentation.addMonitor(AnniePlayerActivity::class.java.name, null, false)
        var playerActivity: AnniePlayerActivity? = null
        try {
            compose.setContent { AnnieTheme { AnnieChat() } }
            compose.onNodeWithTag("composer_input").performTextInput("/$name")
            compose.waitUntil(8_000) {
                compose.onAllNodesWithTag("slash_command_/$name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("slash_command_/$name").performClick()
            compose.onNodeWithTag("send_message").performClick()
            compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
            compose.waitForIdle()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("script_video_message").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("script_video_message").performClick()

            playerActivity = instrumentation.waitForMonitorWithTimeout(monitor, 8_000) as? AnniePlayerActivity
            val player = checkNotNull(playerActivity) { "Tapping the script video did not open Annie's standalone player" }
            assertEquals("Script video proof", player.intent.getStringExtra(AnniePlayerActivity.EXTRA_TITLE))
            assertEquals(videoUrl, player.intent.getStringExtra(AnniePlayerActivity.EXTRA_MEDIA_URI))
            val config = org.json.JSONObject(
                player.intent.getStringExtra(AnniePlayerActivity.EXTRA_VIDEO_CONFIG).orEmpty()
            )
            assertEquals("Test", config.optString("quality"))
            instrumentation.waitForIdleSync()
            saveEmulatorScreenshot("annie-script-video-player")
        } finally {
            playerActivity?.finish()
            instrumentation.removeMonitor(monitor)
            runCatching { files.deleteProject(name) }
        }
    }

    @Test fun scriptMusicMessageKeepsItsLyricsInsideTheChatCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "musicproof${System.nanoTime().toString().takeLast(8)}"
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(
            name, script.name, """
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    return { type: "music", title: "Inline music proof", artist: "Annie", lyrics: "First lyric line" };
                |  }
                |});
            """.trimMargin()
        )
        try {
            compose.setContent { AnnieTheme { AnnieChat() } }
            compose.onNodeWithTag("composer_input").performTextInput("/$name")
            compose.waitUntil(8_000) {
                compose.onAllNodesWithTag("slash_command_/$name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("slash_command_/$name").performClick()
            compose.onNodeWithTag("send_message").performClick()
            compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
            compose.waitForIdle()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Inline music proof", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
            compose.waitForIdle()
            compose.onNodeWithText("Inline music proof", substring = false).assertIsDisplayed()
            compose.onNodeWithText("Lyrics", substring = false).performClick()
            compose.onNodeWithText("First lyric line", substring = false).assertIsDisplayed()
            saveEmulatorScreenshot("annie-script-music-lyrics")
        } finally {
            runCatching { files.deleteProject(name) }
        }
    }
}


private fun findCodeEditor(view: View): CodeEditor? {
    if (view is CodeEditor) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findCodeEditor(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}
