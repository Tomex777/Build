package com.tomex777.annie

import org.junit.Assert.*
import org.junit.Test

class ScriptWebPreviewTest {
    @Test fun bundlesRelativeCssAndDeferredJsWithUnsavedSource() {
        val files = mapOf("pages/index.html" to "<head><link rel='stylesheet' href='../theme.css?v=1'><script defer src='../app.js'></script></head><button id='go'>Go</button>",
            "theme.css" to "button { color: teal; }", "app.js" to "document.getElementById('go').textContent = 'Updated';")
        val bundled = ScriptWebPreview.bundle(files, "pages/index.html")
        assertTrue(bundled.contains("<style>button { color: teal; }</style>"))
        assertTrue(bundled.indexOf("<button") < bundled.indexOf("<script>"))
        assertFalse(bundled.contains("src="))
        assertTrue(bundled.contains("Updated"))
        assertEquals("pages/index.html", ScriptWebPreview.entry(files, "pages/logic.js"))
    }
    @Test fun ignoresCommentTagsAndInlineScriptContents() {
        val html = "<!-- <script src='missing.js'></script> --><script>const s = \"<link rel='stylesheet' href='missing.css'>\";</script>"
        assertEquals(html, ScriptWebPreview.bundle(mapOf("index.html" to html), "index.html"))
    }
    @Test fun rejectsMissingRemoteAndEscapingDependencies() {
        for (ref in listOf("missing.js", "https://example.com/a.js", "//example.com/a.js", "../outside.js", "%2e%2e/outside.js", "/app.js")) {
            try {
                ScriptWebPreview.bundle(mapOf("index.html" to "<script src='$ref'></script>"), "index.html")
                fail("Must reject $ref")
            } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
        }
    }
    @Test fun preservesExecutionOrderAndRejectsModules() {
        val files = mapOf("index.html" to "<script src='first.js'></script><script src='second.js'></script>", "first.js" to "window.value=1;", "second.js" to "window.value++; // </SCRIPT>")
        val bundled = ScriptWebPreview.bundle(files, "index.html")
        assertTrue(bundled.indexOf("window.value=1") < bundled.indexOf("window.value++"))
        assertTrue(bundled.contains("<\\/script>"))
        try {
            ScriptWebPreview.bundle(files + ("index.html" to "<script type='module' src='first.js'></script>"), "index.html")
            fail("Modules must not silently break")
        } catch (_: IllegalArgumentException) {}
    }
}
