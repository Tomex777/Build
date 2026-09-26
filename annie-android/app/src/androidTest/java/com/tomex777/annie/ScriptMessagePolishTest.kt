package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("script_text_message").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("script_text_message").assertIsDisplayed()
            compose.onNodeWithText("Native scripted bubble", substring = false).assertIsDisplayed()
            saveEmulatorScreenshot("annie-script-text-bubble")
        } finally {
            runCatching { files.deleteProject(name) }
        }
    }
}
