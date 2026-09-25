package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import android.net.Uri
import java.io.File
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

    @Test fun chessScriptGeneratesPersistentImageAndHandlesPlainTextMove() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val chatId = "chess-proof-${System.nanoTime()}"
        try {
            val commands = workspace.reload()
            assertTrue(commands.any { it.name == "chess" })

            val opening = JSONObject(workspace.execute("chess", "/chess new", chatId, 60L))
            assertEquals("image", opening.getString("type"))
            val openingFile = File(requireNotNull(Uri.parse(opening.getString("uri")).path))
            assertTrue("Generated opening board does not exist", openingFile.isFile && openingFile.length() > 10_000)

            val response = workspace.executeSession("e4", chatId, 61L)
            val board = JSONObject(response!!.resultJson)
            assertEquals("image", board.getString("type"))
            assertTrue(board.getString("caption").startsWith("Black played "))
            val replyFile = File(requireNotNull(Uri.parse(board.getString("uri")).path))
            assertTrue("Generated reply board does not exist", replyFile.isFile && replyFile.length() > 10_000)
            assertTrue("Board image did not change after moves", opening.getString("uri") != board.getString("uri"))
        } finally {
            workspace.close()
        }
    }

    @Test fun actionsAndMultiTurnSessionPersistAcrossWorkspaceRestart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val projectName = "sessionproof${System.nanoTime().toString().takeLast(8)}"
        val chatId = "session-chat-$projectName"
        try {
            workspace.files.createFolder(projectName)
            workspace.files.writeFile(
                projectName, "main.js", """
                    |annie.sessions.register({
                    |  name: "conversation",
                    |  async onMessage(ctx) {
                    |    if (ctx.text === "done") {
                    |      ctx.session.end();
                    |      return { type: "text", text: "session ended" };
                    |    }
                    |    return { type: "text", text: "session: " + ctx.text };
                    |  }
                    |});
                    |annie.actions.register("pick", async (payload) => ({
                    |  type: "text",
                    |  text: "picked " + payload.value
                    |}));
                    |annie.commands.register({
                    |  name: "$projectName",
                    |  async execute(ctx) {
                    |    ctx.session.start("conversation");
                    |    return {
                    |      type: "options",
                    |      title: "Pick",
                    |      options: [{ id: "seven", label: "Seven", action: "pick", payload: { value: 7 } }]
                    |    };
                    |  }
                    |});
                """.trimMargin()
            )
            workspace.reload()
            val commandResult = JSONObject(workspace.execute(projectName, "/$projectName", chatId, 70L))
            assertEquals("options", commandResult.getString("type"))

            val action = workspace.executeAction(projectName, "pick", """{"value":7}""", chatId, 71L)
            assertEquals("picked 7", JSONObject(action!!.resultJson).getString("text"))

            val firstSession = workspace.executeSession("hello", chatId, 72L)
            assertEquals("session: hello", JSONObject(firstSession!!.resultJson).getString("text"))
        } finally {
            workspace.close()
        }

        val restored = ScriptWorkspace(context)
        try {
            restored.reload()
            val afterRestart = restored.executeSession("again", chatId, 73L)
            assertEquals("session: again", JSONObject(afterRestart!!.resultJson).getString("text"))
            val ended = restored.executeSession("done", chatId, 74L)
            assertEquals("session ended", JSONObject(ended!!.resultJson).getString("text"))
            assertEquals(null, restored.executeSession("after", chatId, 75L))
        } finally {
            runCatching { restored.files.deleteProject(projectName) }
            restored.close()
        }
    }

    @Test fun standaloneScriptCanBeEditedWithoutCreatingABogusDirectory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val projectName = "editproof${System.nanoTime().toString().takeLast(8)}"
        try {
            val file = workspace.files.createScript(projectName)
            val source = """
                |annie.commands.register({
                |  name: "$projectName",
                |  async execute(ctx) { return { type: "text", text: "edited " + ctx.text }; }
                |});
            """.trimMargin()
            workspace.files.writeFile(projectName, file.name, source)
            assertEquals(source, workspace.files.readFile(projectName, file.name))
            assertTrue(!java.io.File(workspace.files.root, projectName).exists())
            workspace.reload()
            val response = JSONObject(workspace.execute(projectName, "/$projectName yes", "edit-chat", 80L))
            assertEquals("edited yes", response.getString("text"))
        } finally {
            runCatching { workspace.files.deleteProject(projectName) }
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
