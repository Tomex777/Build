package studio.artistscene.app

import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.SceneProjectStore

class RendererLaunchTest {
    @Test
    fun viewportDragPersistsAuthoredTransform() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // This test runs in a fresh emulator and must not depend on another workflow
        // having opened the starter scene or on an empty project store.
        SceneProjectStore(context).save(PrototypeScene.create())

        ActivityScenario.launch(MainActivity::class.java).use {
            val openStarter = requireNotNull(
                device.wait(Until.findObject(By.res("project-open-feasibility-stage")), 10_000),
            ) { "Starter scene was not available from the project browser" }
            openStarter.click()

            val moveTool = requireNotNull(
                device.wait(Until.findObject(By.res("tool-move")), 45_000),
            ) { "Move tool was not exposed in the viewport toolbar" }
            moveTool.click()
            val moveX = requireNotNull(
                device.wait(Until.findObject(By.res("gizmo-move-x")), 10_000),
            ) { "Viewport Move X handle was not exposed" }
            assertTrue(
                "Move X handle was visible but was not draggable",
                moveX.isClickable,
            )
            val bounds = moveX.visibleBounds
            device.swipe(bounds.centerX() - 25, bounds.centerY(), bounds.centerX() + 25, bounds.centerY(), 8)

            val inspector = requireNotNull(
                device.wait(Until.findObject(By.res("inspector")), 10_000),
            ) { "Inspector was not available from the viewport tool strip" }
            inspector.click()
            val positionX = requireNotNull(
                device.wait(Until.findObject(By.res("numeric-x")), 10_000),
            ) { "Position X value was not visible in the contextual inspector" }
            val movedX = positionX.text.replace(',', '.').toFloatOrNull()
            assertTrue("Viewport drag did not move X by a visible amount: ${positionX.text}", movedX != null && movedX >= 0.15f)
            requireNotNull(device.wait(Until.findObject(By.res("close-context-sheet")), 10_000)) {
                "Inspector close control was unavailable"
            }.click()

            val save = requireNotNull(
                device.wait(Until.findObject(By.res("save-project")), 10_000),
            ) { "Save control was not exposed to the real activity UI" }
            assertTrue(
                "Save is visible but Compose did not expose it as an actionable button",
                save.isClickable,
            )
            save.click()

            assertTrue(
                "Scene project did not persist",
                device.wait(Until.hasObject(By.text("Saved scene")), 10_000),
            )
            val saved = SceneProjectStore(context).load(PrototypeScene.PROJECT_ID)
            assertTrue("Saved transform differs from the inspector", kotlin.math.abs(
                saved.actors.first { actor -> actor.id == "fixture-boombox" }.transform.position.x - requireNotNull(movedX)
            ) < 0.02f)
            val screenshot = File(context.getExternalFilesDir(null), "instrumented-viewport.png")
            assertTrue("Device screenshot failed", device.takeScreenshot(screenshot))
            val bitmap = requireNotNull(BitmapFactory.decodeFile(screenshot.path))
            var visible = 0
            var sampled = 0
            for (y in (bitmap.height * .18f).toInt() until (bitmap.height * .58f).toInt() step 4) {
                for (x in (bitmap.width * .2f).toInt() until (bitmap.width * .8f).toInt() step 4) {
                    val pixel = bitmap.getPixel(x, y)
                    if (maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) > 18) visible++
                    sampled++
                }
            }
            bitmap.recycle()
            assertTrue("Rendering region is black even though editor controls are visible", visible > sampled / 10)
        }
    }
}
