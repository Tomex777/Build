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

        fun screenshot(stage: String): File {
            // After rig discovery, wait for a real surface frame, not just composed UI.
            SystemClock.sleep(3_500)
            val image = File(proof, "anime-$stage.png")
            assertTrue("Could not capture the $stage screenshot", device.takeScreenshot(image))
            return image
        }

        fun waitForRig() {
            val deadline = SystemClock.uptimeMillis() + 90_000
            while (SystemClock.uptimeMillis() < deadline) {
                val actor = store.load(project.id).actors.first { it.id == actorId }
                if ((actor.rigDefinition?.bones?.size ?: 0) >= 150) {
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
            throw AssertionError("The real 163-joint anime GLB never completed discovery/persistence")
        }

        fun distinctPixelDifference(first: File, second: File): Int {
            val a = requireNotNull(BitmapFactory.decodeFile(first.path))
            val b = requireNotNull(BitmapFactory.decodeFile(second.path))
            assertEquals(a.width, b.width)
            assertEquals(a.height, b.height)
            var changed = 0
            // Ignore tool rail and top/bottom chrome. This area contains the figure.
            for (y in a.height * 20 / 100 until a.height * 78 / 100 step 3) {
                for (x in a.width * 20 / 100 until a.width * 80 / 100 step 3) {
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
            val posed: File
            ActivityScenario.launch(MainActivity::class.java).use {
                openScene()
                waitForRig()
                assertEquals(expected.values.toSet(), store.load(project.id)
                    .actors.first { it.id == actorId }.rig!!.joints.values.toSet())
                posed = screenshot("posed")
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
                neutral = screenshot("neutral")
            }
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
