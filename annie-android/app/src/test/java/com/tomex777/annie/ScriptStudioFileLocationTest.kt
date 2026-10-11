package com.tomex777.annie

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptStudioFileLocationTest {
    private val root = File(System.getProperty("java.io.tmpdir"), "annie-studio-test-root")

    @Test fun standaloneJavaScriptKeepsItsOwnProjectId() {
        assertEquals("alpha" to "alpha.js",
            studioCreatedFileLocation(root, File(root, "alpha.js")))
    }

    @Test fun nonJavascriptFileUnderNewProjectUsesFolderIdNotExtensionlessFileName() {
        assertEquals("landing" to "index.html",
            studioCreatedFileLocation(root, File(root, "landing/index.html")))
        assertEquals("theme" to "styles.css",
            studioCreatedFileLocation(root, File(root, "theme/styles.css")))
    }

    @Test fun nestedFileInsideExistingProjectRemainsSelected() {
        assertEquals("workspace" to "assets/styles.css",
            studioCreatedFileLocation(root, File(root, "workspace/assets/styles.css"), "workspace"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotSelectFileOutsideWorkspace() {
        studioCreatedFileLocation(root, File(root.parentFile, "outside.html"))
    }
}
