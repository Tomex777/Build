package studio.artistscene.app

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import android.os.SystemClock
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneProjectStore

class HumanoidAppearanceTest {
    @Test fun bodyShapesRemainPoseableAndSurviveReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SceneProjectStore(context)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val projectId = "humanoid-appearance-test"
        val human = PrototypeScene.starterAssets().first { it.asset?.assetId == PrototypeScene.HUMANOID_ASSET_ID }
            .copy(id = "test-human")
        store.save(SceneProject(id = projectId, name = "Humanoid appearance test", actors = listOf(human)))
        fun find(tag: String, timeout: Long = 10_000): UiObject2 = requireNotNull(
            device.wait(Until.findObject(By.res(tag)), timeout),
        ) { "Missing control: $tag" }
        fun save() {
            find("save-project").click()
            assertTrue("Scene did not save", device.wait(Until.hasObject(By.text("Saved scene")), 10_000))
        }
        fun closeInspector() {
            find("close-context-sheet").click()
            assertTrue(device.wait(Until.gone(By.res("close-context-sheet")), 5_000))
            // Allow the dismissed sheet and the Filament texture to present a clean frame.
            SystemClock.sleep(3_000)
        }
        fun openInspector() {
            find("inspector", 45_000).click()
            find("close-context-sheet")
            find("character-taller")
            device.waitForIdle()
        }
        fun visible(tag: String): UiObject2 {
            device.findObject(By.res(tag))?.let { return it }
            // Scroll within the inspector's actual UI bounds, including after partial expansion.
            device.wait(Until.findObject(By.scrollable(true)), 5_000)?.scroll(Direction.UP, 1f)
            repeat(12) {
                device.findObject(By.res(tag))?.let { return it }
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, .6f)
                device.waitForIdle()
            }
            return find(tag)
        }
        fun captureRenderedBody(stage: String) {
            val shot = File(context.getExternalFilesDir(null), "humanoid-$stage.png")
            val deadline = SystemClock.elapsedRealtime() + 20_000
            do {
                assertTrue("Screenshot failed", device.takeScreenshot(shot))
                val bitmap = requireNotNull(BitmapFactory.decodeFile(shot.path))
                var sum = 0.0
                var squares = 0.0
                var count = 0
                // This scene has a flat ground plane. Torso shading must appear here;
                // a sky/ground-only frame cannot satisfy the body presence check.
                for (y in (bitmap.height * .4f).toInt() until (bitmap.height * .62f).toInt() step 2) {
                    for (x in (bitmap.width * .4f).toInt() until (bitmap.width * .6f).toInt() step 2) {
                        val pixel = bitmap.getPixel(x, y)
                        val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3.0
                        sum += brightness
                        squares += brightness * brightness
                        count++
                    }
                }
                bitmap.recycle()
                val mean = sum / count
                if (squares / count - mean * mean > 16.0) return
                SystemClock.sleep(500)
            } while (SystemClock.elapsedRealtime() < deadline)
            throw AssertionError("Humanoid was not visible in the $stage viewport before timeout")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                find("project-open-$projectId").click()
                openInspector()
                // Appearance controls exist only after Filament has discovered real morph targets.
                val deadline = SystemClock.elapsedRealtime() + 45_000
                while (store.load(projectId).actors.single().rigDefinition?.morphTargets?.size != 4 &&
                    SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
                assertTrue("Filament did not discover four body shapes", store.load(projectId).actors.single().rigDefinition?.morphTargets?.size == 4)
                visible("appearance-increase-body-fat")
                closeInspector()
                captureRenderedBody("before")
                openInspector()
                find("character-taller").click()
                listOf("body-fat", "muscularity", "pointed-ears", "ear-size").forEach { shape ->
                    repeat(4) { visible("appearance-increase-$shape").click(); device.waitForIdle() }
                }
                closeInspector()
                save()
                val shaped = store.load(projectId).actors.single()
                assertTrue("Height did not change", shaped.transform.scale.y > 1f)
                assertTrue("Four real body shapes were not authored", shaped.rig?.morphWeights?.size == 4)
                assertTrue(shaped.rig!!.morphWeights.values.all { weight -> weight >= .39f })
                captureRenderedBody("appearance")
                // The shaped mesh must still expose real joint controls, and persist a pose.
                find("tool-rail-page").click()
                find("pose-tools").click()
                find("joint-marker-head", 20_000).click()
                device.waitForIdle()
                repeat(6) { find("pose-joint-positive").click(); device.waitForIdle() }
                find("pose-done").click()
                save()
                assertTrue("Customized humanoid lost joint posing", store.load(projectId).actors.single().rig!!.joints.isNotEmpty())
                captureRenderedBody("posed")
            }
            val expected = store.load(projectId).actors.single()
            ActivityScenario.launch(MainActivity::class.java).use {
                find("project-open-$projectId").click()
                openInspector()
                visible("appearance-increase-body-fat")
                closeInspector()
                save()
                val restored = store.load(projectId).actors.single()
                assertTrue("Appearance or pose changed on reopen", expected.rig == restored.rig && expected.transform == restored.transform)
            }
        } finally {
            store.delete(projectId)
        }
    }
}
