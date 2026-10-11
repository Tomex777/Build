package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptPackageImportTest {
    @Test fun looseJavaScriptImportIsStagedDisabledUntilExplicitEnable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = ScriptFiles(context)
        val name = "importproof" + System.nanoTime().toString().takeLast(8)
        val workspace = ScriptWorkspace(context)
        try {
            val imported = files.importJavaScript(
                "$name.js",
                """
                    |annie.commands.register({
                    |  name: "$name",
                    |  async execute() { return annie.messages.text("ready"); }
                    |});
                """.trimMargin(),
            )
            assertTrue(imported.isFile)
            assertFalse(files.listProjects().first { it.id == name }.enabled)

            var commands = workspace.reload()
            assertFalse(commands.any { it.name == name })

            files.setEnabled(name, true)
            commands = workspace.reload()
            assertTrue(commands.any { it.name == name })
        } finally {
            workspace.close()
            runCatching { files.deleteProject(name) }
        }
    }
}
