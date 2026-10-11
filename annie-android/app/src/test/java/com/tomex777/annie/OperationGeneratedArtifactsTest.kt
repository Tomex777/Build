package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** L5: generated artifacts must match the committed copies; plus the JS-binding L1 checks. */
class OperationGeneratedArtifactsTest {
    private class NoBackend : AndroidCapabilityBackend {
        override suspend fun speak(ownerPackageId: String, text: String, languageTag: String?, queueMode: String) = JSONObject()
        override suspend fun ttsStatus(ownerPackageId: String, utteranceId: String) = JSONObject()
        override suspend fun stopSpeech(ownerPackageId: String) = JSONObject()
        override suspend fun recognizeText(imageFile: File) = JSONObject()
        override suspend fun listen(languageTag: String?, prompt: String?) = JSONObject()
        override suspend fun pickTextDocument(mimeType: String) = JSONObject()
        override suspend fun inspectMedia(mediaFile: File) = JSONObject()
        override suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String) = JSONObject()
        override suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String) = JSONObject()
        override suspend fun cancelNotification(ownerPackageId: String, key: String) = JSONObject()
    }

    private val noAssets = object : PackageAssetResolver {
        override fun resolveAssetFile(projectId: String, logicalId: String): File = File("unused")
    }

    private fun allDefinitions() = CoreAndroidOperationProvider(NoBackend(), noAssets).operations + downloadOperationDefinitions() + messageOperationDefinitions()

    private fun committed(name: String): File {
        // Gradle runs unit tests with the module directory (app/) as the working directory.
        val candidates = listOf(File("../docs/generated/$name"), File("annie-android/docs/generated/$name"), File("docs/generated/$name"))
        return candidates.firstOrNull { it.parentFile?.isDirectory == true } ?: candidates.first()
    }

    private fun normalized(text: String) = text.replace("\r\n", "\n")

    @Test fun l5_generatedTypingsMatchCommittedFile() {
        val generated = OperationTypings.dts(allDefinitions())
        val file = committed("annie.generated.d.ts")
        if (System.getenv("ANNIE_UPDATE_GENERATED") == "1") {
            file.parentFile?.mkdirs()
            file.writeText(generated)
            return
        }
        assertTrue("Missing ${file.path}. Run with ANNIE_UPDATE_GENERATED=1 and commit it.", file.exists())
        assertEquals(
            "annie.generated.d.ts drifted from the registry. Regenerate with ANNIE_UPDATE_GENERATED=1 and review the diff.",
            normalized(file.readText()),
            generated,
        )
    }

    @Test fun l1_jsBindingsAreCompleteAndPathsUnique() {
        val definitions = allDefinitions()
        val paths = definitions.map(OperationTypings::jsPath)
        assertEquals("duplicate JS paths: $paths", paths.size, paths.toSet().size)
        // No path may be both a method and a namespace of other methods.
        paths.forEach { path ->
            assertTrue("$path collides with a namespace", paths.none { it.startsWith("$path.") })
        }
        definitions.forEach { d ->
            val binding = d.js
            val names = binding.positional + binding.optionsKeys
            val schemaNames = d.input.properties.keys
            names.forEach { assertTrue("${d.id}: binding names unknown property $it", it in schemaNames) }
            assertEquals("${d.id}: a property is bound twice", names.size, names.toSet().size)
            if (binding.spreadArg != null) {
                assertTrue("${d.id}: spreadArg cannot be combined with positional/options", names.isEmpty())
            } else {
                assertEquals("${d.id}: every property must be reachable from the JS binding", schemaNames, names.toSet())
            }
        }
    }
}
