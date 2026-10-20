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
    fun deletingLibraryAssetCannotBreakClosedOrDuplicatedProjects() {
        val files = Files.createTempDirectory("mise-shared-asset").toFile()
        try {
            val library = ManagedAssetLibrary(files, "library")
            val asset = library.install("Character.glb", "character", "glb", byteArrayOf(1, 2, 3))
            val actor = asset.actor(studio.artistscene.core.ActorKind.CHARACTER, "actor")
            val project = studio.artistscene.core.SceneProject(id = "original", name = "Original", actors = listOf(actor))
            val projects = File(files, "projects").apply { mkdirs() }
            val original = File(projects, "original.scene.json")
            val duplicate = File(projects, "duplicate.scene.json")
            original.writeText(studio.artistscene.core.SceneProjectCodec.encode(project))
            duplicate.writeText(studio.artistscene.core.SceneProjectCodec.encode(project.copy(id = "duplicate")))
            assertFalse(library.delete(asset.assetId))
            original.delete()
            assertFalse(library.delete(asset.assetId))
            assertTrue(File(files, asset.relativePath).isFile)
            assertEquals(1, library.list().size)
            duplicate.delete()
            assertTrue(library.delete(asset.assetId))
            assertFalse(File(files, asset.relativePath).exists())
        } finally { files.deleteRecursively() }
    }

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

            ManagedAssetLibrary(files, "library").updateRig(
                assetId = restored.assetId,
                compatibility = RigCompatibility.POSEABLE_CUSTOM_RIG,
                boneCount = 54,
                fingerJointCount = 30,
                morphTargetCount = 12,
            )
            val classified = ManagedAssetLibrary(files, "library").list().single()
            assertEquals(RigCompatibility.POSEABLE_CUSTOM_RIG, classified.rigCompatibility)
            assertEquals(54, classified.boneCount)
            assertEquals(30, classified.fingerJointCount)
            assertEquals(12, classified.morphTargetCount)
            assertTrue(ManagedAssetLibrary(files, "library").delete(restored.assetId))
            assertTrue(ManagedAssetLibrary(files, "library").list().isEmpty())
            assertFalse(File(files, installed.relativePath).exists())
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun fileInstallStreamsIntoContentAddressedLibrary() {
        val files = Files.createTempDirectory("mise-library-stream-test").toFile()
        try {
            val payload = ByteArray(512 * 1024) { (it % 251).toByte() }
            val source = File(files, "source.glb").apply { writeBytes(payload) }
            val asset = ManagedAssetLibrary(files, "library").installFile(
                name = "Streamed Model.glb",
                category = "character",
                format = "glb",
                file = source,
            )
            val saved = File(files, asset.relativePath)
            assertTrue(saved.isFile)
            assertEquals(payload.size.toLong(), saved.length())
            assertEquals(asset.checksumSha256, asset.assetId.removePrefix("local."))
            assertTrue(saved.readBytes().contentEquals(payload))
            assertTrue(ManagedAssetLibrary(files, "library").list().any { it.assetId == asset.assetId })
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun corruptedPrimaryIndexRecoversFromLastCommittedBackup() {
        val files = Files.createTempDirectory("mise-backup-test").toFile()
        try {
            val original = ManagedAssetLibrary(files, "library")
            val first = original.install("Oak.glb", "prop", "glb", byteArrayOf(4, 5, 6))
            val index = File(files, "library/library.json")
            val backup = File(files, "library/library.backup")
            assertTrue(index.isFile)
            assertTrue(backup.isFile)

            index.writeText("[invalid json")
            val reopened = ManagedAssetLibrary(files, "library")
            assertEquals(first.assetId, reopened.list().single().assetId)

            reopened.install("Birch.glb", "prop", "glb", byteArrayOf(7, 8, 9))
            assertEquals(2, ManagedAssetLibrary(files, "library").list().size)
            assertEquals(index.readText(), backup.readText())
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun damagedPrimaryAndBackupCannotBeSilentlyOverwritten() {
        val files = Files.createTempDirectory("mise-index-protect").toFile()
        try {
            val library = ManagedAssetLibrary(files, "library")
            val installed = library.install("Character.glb", "character", "glb", byteArrayOf(1, 2, 3))
            val main = File(files, "library/library.json")
            val backup = File(files, "library/library.backup")
            main.writeText("not json")
            backup.writeText("also not json")

            val error = runCatching {
                ManagedAssetLibrary(files, "library").install(
                    "New.glb", "prop", "glb", byteArrayOf(5, 6, 7),
                )
            }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals("not json", main.readText())
            assertEquals("also not json", backup.readText())
            assertTrue(File(files, installed.relativePath).isFile)
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun missingPayloadRecordIsPreservedWhenAnotherAssetIsInstalled() {
        val files = Files.createTempDirectory("mise-missing-asset").toFile()
        try {
            val library = ManagedAssetLibrary(files, "library")
            val missingBytes = byteArrayOf(1, 2, 3, 4)
            val missing = library.install("Missing.glb", "character", "glb", missingBytes)
            assertTrue(File(files, missing.relativePath).delete())
            library.install("Other.glb", "prop", "glb", byteArrayOf(8, 9, 10))

            // Missing records are hidden from the picker but must not be thrown out of the index.
            val index = File(files, "library/library.json").readText()
            assertTrue(index.contains(missing.assetId))
            val restored = library.install("Missing.glb", "character", "glb", missingBytes)
            assertEquals(missing.assetId, restored.assetId)
            assertTrue(File(files, missing.relativePath).isFile)
            assertEquals(2, ManagedAssetLibrary(files, "library").list().size)
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun duplicateImportRepairsCorruptedContentAddressedPayload() {
        val files = Files.createTempDirectory("mise-payload-repair").toFile()
        try {
            val library = ManagedAssetLibrary(files, "library")
            val cleanBytes = byteArrayOf(11, 12, 13, 14, 15, 16)
            val first = library.install(
                "Original.glb", "prop", "glb", cleanBytes,
                creator = "Original artist", license = "CC0-1.0",
            )
            val payload = File(files, first.relativePath)
            val bad = cleanBytes.copyOf().also { it[2] = 0 }
            payload.writeBytes(bad)
            val repaired = library.install("Same model.glb", "prop", "glb", cleanBytes)
            assertEquals(first.assetId, repaired.assetId)
            assertTrue(payload.readBytes().contentEquals(cleanBytes))
            assertEquals("Original artist", repaired.creator)
            assertEquals("CC0-1.0", repaired.license)
            assertEquals(1, library.list().size)
        } finally {
            files.deleteRecursively()
        }
    }
}
