package studio.artistscene.core

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real Android filesystem tests: the JVM suite cannot exercise android.system.Os.rename.
 * Every test uses unique scene IDs so it never mutates another artist's saved project.
 */
class SceneProjectStoreIntegrityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun id(label: String) = "mise8-$label-${UUID.randomUUID().toString().take(8)}"

    @Test
    fun duplicateNeverOverwritesExistingSceneOrItsSource() {
        val store = SceneProjectStore(context)
        val sourceId = id("source")
        val destinationId = id("occupied")
        val source = SceneProject(
            id = sourceId,
            name = "Original",
            actors = listOf(
                Actor("character", "Character", ActorKind.CHARACTER),
                Actor("prop", "Held prop", ActorKind.PROP, parentId = "character", parentBoneId = "right-hand"),
            ),
            tracks = listOf(AnimationTrack(
                id = "prop-position",
                targetActorId = "prop",
                propertyPath = SceneTimelinePaths.POSITION,
                keyframes = listOf(Keyframe(0f, AnimatedValue(vector = Vec3(1f, 2f, 3f)))),
            )),
        )
        val occupied = SceneProject(id = destinationId, name = "Keep this artist scene")
        try {
            store.save(source)
            store.save(occupied)
            assertTrue(
                "Duplicate must reject an occupied ID, not replace the existing scene",
                runCatching { store.duplicate(sourceId, destinationId, "Copy") }.exceptionOrNull()
                    is IllegalArgumentException,
            )
            assertTrue(
                "Duplicating a scene over its own ID must be rejected",
                runCatching { store.duplicate(sourceId, sourceId, "Copy") }.exceptionOrNull()
                    is IllegalArgumentException,
            )
            assertEquals(source, store.load(sourceId))
            assertEquals(occupied, store.load(destinationId))
        } finally {
            store.delete(sourceId)
            store.delete(destinationId)
        }
    }

    @Test
    fun duplicatePreservesRigParentTracksAndCreatesIndependentSceneIdentity() {
        val store = SceneProjectStore(context)
        val originalId = id("original")
        val copyId = id("copy")
        val original = SceneProject(
            id = originalId,
            name = "Artist-created multi-actor scene",
            actors = listOf(
                Actor("actor-one", "First", ActorKind.CHARACTER,
                    rig = RigPose(joints = mapOf("hand" to Vec3(z = 42f)))),
                Actor("actor-two", "Second", ActorKind.CHARACTER),
                Actor("tree", "Oak", ActorKind.ENVIRONMENT),
                Actor("car", "Vehicle", ActorKind.VEHICLE),
                Actor("phone", "Attached", ActorKind.PROP, parentId = "actor-one", parentBoneId = "hand"),
            ),
            tracks = listOf(AnimationTrack(
                id = "hand-animation",
                targetActorId = "actor-one",
                propertyPath = SceneTimelinePaths.rigJoint("hand"),
                keyframes = listOf(Keyframe(0.5f,
                    AnimatedValue(rotationEulerDegrees = Vec3(z = 25f)))),
            )),
            savedPoses = listOf(SavedPosePreset("custom-1", "My scene pose")),
        )
        try {
            store.save(original)
            val cloned = store.duplicate(originalId, copyId, "My duplicate")
            assertEquals(copyId, cloned.id)
            assertEquals("My duplicate", cloned.name)
            assertEquals(original.actors, cloned.actors)
            assertEquals(original.tracks, cloned.tracks)
            assertEquals(original.savedPoses, cloned.savedPoses)
            assertTrue(cloned.metadata.createdAtEpochMs > 0L)
            assertEquals(cloned, store.load(copyId))
            assertEquals(original, store.load(originalId))
            store.rename(copyId, "Changed only in the copy")
            assertEquals("Artist-created multi-actor scene", store.load(originalId).name)
        } finally {
            store.delete(originalId)
            store.delete(copyId)
        }
    }

    @Test
    fun mismatchedFileIdentityCannotOpenAsAnotherProject() {
        val store = SceneProjectStore(context)
        val actualId = id("actual")
        val wrongFileId = id("wrong-path")
        val directory = File(context.filesDir, "projects").apply { mkdirs() }
        val wrongFile = File(directory, "$wrongFileId.scene.json")
        try {
            wrongFile.writeText(SceneProjectCodec.encode(SceneProject(id = actualId, name = "Not my ID")))
            assertTrue(
                "Mismatched payload must not be loaded or silently saved under the wrong path",
                runCatching { store.load(wrongFileId) }.exceptionOrNull() is IllegalArgumentException,
            )
            assertFalse(store.list().any { it.id == actualId || it.id == wrongFileId })
            assertTrue("Corrupt/misnamed source must remain on disk for recovery", wrongFile.isFile)
        } finally {
            store.delete(wrongFileId)
        }
    }

    @Test
    fun separateStoreInstancesCanCommitWithoutDamagingJsonOrClobberingPendingFiles() {
        val projectId = id("concurrent")
        val pool = Executors.newFixedThreadPool(4)
        val store = SceneProjectStore(context)
        try {
            val tasks = (1..24).map { version ->
                pool.submit {
                    SceneProjectStore(context).save(SceneProject(
                        id = projectId,
                        name = "Version $version",
                        actors = listOf(Actor("subject", "Actor $version", ActorKind.PROP)),
                    ))
                }
            }
            tasks.forEach { it.get(20, TimeUnit.SECONDS) }
            val saved = store.load(projectId)
            assertTrue(saved.name.matches(Regex("Version [0-9]+")))
            assertEquals(saved.name.removePrefix("Version "),
                saved.actors.single().name.removePrefix("Actor "))
            assertFalse(
                "Temporary writes must be removed after committed saves",
                File(context.filesDir, "projects").listFiles().orEmpty()
                    .any { it.name.startsWith("$projectId.") && it.name.endsWith(".pending") },
            )
        } finally {
            pool.shutdownNow()
            store.delete(projectId)
        }
    }
}
