package studio.artistscene.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererLaunchTest {
    @Test
    fun realGlbLoadsAndTransformPersistsForProcessRestore() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

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
            device.pressBack()

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
        }
    }
}
