package com.tomex777.annie

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
