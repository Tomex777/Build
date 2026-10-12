package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptStudioFileCreationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun typeSelectionCreatesHtmlAndExplicitCssExtensionOverridesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val folder = workspace.files.createFolder("filetypes${System.nanoTime()}")
        val shown = mutableStateOf(true)
        try {
            compose.setContent {
                AnnieTheme {
                    if (shown.value) ScriptStudioSheet(workspace, onCommandsReloaded = {}, initialProjectId = folder.name)
                }
            }
            compose.onNodeWithText("New File").performClick()
            compose.onNodeWithTag("script_file_name").performTextInput("board")
            compose.onNodeWithTag("script_file_type_html").performClick()
            compose.onNodeWithText("Continue").performClick()
            compose.waitUntil(8_000) { File(folder, "board.html").isFile }
            assertFalse(File(folder, "board.js").exists())
            assertTrue(File(folder, "board.html").readText().startsWith("<!doctype html>"))

            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithText("New File").performClick()
            // HTML is inferred from the current document, but an explicit suffix
            // still wins even when the selector says JavaScript.
            compose.onNodeWithTag("script_file_type_js").performClick()
            compose.onNodeWithTag("script_file_name").performTextInput("theme.css")
            compose.onNodeWithText("Continue").performClick()
            compose.waitUntil(8_000) { File(folder, "theme.css").isFile }
            assertFalse(File(folder, "theme.css.js").exists())
            assertTrue(File(folder, "theme.css").readText().startsWith("/*"))
            saveEmulatorScreenshot("annie-studio-selected-file-types")
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.runOnIdle { workspace.close() }
            workspace.files.deleteProject(folder.name)
        }
    }
    @Test fun failedDiskWriteKeepsDraftAndBlocksFileSwitchAndClose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val name = "savefail" + System.nanoTime().toString().takeLast(8)
        val file = workspace.files.createScript(name)
        val otherName = "other" + System.nanoTime().toString().takeLast(8)
        val other = workspace.files.createScript(otherName)
        workspace.files.writeFile(name, file.name, "const value = 'needle';")
        val shown = mutableStateOf(true)
        var closed = false
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {}, initialProjectId = name,
                    onClose = { closed = true })
            } }
            compose.onNodeWithTag("script_tab_editor").performClick()
            // Replace the backing file with a directory to force a real write failure.
            assertTrue(file.delete())
            assertTrue(file.mkdir())
            compose.onNodeWithTag("Find and replace").performClick()
            compose.onNodeWithTag("script_find_query").performTextInput("needle")
            compose.onNodeWithTag("script_replace_text").performTextInput("retained")
            compose.onNodeWithTag("Replace all matches").performClick()
            compose.onNodeWithTag("script_find_query").performTextClearance()
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithText(other.name, substring = false).performClick()
            compose.onNodeWithText("Could not save previous file:", substring = true).assertExists()
            compose.onNodeWithText("$name · ${file.name}").assertExists()
            compose.onNodeWithTag("Close Script Studio").performClick()
            assertFalse("Failed save must keep Studio open", closed)
            // Recover storage, then navigation must write the retained draft.
            assertTrue(file.delete())
            file.writeText("const value = 'needle';")
            compose.onNodeWithText(other.name, substring = false).performClick()
            compose.waitUntil(8_000) { file.isFile && file.readText().contains("retained") }
            assertTrue(file.readText().contains("retained"))
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(name)
            workspace.files.deleteProject(otherName)
        }
    }

}

