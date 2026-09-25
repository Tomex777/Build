package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import android.net.Uri
import android.os.SystemClock
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptRuntimeTest {
    @Test fun echoCommandRegistersAndRunsThroughQuickJs() = runBlocking {
        val workspace = ScriptWorkspace(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val commands = workspace.reload()
            assertTrue("Echo script did not register: ${workspace.logs()}", commands.any { it.name == "echo" })
            val response = JSONObject(workspace.execute("echo", "/echo hello from JavaScript", "test-chat", 42L))
            assertEquals("text", response.getString("type"))
            assertEquals("hello from JavaScript", response.getString("text"))
        } finally {
            workspace.close()
        }
    }

    @Test fun disablingScriptRemovesItsCommandWithoutDeletingSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val name = "toggleproof${System.nanoTime().toString().takeLast(8)}"
        try {
            workspace.files.createScript(name)
            val enabledCommands = workspace.reload()
            assertTrue("Created script did not register: ${workspace.logs()}", enabledCommands.any { it.name == name })
            workspace.files.setEnabled(name, false)
            assertFalse(workspace.reload().any { it.name == name })
            assertTrue(workspace.files.listProjects().first { it.id == name }.files.isNotEmpty())
            workspace.files.setEnabled(name, true)
            assertTrue(workspace.reload().any { it.name == name })
        } finally {
            runCatching { workspace.files.deleteProject(name) }
            workspace.close()
        }
    }

    @Test fun syntaxRuntimeAndInfiniteLoopFailuresStayInsideScriptRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val syntax = "syntaxproof${System.nanoTime().toString().takeLast(8)}"
        val boom = "boomproof${System.nanoTime().toString().takeLast(8)}"
        val spin = "spinproof${System.nanoTime().toString().takeLast(8)}"
        try {
            val syntaxFile = workspace.files.createScript(syntax)
            workspace.files.writeFile(syntax, syntaxFile.name, "annie.commands.register({ name: \"$syntax\", execute( {")
            assertFalse(workspace.reload().any { it.name == syntax })
            assertTrue(workspace.logs().any { it.scriptId == syntax && it.level == "ERROR" })

            val boomFile = workspace.files.createScript(boom)
            workspace.files.writeFile(
                boom, boomFile.name,
                """annie.commands.register({ name: "$boom", execute() { throw new Error("boom"); } });"""
            )
            val spinFile = workspace.files.createScript(spin)
            workspace.files.writeFile(
                spin, spinFile.name,
                """annie.commands.register({ name: "$spin", execute() { while (true) {} } });"""
            )
            workspace.reload()
            val thrown = JSONObject(workspace.execute(boom, "/$boom", "error-chat", 90L))
            assertEquals("error", thrown.getString("type"))

            val started = SystemClock.elapsedRealtime()
            val interrupted = JSONObject(workspace.execute(spin, "/$spin", "error-chat", 91L))
            val elapsed = SystemClock.elapsedRealtime() - started
            assertEquals("error", interrupted.getString("type"))
            assertTrue("Infinite loop was not interrupted promptly: ${elapsed}ms", elapsed < 12_000L)
        } finally {
            runCatching { workspace.files.deleteProject(syntax) }
            runCatching { workspace.files.deleteProject(boom) }
            runCatching { workspace.files.deleteProject(spin) }
            workspace.close()
        }
    }

    @Test fun asyncHttpBridgeReturnsNativeResponseWithoutBlockingComposeRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val name = "httpproof${System.nanoTime().toString().takeLast(8)}"
        try {
            val file = workspace.files.createScript(name)
            workspace.files.writeFile(
                name, file.name, """
                    |annie.commands.register({
                    |  name: "$name",
                    |  async execute() {
                    |    const response = await annie.http.request({
                    |      url: "https://example.com/",
                    |      method: "GET",
                    |      timeoutMs: 15000
                    |    });
                    |    return { type: "text", text: String(response.status) };
                    |  }
                    |});
                """.trimMargin()
            )
            workspace.reload()
            val response = JSONObject(workspace.execute(name, "/$name", "http-chat", 92L))
            assertEquals("text", response.getString("type"))
            assertEquals("200", response.getString("text"))
        } finally {
            runCatching { workspace.files.deleteProject(name) }
            workspace.close()
        }
    }

    @Test fun chessScriptGeneratesPersistentImageAndHandlesPlainTextMove() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val chatId = "chess-proof-${System.nanoTime()}"
        try {
            val commands = workspace.reload()
            assertTrue("Chess script did not register: ${workspace.logs()}", commands.any { it.name == "chess" })

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
            assertTrue("Project entry did not register: ${workspace.logs()}", commands.any { it.name == projectName })
            val response = JSONObject(workspace.execute(projectName, "/$projectName import works", "test-chat", 44L))
            assertEquals("from helper: import works", response.getString("text"))
        } finally {
            workspace.close()
        }
    }
}
