package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestRuntimeTest {
    @Test fun typedEntryCannotBeMovedOrDeletedOutOfItsManifest() {
        val files = ScriptFiles(InstrumentationRegistry.getInstrumentation().targetContext)
        val name = "typedentry" + System.nanoTime().toString().takeLast(8)
        try {
            val folder = files.createFolder(name)
            files.createFile(name, "main.ts")
            java.io.File(folder, "manifest.json").writeText("""
                {"packageId":"com.example.$name","displayName":"Typed entry","version":"1.0.0","apiVersion":"2","entryPoint":"main.ts"}
            """.trimIndent())
            assertTrue(runCatching { files.deleteFile(name, "main.ts") }.isFailure)
            assertTrue(runCatching { files.renameFile(name, "main.ts", "moved.ts") }.isFailure)
            assertEquals("main.ts", files.readProject(folder)?.entryPath)
        } finally { runCatching { files.deleteProject(name) } }
    }

    @Test fun projectNamesCannotCollideAcrossRuntimeLanguages() {
        val files = ScriptFiles(InstrumentationRegistry.getInstrumentation().targetContext)
        val name = "guestnames" + System.nanoTime().toString().takeLast(8)
        try {
            files.createScript("$name.py")
            assertTrue(runCatching { files.createScript("$name.js") }.isFailure)
            assertTrue(runCatching { files.createScript("$name.ts") }.isFailure)
            assertTrue(runCatching { files.createScript("$name.lua") }.isFailure)
            assertTrue(runCatching { files.createFolder(name) }.isFailure)
        } finally { runCatching { files.deleteProject(name) } }
    }

    @Test fun pythonAndLuaCommandsExecuteInTheWorker() = runBlocking {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        val prefix = "guest" + System.nanoTime().toString().takeLast(8)
        try {
            for ((extension, source) in listOf(
                "py" to "def main(ctx):\n    return {\"type\": \"text\", \"text\": ctx[\"text\"].upper()}\n",
                "lua" to "function main(ctx) return {type='text', text=string.upper(ctx.text)} end",
            )) {
                val name = prefix + extension
                val file = workspace.files.createScript("$name.$extension")
                workspace.files.writeFile(name, file.name, source)
                assertTrue("Guest failed to register: ${workspace.logs()}", workspace.reload().any { it.name == name })
                assertEquals("HELLO", JSONObject(workspace.execute(name, "/$name hello", "guest-chat", 1L)).getString("text"))
            }
        } finally {
            listOf("py", "lua").forEach { runCatching { workspace.files.deleteProject(prefix + it) } }
            workspace.close()
        }
    }

    @Test fun javascriptOrchestratesPythonAndLuaWithJson() = runBlocking {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        val name = "mixed" + System.nanoTime().toString().takeLast(8)
        try {
            workspace.files.createFolder(name)
            workspace.files.createFile(name, "logic.py")
            workspace.files.createFile(name, "logic.lua")
            workspace.files.writeFile(name, "logic.py", "def main(ctx):\n    return ctx[\"value\"] * 2\n")
            workspace.files.writeFile(name, "logic.lua", "function main(ctx) return ctx.value + 2 end")
            workspace.files.writeFile(name, "main.js", """
                annie.commands.register({name:'$name', async execute() {
                    const python = await annie.runtime.call('logic.py', {value:20});
                    const lua = await annie.runtime.call('logic.lua', {value:python});
                    return annie.messages.text(String(lua));
                }});
            """.trimIndent())
            assertTrue(workspace.reload().any { it.name == name })
            assertEquals("42", JSONObject(workspace.execute(name, "/$name", "guest-chat", 2L)).getString("text"))
        } finally { runCatching { workspace.files.deleteProject(name) }; workspace.close() }
    }

    @Test fun nativeInterpreterRejectsHostAccessAndRecovers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            GuestRuntimeClient.run(context, "python", "bad.py", "import os\ndef main(ctx): return 1", "main", "{}")
            fail("Python host access was accepted")
        } catch (failure: IllegalStateException) { assertTrue(failure.message.orEmpty().contains("math")) }
        assertEquals("42", GuestRuntimeClient.run(context, "python", "ok.py", "def main(ctx): return 42", "main", "{}"))
        assertEquals("42", GuestRuntimeClient.run(context, "lua", "ok.lua", "function main(ctx) return 42 end", "main", "{}"))
    }
}
