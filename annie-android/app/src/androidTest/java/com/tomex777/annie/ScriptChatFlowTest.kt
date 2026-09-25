package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptChatFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun scriptsCommandOpensTheInAppStudio() {
        compose.setContent { AnnieTheme { AnnieChat() } }
        compose.onNodeWithTag("composer_input").performTextInput("/scripts")
        compose.onNodeWithTag("send_message").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("script_studio").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("script_studio").assertIsDisplayed()
        compose.onNodeWithTag("script_editor").assertIsDisplayed()
        saveEmulatorScreenshot("annie-script-studio")
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
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("script_option_yes").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("script_option_yes").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("confirmed yes", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
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
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("hello from the real chat", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("hello from the real chat", substring = false).assertIsDisplayed()
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
}
