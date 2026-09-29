package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptMessagePolishTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun scriptTextUsesTheNativeIncomingBubbleShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "textbubble" + System.nanoTime().toString().takeLast(8)
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(
            name,
            script.name,
            """
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    return annie.messages.text("Native scripted bubble");
                |  }
                |});
            """.trimMargin(),
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
            compose.waitUntil(3_000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("script_text_message").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("conversation")
                .performScrollToNode(hasTestTag("script_text_message"))
            compose.onNodeWithTag("script_text_message").assertIsDisplayed()
            compose.onNodeWithTag("conversation")
                .performScrollToNode(hasText("Native scripted bubble", substring = false))
            compose.onNodeWithText("Native scripted bubble", substring = false).assertIsDisplayed()
            saveEmulatorScreenshot("annie-script-text-bubble")
        } finally {
            runCatching { files.deleteProject(name) }
        }
    }

    @Test fun nativeFormRoutesSubmittedValuesBackToOwningScriptAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "formproof" + System.nanoTime().toString().takeLast(8)
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(
            name,
            script.name,
            """
                |annie.actions.register("submit-options", async payload => {
                |  return annie.messages.text(
                |    "FORM_RESULT " + payload.values.quality + " " + payload.values.subtitles
                |  );
                |});
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    return annie.messages.form({
                |      id: "download-options",
                |      title: "Download options",
                |      fields: [
                |        { id: "quality", type: "select", label: "Quality", options: ["720p", "1080p"], value: "1080p" },
                |        { id: "subtitles", type: "switch", label: "Include subtitles", value: true }
                |      ],
                |      submit: { label: "Download", action: "submit-options" }
                |    });
                |  }
                |});
            """.trimMargin(),
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
            compose.waitUntil(3_000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("script_form_message").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("conversation")
                .performScrollToNode(hasText("Download options", substring = false))
            compose.onNodeWithText("Download options", substring = false).assertIsDisplayed()
            compose.onNodeWithTag("conversation")
                .performScrollToNode(hasTestTag("script_form_option_quality_0"))
            compose.onNodeWithTag("script_form_option_quality_0").performClick()
            compose.onNodeWithTag("conversation")
                .performScrollToNode(hasTestTag("script_form_switch_subtitles"))
            compose.onNodeWithTag("script_form_switch_subtitles").performClick()
            compose.onNodeWithTag("conversation")
                .performScrollToNode(hasTestTag("script_form_submit"))
            compose.onNodeWithTag("script_form_submit").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("FORM_RESULT 720p false", substring = false)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("conversation")
                .performScrollToNode(hasText("FORM_RESULT 720p false", substring = false))
            compose.onNodeWithText("FORM_RESULT 720p false", substring = false).assertIsDisplayed()
            saveEmulatorScreenshot("annie-script-form-callback")
        } finally {
            runCatching { files.deleteProject(name) }
        }
    }

}
