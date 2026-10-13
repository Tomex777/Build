package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeScriptRuntimeTest {
    @Test fun typedEntryAndImportedModuleRunThroughAnnie() = runBlocking {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        val name = "tsproof" + System.nanoTime().toString().takeLast(8)
        try {
            val file = workspace.files.createScript("$name.ts")
            workspace.files.writeFile(name, file.name, """
                interface Item { value: number }
                enum Mode { Twice = 2 }
                function identity<T>(value: T): T { return value; }
                annie.commands.register({ name: "$name", async execute() {
                  const item: Item = { value: identity(21) };
                  return annie.messages.text(String(item.value * Mode.Twice));
                }});
            """.trimIndent())
            assertTrue("Typed command did not load: ${workspace.logs()}", workspace.reload().any { it.name == name })
            assertEquals("42", JSONObject(workspace.execute(name, "/$name", "ts-test", 1L)).getString("text"))
            workspace.files.writeFile(name, file.name, "const broken: = 1;")
            assertFalse(workspace.reload().any { it.name == name })
            assertTrue(workspace.logs().any { it.scriptId == name && it.level == "ERROR" && it.message.contains("TS") })
        } finally {
            runCatching { workspace.files.deleteProject(name) }
            workspace.close()
        }
    }

    @Test fun typescriptFolderImportsAndJavascriptInterop() = runBlocking {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        val name = "tsmodules" + System.nanoTime().toString().takeLast(8)
        try {
            workspace.files.createFolder(name)
            workspace.files.createFile(name, "helper.ts")
            workspace.files.writeFile(name, "helper.ts", "export const value: number = 42;")
            workspace.files.writeFile(name, "main.js", """
                import { value } from './helper.ts';
                annie.commands.register({name: "$name", execute() { return annie.messages.text(String(value)); }});
            """.trimIndent())
            assertTrue("TS import failed: ${workspace.logs()}", workspace.reload().any { it.name == name })
            assertEquals("42", JSONObject(workspace.execute(name, "/$name", "ts-test", 2L)).getString("text"))
        } finally {
            runCatching { workspace.files.deleteProject(name) }
            workspace.close()
        }
    }
}
