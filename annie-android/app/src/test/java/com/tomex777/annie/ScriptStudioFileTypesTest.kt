package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptStudioFileTypesTest {
    @Test fun selectedTypeAppliesToExtensionlessNamesInsideDottedFolders() {
        assertEquals("assets.v2/board.html", studioNewFileName(" assets.v2/board ", "html"))
        assertEquals("styles.css", studioNewFileName("styles", "css"))
    }

    @Test fun explicitExtensionOverridesSelectorWithoutDoubleSuffix() {
        assertEquals("theme.css", studioNewFileName("theme.css", "js"))
        assertEquals("INDEX.HTML", studioNewFileName("INDEX.HTML", "css"))
    }

    @Test fun newFilesInheritTheCurrentSupportedDocumentType() {
        assertEquals("html", studioDefaultFileType("views/INDEX.HTML"))
        assertEquals("js", studioDefaultFileType(null))
        assertEquals("js", studioDefaultFileType("picture.png"))
    }

    @Test fun creationAcceptsSafeNestedCodeFiles() {
        assertEquals("src/util.ts", studioNewFileName("src/util", "ts"))
        assertEquals("notes/Journal.MD", studioNewFileName("notes/Journal.MD", "js"))
        assertEquals("data/table.json", studioNewFileName("data/table.json", "html"))
    }

    @Test fun invalidNamesAreRejectedWithoutDismissOrHiddenFileSideEffects() {
        for (name in listOf("../outside.js", "dir/../../escape.css", "/absolute.js",
            "folder\\\\escape.js", "draft.exe", "file.", "x".repeat(65) + ".js",
            "folder//doc.md", ".hidden.js")) {
            org.junit.Assert.assertTrue("Invalid file name unexpectedly allowed: $name",
                runCatching { studioNewFileName(name, "js") }.isFailure)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameDoesNotBecomeAnInvisibleJsFile() {
        studioNewFileName(" ", "js")
    }
}
