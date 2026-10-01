package com.tomex777.annie

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptEnvTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun environmentScreenKeepsSavedSecretHidden(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "envscreen" + System.nanoTime().toString().takeLast(8)
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(name, script.name, """
            annie.env.define({title: "Connection", fields: [
                {key: "server", type: "text", label: "Server", default: "https://example.test"},
                {key: "token", type: "secret", label: "Access key"}
            ]});
        """.trimIndent())
        val workspace = ScriptWorkspace(context)
        val secret = "private-env-screen-" + System.nanoTime()
        try {
            workspace.reload()
            workspace.setEnvValue(name, "token", secret)
            compose.setContent { AnnieTheme { ScriptStudioSheet(workspace, {}, initialProjectId = name, openEnvironment = true) } }
            compose.onNodeWithTag("script_env_secret_token").assertExists()
            compose.onNodeWithText("Access key · configured").assertExists()
            assertTrue(compose.onAllNodesWithText(secret, substring = true).fetchSemanticsNodes().isEmpty())
            hideEmulatorKeyboard(compose.activity)
            saveEmulatorScreenshot("annie-env-secrets-configured")
        } finally {
            workspace.close()
            files.deleteProject(name)
        }
    }

    @Test fun thrownSecretStaysOutOfCommandActionSessionErrorsAndStudioLogs() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "enverror" + System.nanoTime().toString().takeLast(8)
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(name, script.name, """
            annie.env.define({fields: [{key: "token", type: "secret", label: "Token"}]});
            const fail = async () => { throw new Error("Request rejected: " + await annie.env.secret("token")); };
            annie.actions.register("fail", fail);
            annie.sessions.register({name: "fails", onMessage: fail});
            annie.commands.register({name: "$name", async execute(ctx) {
                if (ctx.text.includes("start")) { ctx.session.start("fails"); return annie.messages.text("Ready"); }
                return fail();
            }});
        """.trimIndent())
        val workspace = ScriptWorkspace(context)
        val secret = "private-error-token-" + System.nanoTime()
        try {
            workspace.reload()
            workspace.setEnvValue(name, "token", secret)
            val command = workspace.execute(name, "/$name", "env-errors", 1L).orEmpty()
            val action = workspace.executeAction(name, "fail", "{}", "env-errors", 2L)!!.resultJson
            workspace.execute(name, "/$name start", "env-errors", 3L)
            val session = workspace.executeSession("fail", "env-errors", 4L)!!.resultJson
            listOf(command, action, session).forEach { result ->
                assertEquals("error", JSONObject(result).getString("type"))
                assertFalse(result.contains(secret))
                assertFalse(result.contains("Request rejected"))
            }
            assertTrue(workspace.logs().any { it.level == "ERROR" && "[redacted]" in it.message })
            assertTrue(workspace.logs().none { secret in it.message })
        } finally {
            workspace.close()
            files.deleteProject(name)
        }
    }

    @Test fun envPersistsAndKeepsSecretsOutOfBulkReadsAndLogs() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "envproof" + System.nanoTime().toString().takeLast(8)
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(
            name,
            script.name,
            """
                |annie.env.define({
                |  title: "ENV proof",
                |  fields: [
                |    { key: "enabled", type: "switch", label: "Enabled", default: true, scriptWritable: true },
                |    { key: "token", type: "secret", label: "Token" },
                |    { key: "quality", type: "select", label: "Quality", options: ["720p", "1080p"], default: "1080p", scriptWritable: true }
                |  ]
                |});
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    const enabled = await annie.env.get("enabled");
                |    const token = await annie.env.secret("token");
                |    const values = annie.env.values();
                |    annie.log.info("token=" + token);
                |    return annie.messages.text(JSON.stringify({
                |      enabled,
                |      quality: values.quality,
                |      hasToken: !!token,
                |      tokenInValues: Object.prototype.hasOwnProperty.call(values, "token")
                |    }));
                |  }
                |});
            """.trimMargin(),
        )

        val secret = "super-secret-" + System.nanoTime()
        var workspace = ScriptWorkspace(context)
        try {
            workspace.reload()
            val definition = workspace.envDefinition(name)
            assertNotNull(definition)
            assertEquals("ENV proof", definition?.title)
            assertEquals(3, definition?.fields?.size)

            workspace.setEnvValue(name, "enabled", false)
            workspace.setEnvValue(name, "quality", "720p")
            workspace.setEnvValue(name, "token", secret)

            val result = JSONObject(workspace.execute(name, "/$name", "env-test", 1L).orEmpty())
            val payload = JSONObject(result.getString("text"))
            assertFalse(payload.getBoolean("enabled"))
            assertEquals("720p", payload.getString("quality"))
            assertTrue(payload.getBoolean("hasToken"))
            assertFalse(payload.getBoolean("tokenInValues"))
            assertTrue(workspace.logs().none { secret in it.message })

            val safeId = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val encrypted = context.getSharedPreferences("annie_script_env_secrets_$safeId", android.content.Context.MODE_PRIVATE)
                .getString("token", null)
            assertNotNull(encrypted)
            assertFalse(encrypted.orEmpty().contains(secret))
        } finally {
            workspace.close()
        }

        workspace = ScriptWorkspace(context)
        try {
            workspace.reload()
            assertEquals(false, workspace.envValue(name, "enabled"))
            assertEquals("720p", workspace.envValue(name, "quality"))
            assertTrue(workspace.envHasSecret(name, "token"))
        } finally {
            workspace.close()
            runCatching { files.deleteProject(name) }
            val safeId = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
            context.getSharedPreferences("annie_script_env_$safeId", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("annie_script_env_secrets_$safeId", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}

