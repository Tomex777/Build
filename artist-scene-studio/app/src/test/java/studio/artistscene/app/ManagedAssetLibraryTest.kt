package studio.artistscene.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedAssetLibraryTest {
    @Test
    fun installedAssetAndAttributionSurviveLibraryRecreationAndDeleteCleanly() {
        val files = Files.createTempDirectory("mise-library-test").toFile()
        try {
            val bytes = byteArrayOf(0x67, 0x6c, 0x54, 0x46, 2, 0, 0, 0, 8, 0, 0, 0)
            val first = ManagedAssetLibrary(files, "library")
            val installed = first.install(
                name = "Artist Character.glb",
                category = "character",
                format = "glb",
                bytes = bytes,
                source = "https://example.test/model/123",
                creator = "artist-example",
                license = "CC-BY-4.0",
                licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
                attribution = "Credit artist-example and the model source.",
            )
            assertTrue(File(files, installed.relativePath).isFile)
            assertTrue(File(files, "library/library.json").isFile)
            assertFalse(File(files, "library/payloads/${installed.checksumSha256}.glb.pending").exists())

            val restored = ManagedAssetLibrary(files, "library").list().single()
            assertEquals(installed.assetId, restored.assetId)
            assertEquals("artist-example", restored.creator)
            assertEquals("CC-BY-4.0", restored.license)
            assertEquals("https://example.test/model/123", restored.source)
            assertEquals("Credit artist-example and the model source.", restored.attribution)
            assertEquals("https://creativecommons.org/licenses/by/4.0/", restored.licenseUrl)
            assertEquals(bytes.size.toLong(), restored.byteSize)
            assertNotNull(restored.actor(
                studio.artistscene.core.ActorKind.CHARACTER,
                "actor-instance-a",
            ).asset)

            ManagedAssetLibrary(files, "library").updateRig(restored.assetId, RigCompatibility.POSEABLE_CUSTOM_RIG, 19)
            val classified = ManagedAssetLibrary(files, "library").list().single()
            assertEquals(RigCompatibility.POSEABLE_CUSTOM_RIG, classified.rigCompatibility)
            assertEquals(19, classified.boneCount)
            assertTrue(ManagedAssetLibrary(files, "library").delete(restored.assetId))
            assertTrue(ManagedAssetLibrary(files, "library").list().isEmpty())
            assertFalse(File(files, installed.relativePath).exists())
        } finally {
            files.deleteRecursively()
        }
    }
}
