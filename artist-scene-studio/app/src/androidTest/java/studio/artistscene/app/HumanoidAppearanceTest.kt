package studio.artistscene.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
        fun visible(tag: String): UiObject2 {
            repeat(12) {
                device.findObject(By.res(tag))?.let { return it }
                device.swipe(device.displayWidth / 2, device.displayHeight * 8 / 10,
                    device.displayWidth / 2, device.displayHeight * 4 / 10, 12)
                device.waitForIdle()
            }
            return find(tag)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                find("project-open-$projectId").click()
                find("inspector", 45_000).click()
                // Appearance controls exist only after Filament has discovered real morph targets.
                find("appearance-increase-body-fat", 45_000)
                find("close-context-sheet").click()
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "humanoid-before.png")))
                find("inspector").click()
                find("character-taller").click()
                listOf("body-fat", "muscularity", "pointed-ears", "ear-size").forEach { shape ->
                    repeat(4) { visible("appearance-increase-$shape").click(); device.waitForIdle() }
                }
                find("close-context-sheet").click()
                save()
                val shaped = store.load(projectId).actors.single()
                assertTrue("Height did not change", shaped.transform.scale.y > 1f)
                assertTrue("Four real body shapes were not authored", shaped.rig?.morphWeights?.size == 4)
                assertTrue(shaped.rig!!.morphWeights.values.all { weight -> weight >= .39f })
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "humanoid-appearance.png")))
                // The shaped mesh must still expose real joint controls, and persist a pose.
                find("tool-rail-page").click()
                find("pose-tools").click()
                find("joint-marker-head", 20_000).click()
                find("pose-joint-positive").click()
                find("pose-done").click()
                save()
                assertTrue("Customized humanoid lost joint posing", store.load(projectId).actors.single().rig!!.joints.isNotEmpty())
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "humanoid-posed.png")))
            }
            val expected = store.load(projectId).actors.single()
            ActivityScenario.launch(MainActivity::class.java).use {
                find("project-open-$projectId").click()
                find("inspector", 45_000).click()
                find("appearance-increase-body-fat", 45_000)
                find("close-context-sheet").click()
                save()
                val restored = store.load(projectId).actors.single()
                assertTrue("Appearance or pose changed on reopen", expected.rig == restored.rig && expected.transform == restored.transform)
            }
        } finally {
            store.delete(projectId)
        }
    }
}
