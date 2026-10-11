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

    @Test(expected = IllegalArgumentException::class)
    fun blankNameDoesNotBecomeAnInvisibleJsFile() {
        studioNewFileName(" ", "js")
    }
}
