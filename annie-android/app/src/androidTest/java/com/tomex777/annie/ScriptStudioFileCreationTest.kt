package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import io.github.rosemoe.sora.widget.CodeEditor
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
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

    @Test fun plainStudioOpensWithoutSelectingTheFirstFileOrShowingEditorTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val name = "unselected" + System.nanoTime().toString().takeLast(9)
        val file = workspace.files.createScript(name)
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {})
            } }
            compose.onNodeWithTag("script_tab_editor").assertDoesNotExist()
            compose.onNodeWithText("No file selected").assertExists()
            compose.onNodeWithTag("script_tab_files").assertExists()
            compose.onNodeWithTag("script_tab_env").assertExists()
            compose.onNodeWithTag("script_tab_api").assertExists()
            compose.onNodeWithTag("script_selected_file").assertDoesNotExist()
            saveEmulatorScreenshot("annie-agent3-studio-no-file-selected")
            compose.onNodeWithTag("script_file_search").performTextInput(name)
            closeSoftKeyboard()
            compose.onNodeWithText(file.name, substring = false).performClick()
            onView(isAssignableFrom(CodeEditor::class.java)).check { view, failure ->
                if (failure != null) throw failure
                assertTrue(view is CodeEditor)
            }
            // Editor is still reached from a chosen file and Files returns to browsing.
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithText("New File").assertExists()
            compose.onNodeWithTag("script_selected_file").assertExists()
            compose.onNodeWithTag("script_tab_editor").assertDoesNotExist()
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(name)
        }
    }

    @Test fun tappingFolderDoesNotSilentlyOpenItsEntryFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val folder = workspace.files.createFolder("folderfocus" + System.nanoTime().toString().takeLast(8))
        workspace.files.writeFile(folder.name, "main.js", "const stillUntouched = true;")
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {})
            } }
            compose.onNodeWithTag("script_file_search").performTextInput(folder.name)
            closeSoftKeyboard()
            compose.onNodeWithText(folder.name, substring = false).performClick()
            compose.onNodeWithTag("script_editor").assertDoesNotExist()
            compose.onNodeWithTag("script_selected_file").assertDoesNotExist()
            saveEmulatorScreenshot("annie-agent3-studio-folder-focus")
            compose.onNodeWithText("${folder.name} · main.js").assertDoesNotExist()
            compose.onNodeWithText("main.js", substring = false).performClick()
            compose.onNodeWithTag("script_editor").assertExists()
            onView(isAssignableFrom(CodeEditor::class.java)).check { view, failure ->
                if (failure != null) throw failure
                assertTrue((view as CodeEditor).text.toString().contains("stillUntouched"))
            }
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithText("ENV", substring = false).performClick()
            compose.onNodeWithTag("script_tab_env").assertExists()
            compose.onNodeWithText("API", substring = false).performClick()
            compose.onNodeWithTag("script_tab_api").assertExists()
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(folder.name)
        }
    }

    @Test fun newEmptyNestedFolderIsBrowsableAndDoesNotSelectMainJs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val folder = workspace.files.createFolder("nested" + System.nanoTime().toString().takeLast(8))
        workspace.files.createFolder(folder.name, "assets")
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {})
            } }
            compose.onNodeWithTag("script_file_search").performTextInput(folder.name)
            closeSoftKeyboard()
            compose.onNodeWithText(folder.name, substring = false).performClick()
            // Empty directories are not present in ScriptProject.files. They must
            // still be visible and usable without a dummy generated main.js.
            compose.onNodeWithText("assets", substring = false).assertExists()
            compose.onNodeWithText("assets", substring = false).performClick()
            compose.onNodeWithText("${folder.name} / assets").assertExists()
            compose.onNodeWithTag("script_editor").assertDoesNotExist()
            compose.onNodeWithTag("script_file_search").performTextClearance()
            compose.onNodeWithText("New File").performClick()
            compose.onNodeWithTag("script_file_type_css").performClick()
            compose.onNodeWithTag("script_file_name").performTextInput("colors")
            compose.onNodeWithText("Continue").performClick()
            compose.waitUntil(8_000) { File(folder, "assets/colors.css").isFile }
            assertTrue(File(folder, "assets/main.js").exists().not())
            assertTrue(File(folder, "main.js").isFile)
            compose.onNodeWithTag("script_editor").assertExists()
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(folder.name)
        }
    }

    @Test fun importedTextTypesAreNeverAutoEnabledAndPreserveTheirContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val suffix = System.nanoTime().toString().takeLast(8)
        val sources = mapOf(
            "js" to "throw new Error('imported code cannot execute before review');",
            "ts" to "const answer: number = 42;",
            "py" to "print('hello')",
            "lua" to "print('hello')",
            "html" to "<!doctype html><title>Imported</title>",
            "css" to "body { background: black; }",
            "json" to "{\"ready\": true}",
            "md" to "# Imported",
            "txt" to "plain text document",
        )
        val importedIds = mutableListOf<String>()
        try {
            sources.forEach { (extension, source) ->
                val name = "safe_${suffix}_$extension.$extension"
                val (id, path) = studioImportTextDocument(workspace.files, name, source)
                importedIds += id
                assertTrue("Imported project must be disabled: $extension",
                    workspace.files.listProjects().first { it.id == id }.enabled.not())
                org.junit.Assert.assertEquals(source, workspace.files.readFile(id, path))
                org.junit.Assert.assertEquals(extension, path.substringAfterLast('.'))
                if (extension in setOf("html", "css", "json", "md", "txt")) {
                    assertFalse("Data-only import must not invent runnable main.js",
                        File(workspace.files.root, "$id/main.js").exists())
                    org.junit.Assert.assertEquals(path,
                        workspace.files.listProjects().first { it.id == id }.entryPath)
                    assertTrue("Data-only project must refuse script enable",
                        runCatching { workspace.files.setEnabled(id, true) }.isFailure)
                }
            }
            val stream = java.io.ByteArrayInputStream(ByteArray(2 * 1024 * 1024 + 1))
            assertTrue("Oversized imports must be rejected before decoding",
                runCatching { studioReadTextImport(stream) }.isFailure)
            assertTrue("Unsupported image import must be rejected",
                runCatching { studioImportTextDocument(workspace.files, "photo.png", "binary") }.isFailure)
        } finally {
            workspace.close()
            importedIds.forEach { id -> workspace.files.deleteProject(id) }
        }
    }

    @Test fun documentOnlyProjectRenamesAndDeletesWithoutAHiddenEntryScript() {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        val importedIds = mutableListOf<String>()
        try {
            val source = "<section>Read only until edited</section>"
            val (id, path) = studioImportTextDocument(
                workspace.files, "doc${System.nanoTime().toString().takeLast(8)}.html", source,
            )
            importedIds += id
            assertFalse(File(workspace.files.root, "$id/main.js").exists())
            assertFalse(workspace.files.isEnabled(id))
            val moved = workspace.files.renameFile(id, path, "pages/landing.html")
            org.junit.Assert.assertEquals("pages/landing.html", moved)
            org.junit.Assert.assertEquals(source, workspace.files.readFile(id, moved))
            org.junit.Assert.assertEquals(moved,
                workspace.files.listProjects().single { it.id == id }.entryPath)
            workspace.files.deleteFile(id, moved)
            assertFalse(File(workspace.files.root, id).exists())
            importedIds.remove(id)
            val (oneId, onePath) = studioImportTextDocument(
                workspace.files, "collision${System.nanoTime().toString().takeLast(8)}.css", "body{}",
            )
            importedIds += oneId
            val (twoId, twoPath) = studioImportTextDocument(
                workspace.files, "$oneId.css", "p{}",
            )
            importedIds += twoId
            assertTrue("Duplicate import must not overwrite project", oneId != twoId)
            org.junit.Assert.assertEquals("body{}", workspace.files.readFile(oneId, onePath))
            org.junit.Assert.assertEquals("p{}", workspace.files.readFile(twoId, twoPath))
            val (longId, longPath) = studioImportTextDocument(
                workspace.files, "long" + "v".repeat(115) + ".html", "<p>Long name</p>",
            )
            importedIds += longId
            assertTrue("Sanitized name must satisfy Android project file constraints", longPath.length <= 64)
            org.junit.Assert.assertEquals("<p>Long name</p>", workspace.files.readFile(longId, longPath))
        } finally {
            workspace.close()
            importedIds.forEach { runCatching { workspace.files.deleteProject(it) } }
        }
    }

    @Test fun newRootHtmlFileIsARealDocumentAndNotADummyJsCommand() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val id = "rootdoc" + System.nanoTime().toString().takeLast(8)
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {})
            } }
            compose.onNodeWithText("New File").performClick()
            compose.onNodeWithTag("script_file_name").performTextInput("$id.html")
            compose.onNodeWithText("Continue").performClick()
            compose.waitUntil(8_000) { File(workspace.files.root, "$id/$id.html").isFile }
            compose.onNodeWithTag("script_editor").assertExists()
            onView(isAssignableFrom(CodeEditor::class.java)).check { view, failure ->
                if (failure != null) throw failure
                assertTrue("HTML documents should wrap on phone screens",
                    (view as CodeEditor).isWordwrap)
            }
            assertFalse(File(workspace.files.root, "$id/main.js").exists())
            assertFalse("A document cannot run a generated command", workspace.files.isEnabled(id))
            compose.onNodeWithText("Preview", substring = false).performClick()
            compose.waitUntil(8_000) {
                compose.onAllNodesWithTag("script_message_preview").fetchSemanticsNodes().isNotEmpty()
            }
            saveEmulatorScreenshot("annie-agent3-html-offline-preview")
            compose.onNodeWithText("Back to editor", substring = false).performClick()
            compose.onNodeWithTag("script_editor").assertExists()
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithTag("script_file_search").performTextInput(id)
            closeSoftKeyboard()
            compose.onNodeWithText("$id.html", substring = false).assertExists()
            saveEmulatorScreenshot("annie-agent3-root-html-document")
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            runCatching { workspace.files.deleteProject(id) }
        }
    }

    @Test fun unsupportedTextDocumentsNeverOfferAnActivePreviewButton() {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        val name = "readme" + System.nanoTime().toString().takeLast(8)
        val (id, _) = studioImportTextDocument(workspace.files, "$name.txt", "a note")
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {}, initialProjectId = id)
            } }
            compose.onNodeWithText("Preview", substring = false)
                .assertExists()
                .assertIsNotEnabled()
            compose.onNodeWithTag("script_tab_editor").assertDoesNotExist()
            assertFalse(File(workspace.files.root, "$id/main.js").exists())
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(id)
        }
    }

    @Test fun downloadedHtmlOpensRealDocumentWithoutGeneratedCommand() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val id = "htmlproof" + System.nanoTime().toString().takeLast(8)
        val source = "<!doctype html><html><body><h1>Test page</h1></body></html>"
        val download = File(context.cacheDir, "$id.html").apply { writeText(source) }
        val pending = mutableStateOf<File?>(download)
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(
                    workspace = workspace,
                    onCommandsReloaded = {},
                    importFile = pending.value,
                    onFileImportOpened = { pending.value = null },
                )
            } }
            compose.waitUntil(10_000) {
                pending.value == null && File(workspace.files.root, "$id/$id.html").isFile
            }
            compose.onNodeWithTag("script_editor").assertExists()
            compose.onNodeWithText("$id.html", substring = false).assertExists()
            assertFalse("Imported document cannot be enabled implicitly", workspace.files.isEnabled(id))
            assertFalse("Imported HTML must not create a dummy JavaScript command",
                File(workspace.files.root, "$id/main.js").exists())
            org.junit.Assert.assertEquals(source, workspace.files.readFile(id, "$id.html"))
            org.junit.Assert.assertEquals(source, download.readText())
            saveEmulatorScreenshot("annie-agent3-html-document-open")
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            runCatching { workspace.files.deleteProject(id) }
            download.delete()
        }
    }

    @Test fun fileFilterNeverBecomesEditorFindText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val folder = workspace.files.createFolder("searchsplit" + System.nanoTime().toString().takeLast(8))
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {})
            } }
            compose.onNodeWithTag("script_file_search").performTextInput(folder.name)
            closeSoftKeyboard()
            compose.onNodeWithText(folder.name, substring = false).performClick()
            compose.onNodeWithTag("script_file_search").performTextClearance()
            compose.onNodeWithTag("script_file_search").performTextInput("main.js")
            closeSoftKeyboard()
            compose.onNodeWithText("main.js", substring = false).performClick()
            compose.onNodeWithTag("script_editor").assertExists()
            compose.onNodeWithTag("script_find_query").assertTextEquals("")
            compose.onNodeWithTag("script_find_query").performTextInput("commands")
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithTag("script_file_search").assertTextEquals("main.js")
            compose.onNodeWithText("main.js", substring = false).performClick()
            compose.onNodeWithTag("script_find_query").assertTextEquals("commands")
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(folder.name)
        }
    }

    @Test fun removingSelectedProjectDoesNotSelectTheNextProject() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val suffix = System.nanoTime().toString().takeLast(8)
        val removed = workspace.files.createFolder("delete$suffix")
        val survivor = workspace.files.createFolder("survive$suffix")
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {})
            } }
            compose.onNodeWithTag("script_file_search").performTextInput(removed.name)
            closeSoftKeyboard()
            compose.onNodeWithText(removed.name, substring = false).performClick()
            compose.onNodeWithTag("script_selected_file").assertDoesNotExist()
            compose.onNodeWithContentDescription("File actions").performClick()
            compose.onNodeWithText("Delete", substring = false).performClick()
            compose.onNodeWithTag("script_file_name").performTextInput(removed.name)
            compose.onNodeWithText("Continue", substring = false).performClick()
            compose.waitUntil(8_000) { removed.exists().not() }
            compose.onNodeWithTag("script_file_search").performTextClearance()
            compose.onNodeWithTag("script_selected_file").assertDoesNotExist()
            compose.onNodeWithText("No file selected").assertExists()
            assertTrue("Remaining project must not be removed or selected", survivor.isDirectory)
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            if (removed.exists()) workspace.files.deleteProject(removed.name)
            workspace.files.deleteProject(survivor.name)
        }
    }

    @Test fun enablingOrDisablingOtherProjectNeverSwitchesTheFocusedFolder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val suffix = System.nanoTime().toString().takeLast(8)
        val focused = workspace.files.createFolder("focus$suffix")
        val another = workspace.files.createFolder("toggle$suffix")
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {})
            } }
            compose.onNodeWithTag("script_file_search").performTextInput(focused.name)
            closeSoftKeyboard()
            compose.onNodeWithText(focused.name, substring = false).performClick()
            compose.onNodeWithTag("script_file_search").performTextClearance()
            compose.onNodeWithTag("script_file_search").performTextInput(another.name)
            closeSoftKeyboard()
            // B is visible while A remains the deliberately focused project.
            compose.onNodeWithContentDescription("File actions").performClick()
            compose.onNodeWithText("Disable", substring = false).performClick()
            compose.waitUntil(8_000) { workspace.files.isEnabled(another.name).not() }
            compose.onNodeWithText(focused.name, substring = false).assertExists()
            compose.onNodeWithTag("script_selected_file").assertDoesNotExist()
            compose.onNodeWithTag("script_file_search").performTextClearance()
            compose.onNodeWithText("main.js", substring = false).assertExists()
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(focused.name)
            workspace.files.deleteProject(another.name)
        }
    }

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
            compose.onNodeWithTag("script_file_search").performTextInput(name)
            closeSoftKeyboard()
            compose.onNodeWithText(file.name, substring = false).performClick()
            // Replace the backing file with a directory to force a real write failure.
            assertTrue(file.delete())
            assertTrue(file.mkdir())
            compose.onNodeWithTag("Find and replace").performClick()
            compose.onNodeWithTag("script_find_query").performTextInput("needle")
            compose.onNodeWithTag("script_replace_text").performTextInput("retained")
            compose.onNodeWithTag("Replace all matches").performClick()
            compose.onNodeWithTag("script_find_query").performTextClearance()
            // All three top-level tab transitions must save or remain in the editor
            // when storage is unavailable; otherwise the unsaved draft is stranded.
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithTag("script_editor").assertExists()
            compose.onNodeWithText("Could not save previous file:", substring = true).assertExists()
            compose.onNodeWithText("ENV", substring = false).performClick()
            compose.onNodeWithTag("script_editor").assertExists()
            compose.onNodeWithText("API", substring = false).performClick()
            compose.onNodeWithTag("script_editor").assertExists()
            compose.onNodeWithText("$name · ${file.name}").assertExists()
            compose.onNodeWithTag("Close Script Studio").performClick()
            assertFalse("Failed save must keep Studio open", closed)
            pressBack()
            compose.waitForIdle()
            assertFalse("System Back must preserve a draft after failed save", closed)
            compose.onNodeWithText("Could not save previous file:", substring = true).assertExists()
            // Recover storage, then changing Files must first persist the draft.
            assertTrue(file.delete())
            file.writeText("const value = 'needle';")
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.waitUntil(8_000) { file.isFile && file.readText().contains("retained") }
            compose.onNodeWithTag("script_file_search").performTextInput(otherName)
            closeSoftKeyboard()
            compose.waitForIdle()
            compose.onNodeWithText(other.name, substring = false).performClick()
            assertTrue(file.readText().contains("retained"))
            compose.onNodeWithText("Files", substring = false).performClick()
            compose.onNodeWithTag("script_file_search").performTextClearance()
            compose.onNodeWithTag("script_file_search").performTextInput(name)
            closeSoftKeyboard()
            compose.onNodeWithText(file.name, substring = false).performClick()
            onView(isAssignableFrom(CodeEditor::class.java)).check { view, failure ->
                if (failure != null) throw failure
                assertTrue("Reopening must retain the latest saved source", (view as CodeEditor).text.toString().contains("retained"))
            }
            closeSoftKeyboard()
            pressBack()
            compose.waitForIdle()
            assertTrue("System Back may close after the draft is saved", closed)
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            // Restore the damaged fixture before cleanup so it cannot mask assertions.
            if (file.isDirectory) file.deleteRecursively()
            if (!file.isFile) file.writeText("")
            workspace.files.deleteProject(name)
            workspace.files.deleteProject(otherName)
        }
    }

    @Test fun folderSelectionLoadsItsOwnEntryWithoutLeakingOtherDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val suffix = System.nanoTime().toString().takeLast(8)
        val first = workspace.files.createFolder("first$suffix")
        val second = workspace.files.createFolder("second$suffix")
        val firstSource = "const owner = 'first';"
        val secondSource = "const owner = 'second';"
        workspace.files.writeFile(first.name, "main.js", firstSource)
        workspace.files.writeFile(second.name, "main.js", secondSource)
        val shown = mutableStateOf(true)
        try {
            compose.setContent { AnnieTheme {
                if (shown.value) ScriptStudioSheet(workspace, {}, initialProjectId = first.name)
            } }
            compose.onNodeWithTag("script_file_search").performTextInput(second.name)
            closeSoftKeyboard()
            compose.onNode(hasText(second.name, substring = false) and !hasTestTag("script_file_search")).performClick()
            compose.onNodeWithText("main.js", substring = false).performClick()
            onView(isAssignableFrom(CodeEditor::class.java)).check { view, failure ->
                if (failure != null) throw failure
                org.junit.Assert.assertEquals(secondSource, (view as CodeEditor).text.toString())
            }
            org.junit.Assert.assertEquals(firstSource, workspace.files.readFile(first.name, "main.js"))
            org.junit.Assert.assertEquals(secondSource, workspace.files.readFile(second.name, "main.js"))
        } finally {
            compose.runOnIdle { shown.value = false }
            compose.waitForIdle()
            workspace.close()
            workspace.files.deleteProject(first.name)
            workspace.files.deleteProject(second.name)
        }
    }

}

