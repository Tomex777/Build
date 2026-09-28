package com.tomex777.annie

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ScriptPackageArchiveTest {
    @Test fun androidDeviceInfoBridgeNeedsDeclaredCapabilityAndUserPermission() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString().takeLast(8)
        val name = "device-$suffix"
        val permission = ANDROID_DEVICE_INFO_PERMISSION
        val archive = tempZip(name)
        val manifest = JSONObject().put("packageId", "com.example.$name").put("displayName", "Device Info")
            .put("version", "1.0.0").put("apiVersion", "1").put("entryPoint", "main.js")
            .put("permissions", org.json.JSONArray().put(permission))
            .put("capabilities", org.json.JSONArray().put(ANDROID_DEVICE_INFO_CAPABILITY))
        writeZip(archive, mapOf(
            "manifest.json" to manifest.toString(),
            "main.js" to """
                |annie.commands.register({ name: "$name", async execute() {
                |  const info = await annie.android.deviceInfo();
                |  return { type: "text", text: JSON.stringify(info) };
                |} });
            """.trimMargin(),
        ))
        val workspace = ScriptWorkspace(context)
        var installedId: String? = null
        try {
            val installed = AnniePackageArchive.install(context, archive)
            installedId = installed.id
            workspace.files.setEnabled(installed.id, true)
            workspace.reload()
            val notGranted = JSONObject(requireNotNull(workspace.execute(name, "/$name", "android-bridge", 1L)))
            assertEquals("error", notGranted.optString("type"))
            assertTrue(notGranted.optString("text").contains("has not been granted"))

            workspace.files.setGrantedPermissions(installed.id, setOf(permission))
            manifest.remove("capabilities")
            File(workspace.files.root, "${installed.id}/manifest.json").writeText(manifest.toString())
            workspace.reload()
            val noCapability = JSONObject(requireNotNull(workspace.execute(name, "/$name", "android-bridge", 2L)))
            assertEquals("error", noCapability.optString("type"))
            assertTrue(noCapability.optString("text").contains("does not declare capability"))

            manifest.put("capabilities", org.json.JSONArray().put(ANDROID_DEVICE_INFO_CAPABILITY)).remove("permissions")
            File(workspace.files.root, "${installed.id}/manifest.json").writeText(manifest.toString())
            workspace.reload()
            val noPermission = JSONObject(requireNotNull(workspace.execute(name, "/$name", "android-bridge", 3L)))
            assertEquals("error", noPermission.optString("type"))
            assertTrue(noPermission.optString("text").contains("does not declare permission"))

            manifest.put("permissions", org.json.JSONArray().put(permission))
            File(workspace.files.root, "${installed.id}/manifest.json").writeText(manifest.toString())
            workspace.files.setGrantedPermissions(installed.id, setOf(permission))
            workspace.reload()
            val result = JSONObject(requireNotNull(workspace.execute(name, "/$name", "android-bridge", 4L)))
            assertEquals("Unexpected Android bridge response: $result", "text", result.optString("type"))
            val info = JSONObject(result.optString("text"))
            assertEquals("android", info.optString("platform"))
            assertTrue(info.optInt("apiLevel") >= 21)
            assertTrue(info.optString("locale").isNotBlank())
            assertFalse("The narrow bridge must not expose hardware identity", info.has("deviceId"))
            assertFalse("The narrow bridge must not expose phone model details", info.has("model"))
        } finally {
            workspace.close()
            installedId?.let { runCatching { workspace.files.deleteProject(it) } }
            archive.delete()
        }
    }

    @Test fun androidCapabilitiesNeedIndependentGrantsAndStayDataOnly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString().takeLast(8)
        val name = "native-$suffix"
        val archive = tempZip(name)
        val permissions = linkedSetOf(ANDROID_TTS_PERMISSION, ANDROID_OCR_PERMISSION, ANDROID_STT_PERMISSION)
        val capabilities = linkedSetOf(ANDROID_TTS_CAPABILITY, ANDROID_OCR_CAPABILITY, ANDROID_STT_CAPABILITY)
        val manifest = JSONObject()
            .put("packageId", "com.example.$name")
            .put("displayName", "Native Capabilities")
            .put("version", "1.0.0")
            .put("apiVersion", "1")
            .put("entryPoint", "main.js")
            .put("permissions", org.json.JSONArray(permissions.toList()))
            .put("capabilities", org.json.JSONArray(capabilities.toList()))
            .put("assets", org.json.JSONArray().put(
                JSONObject().put("id", "scan").put("path", "assets/scan.png")
            ))
        writeZip(archive, mapOf(
            "manifest.json" to manifest.toString(),
            "assets/scan.png" to "fake-image-is-never-decoded-by-the-test-backend",
            "main.js" to """
                |annie.commands.register({ name: "$name", async execute() {
                |  const tts = await annie.android.tts.speak("Hello Annie", { language: "en-US" });
                |  const ocr = await annie.android.ocr.asset("scan");
                |  const stt = await annie.android.stt.listen({ language: "en-US", prompt: "Say Annie" });
                |  return { type: "text", text: JSON.stringify({ tts, ocr, stt }) };
                |} });
            """.trimMargin(),
        ))
        val backend = object : AndroidCapabilityBackend {
            override suspend fun speak(text: String, languageTag: String?) =
                JSONObject().put("queued", text == "Hello Annie").put("language", languageTag ?: "")
            override suspend fun recognizeText(imageFile: File) =
                JSONObject().put("text", "ANNIE OCR").put("assetName", imageFile.name)
            override suspend fun listen(languageTag: String?, prompt: String?) =
                JSONObject().put("status", "recognized").put("text", "Annie voice")
                    .put("language", languageTag ?: "").put("prompt", prompt ?: "")
        }
        val workspace = ScriptWorkspace(context, backend)
        var installedId: String? = null
        try {
            val installed = AnniePackageArchive.install(context, archive)
            installedId = installed.id
            workspace.files.setEnabled(installed.id, true)
            workspace.reload()

            suspend fun execute(id: Long) =
                JSONObject(requireNotNull(workspace.execute(name, "/$name", "native-capability", id)))

            val noGrant = execute(1L)
            assertEquals("error", noGrant.optString("type"))
            assertTrue(noGrant.optString("text").contains(ANDROID_TTS_PERMISSION))

            workspace.files.setGrantedPermissions(installed.id, setOf(ANDROID_TTS_PERMISSION))
            val ocrDenied = execute(2L)
            assertEquals("error", ocrDenied.optString("type"))
            assertTrue(ocrDenied.optString("text").contains(ANDROID_OCR_PERMISSION))

            workspace.files.setGrantedPermissions(installed.id, setOf(ANDROID_TTS_PERMISSION, ANDROID_OCR_PERMISSION))
            val sttDenied = execute(3L)
            assertEquals("error", sttDenied.optString("type"))
            assertTrue(sttDenied.optString("text").contains(ANDROID_STT_PERMISSION))

            workspace.files.setGrantedPermissions(installed.id, permissions)
            val allowed = execute(4L)
            assertEquals("text", allowed.optString("type"))
            val payload = JSONObject(allowed.optString("text"))
            assertTrue(payload.getJSONObject("tts").optBoolean("queued"))
            assertEquals("ANNIE OCR", payload.getJSONObject("ocr").optString("text"))
            assertEquals("scan.png", payload.getJSONObject("ocr").optString("assetName"))
            assertEquals("recognized", payload.getJSONObject("stt").optString("status"))
            assertEquals("Annie voice", payload.getJSONObject("stt").optString("text"))

            val activityInfo = context.packageManager.getActivityInfo(
                ComponentName(context, AnnieSpeechRecognitionActivity::class.java),
                0,
            )
            assertFalse("Speech recognition broker activity must never be exported", activityInfo.exported)
        } finally {
            workspace.close()
            installedId?.let { runCatching { workspace.files.deleteProject(it) } }
            archive.delete()
        }
    }

    @Test fun interPackageServiceNeedsDeclaredDependencyAndGrantedPermission() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString().takeLast(8)
        val providerId = "com.example.provider.$suffix"
        val consumerId = "com.example.consumer.$suffix"
        val permission = servicePermission(providerId, "greeting")
        val providerZip = tempZip("provider-$suffix")
        val consumerZip = tempZip("consumer-$suffix")
        val providerManifest = JSONObject().put("packageId", providerId).put("displayName", "Provider")
            .put("version", "1.0.0").put("apiVersion", "1").put("entryPoint", "main.js")
            .put("services", org.json.JSONArray().put(JSONObject().put("name", "greeting").put("version", "1").put("input", "json").put("output", "json")))
        val consumerManifest = JSONObject().put("packageId", consumerId).put("displayName", "Consumer")
            .put("version", "1.0.0").put("apiVersion", "1").put("entryPoint", "main.js")
            .put("permissions", org.json.JSONArray().put(permission))
            .put("capabilities", org.json.JSONArray().put(SERVICE_INVOKE_CAPABILITY))
            .put("serviceDependencies", org.json.JSONArray().put(JSONObject()
                .put("packageId", providerId).put("name", "greeting").put("version", "1")
                .put("input", "json").put("output", "json")))
        writeZip(providerZip, mapOf(
            "manifest.json" to providerManifest.toString(),
            "main.js" to "annie.services.provide('greeting', async input => ({ greeting: 'Hello ' + input.name }));",
        ))
        writeZip(consumerZip, mapOf(
            "manifest.json" to consumerManifest.toString(),
            "main.js" to """
                |annie.commands.register({ name: "service-$suffix", async execute() {
                |  const reply = await annie.services.call("$providerId", "greeting", { name: "Annie" });
                |  return { type: "text", text: reply.greeting };
                |} });
            """.trimMargin(),
        ))
        val workspace = ScriptWorkspace(context)
        val installedIds = mutableListOf<String>()
        try {
            val provider = AnniePackageArchive.install(context, providerZip).also { installedIds += it.id }
            val consumer = AnniePackageArchive.install(context, consumerZip).also { installedIds += it.id }
            workspace.files.setEnabled(provider.id, true)
            workspace.files.setEnabled(consumer.id, true)
            workspace.reload()
            val denied = JSONObject(requireNotNull(workspace.execute("service-$suffix", "/service-$suffix", "service-chat", 1L)))
            assertEquals("error", denied.optString("type"))
            assertTrue(denied.optString("text").contains("has not been granted"))

            workspace.files.setGrantedPermissions(consumer.id, setOf(permission))
            assertTrue("User grants must be durable", permission in ScriptFiles(context).grantedPermissions(consumer.id))
            workspace.reload()
            val undeclared = JSONObject(requireNotNull(workspace.execute("service-$suffix", "/service-$suffix", "service-chat", 2L)))
            assertEquals("error", undeclared.optString("type"))
            assertTrue(undeclared.optString("text").contains("does not declare dependency"))

            val manifestFile = File(workspace.files.root, "${consumer.id}/manifest.json")
            manifestFile.writeText(consumerManifest.put("dependencies", JSONObject().put(providerId, "1.0.0")).toString())
            workspace.reload()
            val allowed = JSONObject(requireNotNull(workspace.execute("service-$suffix", "/service-$suffix", "service-chat", 2L)))
            assertEquals("Hello Annie", allowed.optString("text"))

            workspace.files.setEnabled(provider.id, false)
            workspace.reload()
            val disabled = JSONObject(requireNotNull(workspace.execute("service-$suffix", "/service-$suffix", "service-chat", 3L)))
            assertEquals("error", disabled.optString("type"))
            assertTrue(disabled.optString("text").contains("disabled"))
        } finally {
            workspace.close()
            installedIds.forEach { runCatching { workspace.files.deleteProject(it) } }
            providerZip.delete()
            consumerZip.delete()
        }
    }

    @Test fun importsManifestPackageAssetsAndScriptsDisabledWithoutExecuting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "zipproof${System.nanoTime().toString().takeLast(7)}"
        val archive = tempZip(name)
        val source = """
            annie.commands.register({
              name: "$name",
              async execute() {
                const previous = await annie.storage.get("relaunch-marker");
                await annie.storage.set("relaunch-marker", previous || "stored");
                await annie.files.writeText("state.txt", "private package data");
                return annie.messages.image({ uri: annie.assets.image("board"), audio: annie.assets.audio("move"), caption: previous || "first-run" });
              }
            });
        """.trimIndent()
        val manifest = JSONObject()
            .put("packageId", "com.example.$name")
            .put("displayName", "ZIP Proof")
            .put("version", "1.0.0")
            .put("apiVersion", "1")
            .put("entryPoint", "src/main.js")
            .put("assets", org.json.JSONArray()
                .put(JSONObject().put("id", "board").put("path", "assets/board.webp"))
                .put(JSONObject().put("id", "copy").put("path", "assets/caption.txt"))
                .put(JSONObject().put("id", "move").put("path", "sounds/move.ogg")))
        writeZip(archive, mapOf(
            "manifest.json" to manifest.toString(),
            "src/main.js" to source,
            "src/rules.js" to "export const value = 7;",
            "assets/board.webp" to "board-resource",
            "assets/caption.txt" to "board from package assets",
            "sounds/move.ogg" to "move-resource",
        ))
        val files = ScriptFiles(context)
        var workspace = ScriptWorkspace(context)
        var projectId: String? = null
        try {
            val preview = AnniePackageArchive.inspect(archive)
            assertEquals("src/main.js", preview.manifest.entryPoint)
            assertEquals(2, preview.javaScriptFiles.size)
            assertEquals(listOf("assets/board.webp"), preview.imageFiles)
            assertEquals(listOf("sounds/move.ogg"), preview.audioFiles)

            val imported = AnniePackageArchive.install(context, archive)
            projectId = imported.id
            assertFalse("Imported package must stay disabled", imported.enabled)
            val initialState = requireNotNull(files.installedPackageState(imported.id))
            assertEquals("com.example.$name", initialState.packageId)
            assertFalse(initialState.enabled)
            assertTrue(initialState.installedAtMillis > 0L)
            assertEquals("com.example.$name", imported.manifest.packageId)
            assertEquals(source, imported.files["src/main.js"])
            runCatching { AnniePackageArchive.install(context, archive) }
                .onSuccess { error("Duplicate package IDs must be rejected") }
                .onFailure { assertTrue(it.message.orEmpty().contains("already installed")) }
            assertEquals("board-resource", File(files.root, "${imported.id}/assets/board.webp").readText())
            assertEquals("move-resource", File(files.root, "${imported.id}/sounds/move.ogg").readText())
            assertEquals(File(files.root, "${imported.id}/assets/board.webp").canonicalFile, files.resolveAssetFile(imported.id, "board").canonicalFile)
            assertEquals(File(files.root, "${imported.id}/sounds/move.ogg").canonicalFile, files.resolveAssetFile(imported.id, "move").canonicalFile)
            assertEquals("board from package assets", files.readAssetText(imported.id, "copy"))
            runCatching { files.resolveAssetFile(imported.id, "../other-package/secret") }
                .onSuccess { error("Package asset IDs must not allow cross-package paths") }

            val commands = workspace.reload()
            assertFalse("Import must not execute or register package code", commands.any { it.name == name })
            files.setEnabled(imported.id, true)
            assertTrue(workspace.reload().any { it.name == name })
            val result = JSONObject(requireNotNull(workspace.execute(name, "/$name", "zip-test", 1L)))
            assertEquals("annie-asset://board", result.optString("uri"))
            assertEquals("annie-asset://move", result.optString("audio"))
            assertEquals("first-run", result.optString("caption"))
            assertEquals("private package data", File(context.filesDir, "annie-script-data/${imported.id}/state.txt").readText())

            workspace.close()
            workspace = ScriptWorkspace(context)
            val restoredProject = ScriptFiles(context).listProjects().single { it.id == imported.id }
            assertTrue("Package enabled state must survive workspace recreation", restoredProject.enabled)
            assertEquals(initialState.installedAtMillis, files.installedPackageState(imported.id)?.installedAtMillis)
            assertTrue(workspace.reload().any { it.name == name })
            val afterRestart = JSONObject(requireNotNull(workspace.execute(name, "/$name", "zip-test", 2L)))
            assertEquals("Persistent script storage must survive runtime recreation", "stored", afterRestart.optString("caption"))

            context.getSharedPreferences("annie_script_env_${imported.id}", android.content.Context.MODE_PRIVATE)
                .edit().putString("saved-setting", "value").commit()
            context.getSharedPreferences("annie_script_env_secrets_${imported.id}", android.content.Context.MODE_PRIVATE)
                .edit().putString("saved-secret", "ciphertext").commit()
            context.getSharedPreferences("annie_script_sessions", android.content.Context.MODE_PRIVATE)
                .edit().putString("package-lifecycle-test", JSONObject().put("scriptId", imported.id).put("sessionName", "flow").toString()).commit()
            workspace.close()
            workspace = ScriptWorkspace(context)
            files.deleteProject(imported.id)
            projectId = null
            assertFalse("Uninstall must remove the package directory", File(files.root, imported.id).exists())
            assertEquals(null, files.installedPackageState(imported.id))
            assertTrue(context.getSharedPreferences("annie_script_storage_${imported.id}", android.content.Context.MODE_PRIVATE).all.isEmpty())
            assertFalse("Uninstall must remove package-private files", File(context.filesDir, "annie-script-data/${imported.id}").exists())
            assertTrue(context.getSharedPreferences("annie_script_env_${imported.id}", android.content.Context.MODE_PRIVATE).all.isEmpty())
            assertTrue(context.getSharedPreferences("annie_script_env_secrets_${imported.id}", android.content.Context.MODE_PRIVATE).all.isEmpty())
            assertEquals(null, context.getSharedPreferences("annie_script_sessions", android.content.Context.MODE_PRIVATE).getString("package-lifecycle-test", null))
        } finally {
            workspace.close()
            projectId?.let { runCatching { files.deleteProject(it) } }
            archive.delete()
        }
    }

    @Test fun multiEntryArchiveRequiresExplicitEntrySelection() {
        val archive = tempZip("multi-entry")
        try {
            writeZip(archive, mapOf("chess.js" to "// chess", "cards.js" to "// cards"))
            val preview = AnniePackageArchive.inspect(archive)
            assertTrue(preview.requiresEntrySelection)
            assertEquals(listOf("cards.js", "chess.js"), preview.entryCandidates)
            runCatching { AnniePackageArchive.install(InstrumentationRegistry.getInstrumentation().targetContext, archive) }
                .onSuccess { error("Import must require entry selection") }
                .onFailure { assertTrue(it.message.orEmpty().contains("Choose a valid JavaScript entry point")) }
        } finally {
            archive.delete()
        }
    }

    @Test fun infersMainEntryAndLogicalAssetsWhenManifestIsOmitted() {
        val archive = tempZip("inferred")
        try {
            writeZip(archive, mapOf(
                "main.js" to "// project entry",
                "assets/board.webp" to "board bytes",
                "data/rules.json" to "{\"size\":8}",
            ))
            val preview = AnniePackageArchive.inspect(archive)
            assertEquals("main.js", preview.manifest.entryPoint)
            assertTrue(preview.manifest.generated)
            assertTrue(preview.manifest.assets.any { it.logicalId == "assets.board" && it.relativePath == "assets/board.webp" })
            assertTrue(preview.manifest.assets.any { it.logicalId == "data.rules" && it.relativePath == "data/rules.json" })
        } finally {
            archive.delete()
        }
    }

    @Test fun rejectsTraversalExecutableAndInvalidManifestBeforeInstallation() {
        val badArchives = listOf(
            mapOf("../escape.js" to "bad"),
            mapOf("main.js" to "ok", "payload.apk" to "not safe"),
            mapOf("assets" to "file", "assets/board.webp" to "nested file", "main.js" to "ok"),
            mapOf("manifest.json" to "{not-json", "main.js" to "ok"),
        )
        badArchives.forEachIndexed { index, entries ->
            val archive = tempZip("bad-$index")
            try {
                writeZip(archive, entries)
                runCatching { AnniePackageArchive.inspect(archive) }
                    .onSuccess { error("Unsafe archive $index must be rejected") }
            } finally {
                archive.delete()
            }
        }

        val wrongEntry = tempZip("wrong-entry")
        try {
            val manifest = JSONObject().put("packageId", "com.example.invalid")
                .put("displayName", "Invalid").put("version", "1.0.0")
                .put("apiVersion", "1").put("entryPoint", "missing.js")
            writeZip(wrongEntry, mapOf("manifest.json" to manifest.toString(), "main.js" to "ok"))
            runCatching { AnniePackageArchive.inspect(wrongEntry) }
                .onSuccess { error("Manifest entry point must be validated before installation") }
        } finally {
            wrongEntry.delete()
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
