package com.tomex777.annie

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptEditorReplaceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun replacementIsLiteralUndoableAndSavedToTheSameScript() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val name = "replace" + System.nanoTime().toString().takeLast(8)
        val file = workspace.files.createScript(name)
        val original = "const first = \"needle\"; const second = \"NEEDLE\";"
        workspace.files.writeFile(name, file.name, original)
        try {
            compose.setContent { AnnieTheme { ScriptStudioSheet(workspace, {}, initialProjectId = name) } }
            compose.onNodeWithTag("script_tab_editor").performClick()
            compose.onNodeWithTag("Find and replace").performClick()
            compose.onNodeWithTag("script_find_query").performTextInput("needle")
            compose.onNodeWithTag("script_replace_text").performTextInput("\$saved")
            hideEmulatorKeyboard(compose.activity)
            compose.onNodeWithTag("Replace all matches").performClick()
            compose.onNodeWithText("Save", substring = false).performClick()
            val expected = "const first = \"\$saved\"; const second = \"\$saved\";"
            compose.waitUntil(5_000) { workspace.files.readFile(name, file.name) == expected }
            assertEquals(expected, workspace.files.readFile(name, file.name))
            saveEmulatorScreenshot("annie-script-editor-replace")
            compose.onNodeWithTag("Undo").performClick()
            compose.onNodeWithText("Save", substring = false).performClick()
            compose.waitUntil(5_000) { workspace.files.readFile(name, file.name) == original }
            assertEquals(original, workspace.files.readFile(name, file.name))
        } finally {
            workspace.close()
            workspace.files.deleteProject(name)
        }
    }
}
