package studio.artistscene.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneProjectStore

/** Proof of real 3D rig deformation, not a static anime image or posed illustration. */
class AnimePoseStudyInstrumentedTest {
    @Test fun dynamicAnimePoseRendersDifferentlyFromTheSameNeutralSkinnedMesh() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SceneProjectStore(context)
        val project = PrototypeScene.createAnimePoseStudy()
        val actorId = "anime-hero"
        val expected = project.actors.first { it.id == actorId }.rig!!.joints
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val proof = requireNotNull(context.getExternalFilesDir(null))

        fun openScene() {
            val row = requireNotNull(
                device.wait(Until.findObject(By.res("project-open-${project.id}")), 25_000),
            ) { "Anime Pose Study was not available to open" }
            row.click()
            requireNotNull(device.wait(Until.findObject(By.res("scene-viewport")), 45_000)) {
                "Real Filament viewport did not open"
            }
        }

        // ABR: compare actual on-device pixels with the SAME camera, floor and
        // lighting but no character. Skin, hair, clothing and eye colors vary,
        // so a green-pixel test is invalid for an actual textured anime avatar.
        fun screenshot(stage: String, background: File? = null): File {
            val image = File(proof, "anime-$stage.png")
            // Expose only the FINAL accepted PNG to ADB's screenshot watcher.
            // Previously, repeatedly writing anime-posed.png let the watcher
            // capture the first EMPTY pre-render frame and keep it forever.
            val candidate = File(proof, "anime-$stage-candidate.png")
            fun publishAcceptedCapture(): File {
                image.delete()
                check(candidate.renameTo(image)) { "Could not publish accepted $stage screenshot" }
                return image
            }
            val reference = background?.let { requireNotNull(BitmapFactory.decodeFile(it.path)) }
            try {
                val deadline = SystemClock.uptimeMillis() + if (background != null) 40_000 else 4_000
                do {
                    SystemClock.sleep(if (background != null) 1_600 else 3_500)
                    assertTrue("Could not capture the $stage screenshot", device.takeScreenshot(candidate))
                    if (reference == null) return publishAcceptedCapture()
                    val current = requireNotNull(BitmapFactory.decodeFile(candidate.path))
                    var changed = 0
                    for (y in current.height * 24 / 100 until current.height * 58 / 100 step 3) {
                        for (x in current.width * 16 / 100 until current.width * 84 / 100 step 3) {
                            val a = current.getPixel(x, y)
                            val b = reference.getPixel(x, y)
                            if (kotlin.math.abs(Color.red(a) - Color.red(b)) +
                                kotlin.math.abs(Color.green(a) - Color.green(b)) +
                                kotlin.math.abs(Color.blue(a) - Color.blue(b)) >= 55) changed++
                        }
                    }
                    current.recycle()
                    if (changed >= 150) return publishAcceptedCapture()
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError(
                    "Actual textured anime $stage mesh is invisible on Android " +
                        android.os.Build.VERSION.SDK_INT + "; see ${image.name}",
                )
            } finally {
                candidate.delete()
                reference?.recycle()
            }
        }

        fun waitForRig() {
            val deadline = SystemClock.uptimeMillis() + 90_000
            while (SystemClock.uptimeMillis() < deadline) {
                val actor = store.load(project.id).actors.first { it.id == actorId }
                if ((actor.rigDefinition?.bones?.size ?: 0) >= 60) {
                    val actual = actor.rig?.joints.orEmpty()
                    val discovered = actor.rigDefinition!!.bones.map { it.id }.toSet()
                    assertTrue(
                        "Anime pose must be bound to real Filament bones. Unresolved: " +
                            actual.keys.filterNot { it in discovered }.joinToString(),
                        actual.size == expected.size && actual.keys.all { it in discovered },
                    )
                    return
                }
                SystemClock.sleep(350)
            }
            throw AssertionError("The real VRoid anime skeleton never completed discovery/persistence")
        }

        fun distinctPixelDifference(first: File, second: File): Int {
            val a = requireNotNull(BitmapFactory.decodeFile(first.path))
            val b = requireNotNull(BitmapFactory.decodeFile(second.path))
            assertEquals(a.width, b.width)
            assertEquals(a.height, b.height)
            var changed = 0
            // Ignore tool rail and top/bottom chrome. This area contains the figure.
            for (y in a.height * 24 / 100 until a.height * 58 / 100 step 3) {
                for (x in a.width * 16 / 100 until a.width * 84 / 100 step 3) {
                    val before = a.getPixel(x, y)
                    val after = b.getPixel(x, y)
                    val diff = kotlin.math.abs(Color.red(before) - Color.red(after)) +
                        kotlin.math.abs(Color.green(before) - Color.green(after)) +
                        kotlin.math.abs(Color.blue(before) - Color.blue(after))
                    if (diff >= 55) changed++
                }
            }
            a.recycle()
            b.recycle()
            return changed
        }

        store.save(project)
        try {
            // Empty-scene reference must be captured BEFORE the skinned GLB loads:
            // this removes color assumptions and prevents UI-only passes.
            store.save(project.copy(actors = project.actors.filter { it.kind == ActorKind.LIGHT }))
            val empty: File
            ActivityScenario.launch(MainActivity::class.java).use {
                openScene()
                empty = screenshot("empty-background")
            }
            store.save(project)
            val posed: File
            ActivityScenario.launch(MainActivity::class.java).use {
                openScene()
                waitForRig()
                assertEquals(expected.values.toSet(), store.load(project.id)
                    .actors.first { it.id == actorId }.rig!!.joints.values.toSet())
                posed = screenshot("posed", empty)
            }

            // Same GLB, lights, sky, camera and material; only joint rotations change.
            store.save(
                store.load(project.id).copy(actors = store.load(project.id).actors.map { actor ->
                    if (actor.id == actorId) actor.copy(rig = null) else actor
                }),
            )
            val neutral: File
            ActivityScenario.launch(MainActivity::class.java).use {
                openScene()
                neutral = screenshot("neutral", empty)
            }
            // ABR: an invisible posed mesh must NOT pass just because neutral is visible.
            // Compare both shots to a true empty-model screenshot under identical
            // camera, lights and environment; do not accept a screenshot of the UI.
            val posedForeground = distinctPixelDifference(posed, empty)
            val neutralForeground = distinctPixelDifference(neutral, empty)
            assertTrue(
                "The posed anime model is NOT visible ($posedForeground foreground pixels)",
                posedForeground >= 150,
            )
            assertTrue(
                "The neutral anime model is NOT visible ($neutralForeground foreground pixels)",
                neutralForeground >= 150,
            )
            val changed = distinctPixelDifference(posed, neutral)
            var knownVisibleHumanContrast = -1
            if (changed < 65) {
                // One-shot diagnostic: same project/camera/lights, proven MakeHuman GLB.
                // If this renders and our anime does not, the generated GLB or its
                // Filament visibility path is defective; never silently ship it.
                val human = PrototypeScene.starterAssets().single {
                    it.asset?.assetId == PrototypeScene.HUMANOID_ASSET_ID
                }
                store.save(store.load(project.id).copy(actors = store.load(project.id).actors.map { actor ->
                    if (actor.id == actorId) actor.copy(asset = human.asset, rig = null) else actor
                }))
                val control: File
                ActivityScenario.launch(MainActivity::class.java).use {
                    openScene()
                    SystemClock.sleep(8_000)
                    control = screenshot("control-original-human")
                }
                knownVisibleHumanContrast = distinctPixelDifference(posed, control)
            }
            assertTrue(
                "Anime pose invisible or static ($changed changed pixels); " +
                    "same-camera normal humanoid contrast=$knownVisibleHumanContrast",
                changed >= 65,
            )
            // Real female VRoid base: independently load the OTHER textured skinned mesh.
            // This proves the new character catalog is not a screenshot or a duplicate
            // reference to the original male asset.
            val female = PrototypeScene.starterAssets().single {
                it.asset?.assetId == PrototypeScene.ANIME_FEMALE_ASSET_ID
            }.copy(id = actorId, name = "Female anime proof")
            store.save(project.copy(actors = project.actors.map { actor ->
                if (actor.id == actorId) female else actor
            }))
            val femaleNeutral: File
            ActivityScenario.launch(MainActivity::class.java).use {
                openScene()
                val deadline = SystemClock.uptimeMillis() + 70_000
                while (SystemClock.uptimeMillis() < deadline &&
                    (store.load(project.id).actors.first { it.id == actorId }
                        .rigDefinition?.bones?.size ?: 0) < 60) {
                    SystemClock.sleep(400)
                }
                val femaleActor = store.load(project.id).actors.first { it.id == actorId }
                assertTrue("Real female anime rig never loaded",
                    (femaleActor.rigDefinition?.bones?.size ?: 0) >= 60)
                val faceNames = femaleActor.rigDefinition?.morphTargets.orEmpty().map { it.name }.toSet()
                assertTrue("VRM authentic Joy/Blink/Angry/Sorrow expressions unavailable: $faceNames",
                    setOf("Joy", "Blink", "Angry", "Sorrow").all { it in faceNames })

                femaleNeutral = screenshot("female-neutral", empty)
            }
            assertTrue("Female anime model invisible on emulator",
                distinctPixelDifference(femaleNeutral, empty) >= 150)
            // Hair-specific acceptance: actual VRM HairJoint strands MUST deform
            // under gravity/wind. A static textured female model is not enough.
            fun withHair(gravity: Float, wind: Float) {
                val current = store.load(project.id)
                store.save(current.copy(actors = current.actors.map { actor ->
                    if (actor.id == actorId) actor.copy(
                        appearance = actor.appearance.copy(
                            hairPhysicsEnabled = true,
                            // Hair-only acceptance must not compare against a moving
                            // fabric/skirt spring; preserve the strict identical-body
                            // camera assertion instead of accepting body motion.
                            fabricPhysicsEnabled = false,
                            hairGravityStrength = gravity,
                            hairWindStrength = wind,
                        ),
                    ) else actor
                }))
            }
            withHair(gravity = 0f, wind = 0f)
            // Critically, capture both frames WITHIN ONE activity, camera, model
            // instance, transform and daylight studio. Reopening the scene subtly
            // reframes it, producing a fake hair pass from a shifted whole body.
            val stillHair: File
            val movingHair: File
            ActivityScenario.launch(MainActivity::class.java).use {
                openScene()
                stillHair = screenshot("female-hair-still", empty)
                requireNotNull(device.wait(Until.findObject(By.res("inspector")), 10_000)) {
                    "Inspector inaccessible for VRoid gravity controls"
                }.click()
                val scroll = requireNotNull(
                    device.wait(Until.findObject(By.res("context-sheet-content")), 10_000),
                ) { "Inspector scroll panel not accessible" }
                val diagnosticScreenshot = File(proof, "anime-inspector-hair-controls.png")
                assertTrue("Inspector could not be captured", device.takeScreenshot(diagnosticScreenshot))
                val diagnosticHierarchy = File(proof, "anime-inspector-hair-controls.xml")
                device.dumpWindowHierarchy(diagnosticHierarchy)
                println("MISE_HAIR_INSPECTOR_DIAG " +
                    diagnosticHierarchy.readText().split(">").filter {
                        it.contains("anime-gravity") || it.contains("Gravity") ||
                            it.contains("Hair") || it.contains("Appearance") ||
                            it.contains("actor-")
                    }.take(15).joinToString(" ") { it.takeLast(180) })

                fun pressRepeated(tag: String, count: Int) {
                    repeat(count) {
                        var button = device.findObject(By.res(tag))
                        var attempts = 0
                        while (button == null && attempts++ < 8) {
                            scroll.swipe(Direction.UP, .6f)
                            SystemClock.sleep(180)
                            button = device.findObject(By.res(tag))
                        }
                        requireNotNull(button) { "Hair physics control $tag cannot be reached" }.click()
                    }
                }
                pressRepeated("anime-gravity-more", 4)
                pressRepeated("anime-breeze-more", 8)
                // Dismiss the modal without changing orbit position or actor scale.
                device.pressBack()
                assertTrue("Inspector modal still obscures hair after back",
                    device.wait(Until.gone(By.res("context-sheet-content")), 10_000))
                SystemClock.sleep(3_500)
                // A SceneView Node can outlive the asynchronous ModelInstance
                // load. First prove that its native onFrame closure consumes the
                // NOW-loaded spring runtime and live Inspector values. This is a
                // diagnostic guard, never a substitute for the hair pixel proof.
                val physicsLog = device.executeShellCommand(
                    "logcat -d -s MiseHairPhysics:I '*:S'",
                )
                File(proof, "anime-hair-live-physics.log").writeText(physicsLog)
                println("MISE_HAIR_LIVE_RENDERER_DIAG " +
                    physicsLog.lineSequence().filter { it.contains("Live renderer controls") }
                        .toList().takeLast(8).joinToString(" | ").take(2_500))
                assertTrue(
                    "Native renderer callback did not consume the LIVE 2.0 gravity/wind " +
                        "for $actorId; async springRuntime may be stale/null",
                    physicsLog.lineSequence().any {
                        it.contains("Live renderer controls actor=$actorId") &&
                            it.contains("gravity=2.0") && it.contains("wind=2.0")
                    },
                )
                movingHair = screenshot("female-hair-gravity", empty)
            }
            fun hairPixelsChanged(a: File, b: File): Int {
                val before = requireNotNull(BitmapFactory.decodeFile(a.path))
                val after = requireNotNull(BitmapFactory.decodeFile(b.path))
                assertEquals(before.width, after.width)
                assertEquals(before.height, after.height)
                var delta = 0
                for (y in before.height * 20 / 100 until before.height * 35 / 100) {
                    for (x in before.width * 36 / 100 until before.width * 66 / 100) {
                        val p = before.getPixel(x, y)
                        val q = after.getPixel(x, y)
                        if (kotlin.math.abs(Color.red(p) - Color.red(q)) +
                            kotlin.math.abs(Color.green(p) - Color.green(q)) +
                            kotlin.math.abs(Color.blue(p) - Color.blue(q)) >= 60) delta++
                    }
                }
                before.recycle()
                after.recycle()
                return delta
            }
            assertTrue(
                "VRoid hair remained static under 2× gravity and breeze; not acceptable",
                hairPixelsChanged(stillHair, movingHair) >= 30,
            )
            // Verify there was no whole-avatar reframing/scaling or camera
            // movement: lower skirt must be stationary between hair screenshots.
            fun unchangedSkirt(a: File, b: File): Int {
                val before = requireNotNull(BitmapFactory.decodeFile(a.path))
                val after = requireNotNull(BitmapFactory.decodeFile(b.path))
                var changed = 0
                for (y in before.height * 68 / 100 until before.height * 75 / 100) {
                    for (x in before.width * 32 / 100 until before.width * 68 / 100) {
                        val p = before.getPixel(x, y)
                        val q = after.getPixel(x, y)
                        if (kotlin.math.abs(Color.red(p) - Color.red(q)) +
                            kotlin.math.abs(Color.green(p) - Color.green(q)) +
                            kotlin.math.abs(Color.blue(p) - Color.blue(q)) > 55) changed++
                    }
                }
                before.recycle()
                after.recycle()
                return changed
            }
            assertTrue(
                "Hair comparison shifted the rigid lower body/camera; invalid proof",
                unchangedSkirt(stillHair, movingHair) < 70,
            )

        } finally {
            // Restore the user's sample project to the authored hero pose.
            store.save(project)
        }
    }
}
