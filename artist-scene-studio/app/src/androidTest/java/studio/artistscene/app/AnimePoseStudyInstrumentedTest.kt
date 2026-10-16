package studio.artistscene.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
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
            val reference = background?.let { requireNotNull(BitmapFactory.decodeFile(it.path)) }
            try {
                val deadline = SystemClock.uptimeMillis() + if (background != null) 40_000 else 4_000
                do {
                    SystemClock.sleep(if (background != null) 1_600 else 3_500)
                    assertTrue("Could not capture the $stage screenshot", device.takeScreenshot(image))
                    if (reference == null) return image
                    val current = requireNotNull(BitmapFactory.decodeFile(image.path))
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
                    if (changed >= 150) return image
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError(
                    "Actual textured anime $stage mesh is invisible on Android " +
                        android.os.Build.VERSION.SDK_INT + "; see ${image.name}",
                )
            } finally {
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
        } finally {
            // Restore the user's sample project to the authored hero pose.
            store.save(project)
        }
    }
}
