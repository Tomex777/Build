package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** L4: a provider package's AnnieError crosses services.call with code/operation/retryable/retryAfterMs intact. */
@RunWith(AndroidJUnit4::class)
class ServiceErrorTransportInstrumentedTest {
    @Test fun providerErrorsAndGateFailuresKeepTheirCodesAcrossServicesCall() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString().takeLast(8)
        val providerId = "com.example.errprovider.$suffix"
        val consumerId = "com.example.errconsumer.$suffix"
        val permission = servicePermission(providerId, "boom")
        val command = "errsvc$suffix"
        val providerZip = tempZip("errprovider-$suffix")
        val consumerZip = tempZip("errconsumer-$suffix")
        writeZip(providerZip, mapOf(
            "manifest.json" to JSONObject().put("packageId", providerId).put("displayName", "Provider")
                .put("version", "1.0.0").put("apiVersion", "1").put("entryPoint", "main.js")
                .put("services", JSONArray().put(JSONObject().put("name", "boom").put("version", "1").put("input", "json").put("output", "json")))
                .toString(),
            "main.js" to """
                |function fail(code, extra) {
                |  const e = new Error("provider says " + code + " token=SECRET123");
                |  e.code = code; e.operation = "provider.op"; Object.assign(e, extra || {}); throw e;
                |}
                |annie.services.provide("boom", async input => {
                |  if (input.mode === "rate") fail("RATE_LIMITED", { retryable: true, retryAfterMs: 1500 });
                |  if (input.mode === "invalid") fail("INVALID_ARGUMENT");
                |  if (input.mode === "network") fail("NETWORK_ERROR", { retryable: true });
                |  if (input.mode === "plain") throw new Error("plain failure");
                |  return { ok: true };
                |});
            """.trimMargin(),
        ))
        writeZip(consumerZip, mapOf(
            "manifest.json" to JSONObject().put("packageId", consumerId).put("displayName", "Consumer")
                .put("version", "1.0.0").put("apiVersion", "1").put("entryPoint", "main.js")
                .put("permissions", JSONArray().put(permission))
                .put("capabilities", JSONArray().put(SERVICE_INVOKE_CAPABILITY))
                .put("dependencies", JSONObject().put(providerId, "1.0.0"))
                .put("serviceDependencies", JSONArray().put(JSONObject()
                    .put("packageId", providerId).put("name", "boom").put("version", "1").put("input", "json").put("output", "json")))
                .toString(),
            "main.js" to """
                |annie.commands.register({ name: "$command", async execute(ctx) {
                |  try {
                |    await annie.services.call("$providerId", "boom", { mode: String(ctx.args[0]) });
                |    return { type: "text", text: JSON.stringify({ ok: true }) };
                |  } catch (e) {
                |    return { type: "text", text: JSON.stringify({ code: e.code, operation: e.operation, retryable: e.retryable,
                |      retryAfterMs: e.retryAfterMs == null ? null : e.retryAfterMs, permission: e.permission == null ? null : e.permission,
                |      message: e.message, hasCause: e.cause !== undefined }) };
                |  }
                |} });
            """.trimMargin(),
        ))
        val workspace = ScriptWorkspace(context)
        val installed = mutableListOf<String>()
        try {
            val provider = AnniePackageArchive.install(context, providerZip).also { installed += it.id }
            val consumer = AnniePackageArchive.install(context, consumerZip).also { installed += it.id }
            workspace.files.setEnabled(provider.id, true)
            workspace.files.setEnabled(consumer.id, true)
            workspace.reload()
            var messageId = 0L
            suspend fun call(mode: String): JSONObject {
                val reply = JSONObject(requireNotNull(workspace.execute(command, "/$command $mode", "err-chat", ++messageId)))
                return JSONObject(reply.optString("text"))
            }

            val ungranted = call("rate")
            assertEquals("NOT_GRANTED", ungranted.getString("code"))
            assertEquals(permission, ungranted.getString("permission"))
            assertEquals("services.call", ungranted.getString("operation"))

            workspace.files.setGrantedPermissions(consumer.id, setOf(permission))
            workspace.reload()

            val rate = call("rate")
            assertEquals("RATE_LIMITED", rate.getString("code"))
            assertEquals("provider.op", rate.getString("operation"))
            assertTrue(rate.getBoolean("retryable"))
            assertEquals(1500, rate.getInt("retryAfterMs"))
            assertFalse("secrets must be redacted from the message", rate.getString("message").contains("SECRET123"))
            assertFalse("cause must never reach the caller", rate.getBoolean("hasCause"))

            assertEquals("INVALID_ARGUMENT", call("invalid").getString("code"))
            val network = call("network")
            assertEquals("NETWORK_ERROR", network.getString("code"))
            assertTrue(network.getBoolean("retryable"))
            assertEquals("INTERNAL", call("plain").getString("code"))
            assertTrue(call("ok").getBoolean("ok"))

            workspace.files.setEnabled(provider.id, false)
            workspace.reload()
            assertEquals("UNAVAILABLE", call("ok").getString("code"))
        } finally {
            workspace.close()
            installed.forEach { runCatching { workspace.files.deleteProject(it) } }
            providerZip.delete()
            consumerZip.delete()
        }
    }

    private fun tempZip(label: String): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "annie-package-$label-${System.nanoTime()}.zip",
    )

    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
