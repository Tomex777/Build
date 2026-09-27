package com.tomex777.annie

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnieScriptSpecTest {
    @Test fun portableSpecDocumentsCurrentNativeApisAndSelectedSource() {
        val project = ScriptProject(
            id = "demo",
            name = "Demo",
            entryPath = "main.js",
            files = mapOf("main.js" to "return oldValue;", "helper.js" to "export const x = 1;"),
        )

        val spec = AnnieScriptSpec.build(
            project = project,
            selectedPath = "main.js",
            selectedSource = "return currentValue;",
        )

        assertTrue(spec.contains("annie.env.define"))
        assertTrue(spec.contains("annie.env.secret"))
        assertTrue(spec.contains("annie.messages.form"))
        assertTrue(spec.contains("annie.schedule.create"))
        assertTrue(spec.contains("annie.tasks.start"))
        assertTrue(spec.contains("annie.assets.image/audio/uri(id)"))
        assertTrue(spec.contains("Registered native message types:"))
        assertTrue(spec.contains("video"))
        assertTrue(spec.contains("form"))
        assertTrue(spec.contains("Project: Demo"))
        assertTrue(spec.contains("return currentValue;"))
        assertFalse(spec.contains("return oldValue;"))
    }
}
