package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneEditorTest {
    private fun scene() = SceneProject(
        id = "editor-test",
        name = "Editor Test",
        actors = listOf(
            Actor("prop-a", "Prop A", ActorKind.PROP),
            Actor("prop-b", "Prop B", ActorKind.PROP, transform = Transform(position = Vec3(1f, 0f, 0f))),
        ),
    )

    @Test
    fun fullTransformEditingIsUndoableAndRedoable() {
        var state = SceneEditorState(scene()).selectActor("prop-a")
        state = state
            .translate(TransformAxis.X, 1f)
            .translate(TransformAxis.Y, 2f)
            .translate(TransformAxis.Z, -3f)
            .rotate(TransformAxis.Y, 45f)
            .setScale(TransformAxis.X, 1.5f)
            .setScale(TransformAxis.Y, 2f)
            .setScale(TransformAxis.Z, 0.5f)

        val actor = requireNotNull(state.selectedActor)
        assertEquals(Vec3(1f, 2f, -3f), actor.transform.position)
        assertEquals(45f, actor.transform.rotationEulerDegrees.y)
        assertEquals(Vec3(1.5f, 2f, 0.5f), actor.transform.scale)
        assertTrue(state.canUndo)

        val undone = state.undo()
        assertEquals(1f, requireNotNull(undone.selectedActor).transform.scale.z)
        assertTrue(undone.canRedo)

        val redone = undone.redo()
        assertEquals(0.5f, requireNotNull(redone.selectedActor).transform.scale.z)
    }

    @Test
    fun viewportDragPreviewsLiveAndCreatesOneUndoStep() {
        val initial = scene()
        val start = SceneEditorState(initial).selectActor("prop-a")
        val preview1 = start.previewSelectedTransform(
            requireNotNull(start.selectedActor).transform.copy(position = Vec3(0.5f, 0f, 0f)),
        )
        val preview2 = preview1.previewSelectedTransform(
            requireNotNull(preview1.selectedActor).transform.copy(position = Vec3(0.75f, 0f, 0f)),
        )

        assertEquals(0f, requireNotNull(start.selectedActor).transform.position.x)
        assertEquals(0.75f, requireNotNull(preview2.selectedActor).transform.position.x)
        assertFalse(preview2.canUndo)

        val committed = preview2.commitTransformGesture(initial)
        assertTrue(committed.canUndo)
        assertEquals(0.75f, requireNotNull(committed.selectedActor).transform.position.x)
        assertEquals(initial, committed.undo().project)
        assertEquals(0f, requireNotNull(committed.undo().selectedActor).transform.position.x)
    }

    @Test
    fun referenceImagesAreUndoableSceneObjects() {
        val reference = ReferenceImage(
            id = "reference-front",
            name = "Front pose",
            persistedUri = "content://mise/front",
            opacity = 0.7f,
            transform = Transform(position = Vec3(0f, 1f, -1f), scale = Vec3(2f, 2f, 2f)),
        )
        val added = SceneEditorState(scene()).addReferenceImage(reference)
        assertEquals(reference, added.project.referenceImages.single())
        assertTrue(added.canUndo)

        val edited = added
            .setReferenceOpacity(reference.id, 0.35f)
            .translateReference(reference.id, TransformAxis.X, 0.5f)
            .scaleReference(reference.id, 0.5f)
            .toggleReferenceVisibility(reference.id)

        val saved = edited.project.referenceImages.single()
        assertEquals(0.35f, saved.opacity)
        assertEquals(0.5f, saved.transform.position.x)
        assertEquals(Vec3(2.5f, 2.5f, 2.5f), saved.transform.scale)
        assertFalse(saved.visible)

        val deleted = edited.deleteReferenceImage(reference.id)
        assertTrue(deleted.project.referenceImages.isEmpty())
        assertEquals(1, deleted.undo().project.referenceImages.size)
    }

    @Test
    fun cameraProjectionLensAndActivationAreSceneOwned() {
        val start = SceneEditorState(scene())
        val second = SceneCamera("camera-ortho", "Ortho", projection = CameraProjection.ORTHOGRAPHIC)
        val added = start.addCamera(second, activate = false)

        val activated = added.activateCamera(second.id)
            .setActiveCameraOrthographicHeight(7.5f)
        assertEquals(second.id, activated.project.activeCameraId)
        assertEquals(7.5f, activated.project.cameras.first { it.id == second.id }.orthographicHeightMeters)

        val perspective = activated
            .setActiveCameraProjection(CameraProjection.PERSPECTIVE)
            .setActiveCameraVerticalFov(200f)
        val active = perspective.project.cameras.first { it.id == second.id }
        assertEquals(CameraProjection.PERSPECTIVE, active.projection)
        assertEquals(120f, active.verticalFovDegrees)
        assertTrue(perspective.canUndo)
    }

    @Test
    fun cameraFramingAndAddedCameraAreProjectOwnedAndUndoable() {
        val start = SceneEditorState(scene())
        val camera = SceneCamera("camera-close", "Close-up")
        val added = start.addCamera(camera)
        assertEquals("camera-close", added.project.activeCameraId)
        assertEquals(2, added.project.cameras.size)

        val framed = added.updateActiveCamera(camera.copy(target = Vec3(1f, 2f, 3f)))
        assertEquals(Vec3(1f, 2f, 3f), framed.project.cameras.last().target)
        assertEquals(camera.target, added.project.cameras.last().target)
        assertEquals(added.project, framed.undo().project)
        assertEquals(start.project, added.undo().project)
    }

    @Test
    fun duplicateDeleteVisibilityRenameAndResetAreSceneOwned() {
        var state = SceneEditorState(scene()).selectActor("prop-a")
        state = state.translate(TransformAxis.X, 3f).duplicateSelected()

        val duplicate = requireNotNull(state.selectedActor)
        assertNotEquals("prop-a", duplicate.id)
        assertEquals(3, state.project.actors.size)
        assertEquals(3.25f, duplicate.transform.position.x)

        state = state.toggleSelectedVisibility()
        assertFalse(requireNotNull(state.selectedActor).visible)

        state = state.renameSelected("Reference Box")
        assertEquals("Reference Box", requireNotNull(state.selectedActor).name)

        state = state.resetTransform()
        assertEquals(Transform(), requireNotNull(state.selectedActor).transform)

        state = state.deleteSelected()
        assertEquals(2, state.project.actors.size)
        assertTrue(state.project.actors.none { it.name == "Reference Box" })
    }

    @Test
    fun lockedActorsRejectDestructiveTransformAndDelete() {
        val locked = scene().copy(
            actors = scene().actors.map { if (it.id == "prop-a") it.copy(locked = true) else it },
        )
        val state = SceneEditorState(locked).selectActor("prop-a")
        assertEquals(state, state.translate(TransformAxis.X, 2f))
        assertEquals(state, state.deleteSelected())
    }

    @Test
    fun reparentRejectsCyclesAndDeleteDetachesChildren() {
        val nested = scene().copy(
            actors = listOf(
                Actor("parent", "Parent", ActorKind.PROP),
                Actor("child", "Child", ActorKind.PROP, parentId = "parent"),
            ),
        )
        var state = SceneEditorState(nested).selectActor("parent")
        assertEquals(state, state.reparentSelected("child"))

        state = state.deleteSelected()
        assertNull(state.project.actors.single().parentId)
    }

    @Test
    fun jointEditsAreUndoableAndResetToImportedRestPose() {
        val bone = RigBone(id = "root/right-arm/elbow", name = "Right Elbow", parentId = "root/right-arm")
        val character = Actor(
            id = "character-a",
            name = "Character A",
            kind = ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(bone)),
        )
        val neutral = SceneEditorState(SceneProject(id = "rig", name = "Rig", actors = listOf(character)))
        val posed = neutral.setRigJointRotation(bone.id, Vec3(z = 25f))

        assertEquals(Vec3(z = 25f), posed.selectedActor?.rig?.joints?.get(bone.id))
        assertTrue(posed.canUndo)
        assertNull(posed.undo().selectedActor?.rig)
        assertEquals(Vec3(z = 25f), posed.undo().redo().selectedActor?.rig?.joints?.get(bone.id))
        assertNull(posed.resetRigJoint(bone.id).selectedActor?.rig)
    }

    @Test
    fun jointDragPreviewsWithoutHistoryAndCommitsOnce() {
        val bone = RigBone("root/arm/elbow", "Elbow")
        val character = Actor("pose-character", "Pose Character", ActorKind.CHARACTER, rigDefinition = RigDefinition(bones = listOf(bone)))
        val start = SceneEditorState(SceneProject(id = "drag", name = "Drag", actors = listOf(character)))
        val preview = start
            .previewRigJointRotation(bone.id, Vec3(z = 5f))
            .previewRigJointRotation(bone.id, Vec3(z = 12f))
            .previewRigJointRotation(bone.id, Vec3(z = 24f))

        assertFalse(preview.canUndo)
        val committed = preview.commitRigGesture(start.project)
        assertTrue(committed.canUndo)
        assertEquals(start.project, committed.undo().project)
        assertEquals(Vec3(z = 24f), committed.undo().redo().selectedActor?.rig?.joints?.get(bone.id))
    }

    @Test
    fun discoveredAnimationsAreDurableAndDirectPoseStopsPlayback() {
        val bone = RigBone("root/arm", "Arm")
        val character = Actor(
            id = "animated-character",
            name = "Animated Character",
            kind = ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(bone)),
        )
        val start = SceneEditorState(
            SceneProject(id = "animation", name = "Animation", actors = listOf(character)),
        )
        val discovered = start.withDiscoveredAnimations(
            "animated-character",
            listOf(
                AnimationClipDefinition("Idle", 2f),
                AnimationClipDefinition("Walk", 1.25f),
            ),
        )

        assertEquals(listOf("Idle", "Walk"), discovered.selectedActor?.animation?.clips?.map { it.name })
        assertEquals("Idle", discovered.selectedActor?.animation?.selectedClip)
        assertFalse(discovered.canUndo)

        val playing = discovered
            .selectAnimationClip("Walk")
            .setSelectedAnimationSpeed(1.5f)
            .setSelectedAnimationPlaying(true)
        assertTrue(requireNotNull(playing.selectedActor).animation.playing)
        assertEquals(1.5f, playing.selectedActor?.animation?.speed)

        val posed = playing.setRigJointRotation(bone.id, Vec3(z = 20f))
        assertFalse(requireNotNull(posed.selectedActor).animation.playing)
        assertEquals(Vec3(z = 20f), posed.selectedActor?.rig?.joints?.get(bone.id))
        assertTrue(posed.undo().selectedActor?.animation?.playing == true)
    }

    @Test
    fun lightEditsAreSceneOwnedUndoableAndClamped() {
        val light = Actor(
            id = "key-light",
            name = "Key Light",
            kind = ActorKind.LIGHT,
            light = LightSettings(
                type = LightType.POINT,
                intensity = 2_000f,
                rangeMeters = 5f,
            ),
        )
        var state = SceneEditorState(
            SceneProject(id = "lights", name = "Lights", actors = listOf(light)),
        ).selectActor("key-light")

        state = state.setSelectedLightIntensity(3_500f)
        state = state.setSelectedLightColorHex("#FFD8B0")
        state = state.setSelectedLightRangeMeters(12f)
        state = state.toggleSelectedLightShadows()

        val edited = requireNotNull(state.selectedActor?.light)
        assertEquals(3_500f, edited.intensity)
        assertEquals("#FFD8B0", edited.colorHex)
        assertEquals(12f, edited.rangeMeters)
        assertFalse(edited.castsShadow)
        assertTrue(state.canUndo)

        val shadowsUndone = state.undo()
        assertTrue(requireNotNull(shadowsUndone.selectedActor?.light).castsShadow)

        val clamped = state
            .setSelectedLightIntensity(Float.MAX_VALUE)
            .setSelectedLightRangeMeters(0f)
        assertEquals(500_000f, requireNotNull(clamped.selectedActor?.light).intensity)
        assertEquals(0.1f, requireNotNull(clamped.selectedActor?.light).rangeMeters)
        assertEquals(clamped, clamped.setSelectedLightColorHex("not-a-color"))
    }

    @Test
    fun duplicateCharactersKeepIndependentBonePoses() {
        val bone = RigBone(id = "skeleton/arm", name = "Arm")
        val original = Actor(
            id = "character-a",
            name = "Character A",
            kind = ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(bone)),
        )
        val state = SceneEditorState(SceneProject(id = "multi-rig", name = "Multi rig", actors = listOf(original)))
            .setRigJointRotation(bone.id, Vec3(y = 30f))
        val duplicated = state.duplicateSelected()
        val duplicateId = duplicated.selectedActorId
        val independentlyPosed = duplicated.setRigJointRotation(bone.id, Vec3(y = -40f))

        assertEquals(Vec3(y = 30f), independentlyPosed.project.actors.first { it.id == original.id }.rig?.joints?.get(bone.id))
        assertEquals(Vec3(y = -40f), independentlyPosed.project.actors.first { it.id == duplicateId }.rig?.joints?.get(bone.id))
    }    @Test
    fun spotLightDirectionAndConeAreArtistEditableAndClamped() {
        val light = Actor(
            id = "spot",
            name = "Spot",
            kind = ActorKind.LIGHT,
            light = LightSettings(type = LightType.SPOT),
        )
        var state = SceneEditorState(scene().copy(actors = scene().actors + light)).selectActor("spot")
        state = state
            .setSelectedLightDirection(Vec3(1f, -1f, 0f))
            .setSelectedSpotConeDegrees(88f, 30f)

        val settings = requireNotNull(state.selectedActor?.light)
        assertTrue(settings.direction.x > 0.7f)
        assertTrue(settings.direction.y < -0.7f)
        assertEquals(29f, settings.spotInnerConeDegrees, 0.0001f)
        assertEquals(30f, settings.spotOuterConeDegrees, 0.0001f)

        val unchanged = state.setSelectedLightDirection(Vec3())
        assertEquals(state.project, unchanged.project)
    }


}
