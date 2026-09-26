package com.tomex777.annie

import android.graphics.Color
import android.view.View
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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.junit.runner.RunWith
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf

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
            compose.onNodeWithTag("script_editor").assertIsDisplayed()
            onView(isAssignableFrom(CodeEditor::class.java)).perform(insertCodeEditorText("\n//caret-proof"))
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Save", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
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

        onView(allOf(isAssignableFrom(CodeEditor::class.java), isDisplayed())).check { view, noView ->
            if (noView != null) throw noView
            val editor = view as CodeEditor
            assertTrue("Script source unexpectedly became empty", editor.text.toString().isNotBlank())
            assertTrue("Native CodeEditor must remain attached and shown", editor.isShown)
            assertTrue(
                "Monarch syntax analysis must use MonarchColorScheme so token ids stay visible",
                editor.colorScheme is MonarchColorScheme,
            )
            val background = editor.colorScheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)
            val averageRgb = (Color.red(background) + Color.green(background) + Color.blue(background)) / 3
            assertTrue("Script Studio editor background must stay dark", averageRgb < 110)
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
        val boardNodes = compose.onAllNodesWithTag("script_image_message").fetchSemanticsNodes()
        compose.onAllNodesWithTag("script_image_message")[boardNodes.lastIndex].performClick()
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

        val hintsBefore = compose.onAllNodesWithText("Try ", substring = true).fetchSemanticsNodes().size
        val repliesBefore = compose.onAllNodesWithTag("received_message_animation").fetchSemanticsNodes().size
        compose.onNodeWithTag("context_action_hint").performClick()
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Try ", substring = true).fetchSemanticsNodes().size > hintsBefore &&
                compose.onAllNodesWithTag("received_message_animation").fetchSemanticsNodes().size > repliesBefore
        }
        val hintNodes = compose.onAllNodesWithText("Try ", substring = true).fetchSemanticsNodes()
        compose.onAllNodesWithText("Try ", substring = true)[hintNodes.lastIndex].assertExists()

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


private fun insertCodeEditorText(value: String): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> =
        allOf(isAssignableFrom(CodeEditor::class.java), isDisplayed())

    override fun getDescription(): String = "insert text into the native Script Studio CodeEditor"

    override fun perform(uiController: UiController, view: View) {
        val editor = view as CodeEditor
        editor.requestFocus()
        val line = (editor.text.lineCount - 1).coerceAtLeast(0)
        editor.setSelection(line, editor.text.getColumnCount(line))
        editor.insertText(value, value.length)
        uiController.loopMainThreadUntilIdle()
    }
}
