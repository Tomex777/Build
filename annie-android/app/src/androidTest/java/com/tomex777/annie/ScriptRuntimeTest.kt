package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptRuntimeTest {
    @Test fun echoCommandRegistersAndRunsThroughQuickJs() = runBlocking {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val commands = workspace.reload()
            assertTrue(commands.any { it.name == "echo" })
            val response = JSONObject(workspace.execute("echo", "/echo hello from JavaScript", "test-chat", 42L))
            assertEquals("text", response.getString("type"))
            assertEquals("hello from JavaScript", response.getString("text"))
        } finally {
            workspace.close()
        }
    }

    @Test fun folderEntryImportsHelperModuleAndKeepsStorageIsolated() = runBlocking {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        val projectName = "module-proof-${System.nanoTime()}"
        try {
            workspace.files.createFolder(projectName)
            workspace.files.writeFile(projectName, "helper.js", "export const label = text => `from helper: ${'$'}{text}`;")
            workspace.files.writeFile(
                projectName, "main.js", """
                    |import { label } from "./helper.js";
                    |annie.commands.register({
                    |  name: "$projectName",
                    |  async execute(ctx) {
                    |    await annie.storage.set("last", ctx.text);
                    |    return { type: "text", text: label(await annie.storage.get("last")) };
                    |  }
                    |});
                """.trimMargin()
            )
            val commands = workspace.reload()
            assertTrue(commands.any { it.name == projectName })
            val response = JSONObject(workspace.execute(projectName, "/$projectName import works", "test-chat", 44L))
            assertEquals("from helper: import works", response.getString("text"))
        } finally {
            workspace.close()
        }
    }
}
