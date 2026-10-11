package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pins annie.files.list against the field report where list("selftest") did not show a.txt after
 * writeText("selftest/a.txt"). Source resolves both through the same per-project data root, so this
 * test exists to prove or disprove the report on a device and keep the behaviour fixed.
 */
@RunWith(AndroidJUnit4::class)
class ScriptFilesListTest {
    @Test fun writeTextInSubfolderIsListedAndSorted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "fileslist${System.nanoTime().toString().takeLast(7)}"
        val source = """
            annie.commands.register({
              name: "$name",
              async execute() {
                await annie.files.writeText("selftest/b.txt", "bee");
                await annie.files.writeText("selftest/A.txt", "ay");
                await annie.files.writeText("selftest/nested/c.txt", "see");
                const inside = await annie.files.list("selftest");
                const root = await annie.files.list("");
                return { type: "text", text: JSON.stringify({ inside, root }) };
              }
            });
        """.trimIndent()
        val manifest = JSONObject()
            .put("packageId", "com.example.$name").put("displayName", "Files list").put("version", "1.0.0")
            .put("apiVersion", "1").put("entryPoint", "main.js")
            .put("commands", org.json.JSONArray().put(JSONObject().put("name", name).put("description", "files list")))
        val archive = File(context.cacheDir, "$name.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            mapOf("manifest.json" to manifest.toString(), "main.js" to source).forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        val files = ScriptFiles(context)
        val workspace = ScriptWorkspace(context)
        var projectId: String? = null
        try {
            val installed = AnniePackageArchive.install(context, archive)
            projectId = installed.id
            files.setEnabled(installed.id, true)
            workspace.reload()
            val result = JSONObject(requireNotNull(workspace.execute(name, "/$name", "files-list", 1L)))
            val payload = JSONObject(result.getString("text"))
            val inside = payload.getJSONArray("inside")
            val names = (0 until inside.length()).map { inside.getJSONObject(it).getString("name") }
            assertEquals(listOf("A.txt", "b.txt", "nested"), names) // case-insensitive sort, non-recursive
            assertTrue(inside.getJSONObject(2).getBoolean("directory"))
            assertEquals(2L, inside.getJSONObject(0).getLong("size"))
            val root = payload.getJSONArray("root")
            assertEquals("selftest", root.getJSONObject(0).getString("name"))
        } finally {
            workspace.close()
            projectId?.let { runCatching { files.deleteProject(it) } }
            archive.delete()
        }
    }
}
