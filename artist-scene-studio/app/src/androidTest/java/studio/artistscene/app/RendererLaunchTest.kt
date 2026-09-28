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
            assertTrue(
                "Real GLB never reached the loaded state; renderer/asset diagnostics are visible in the activity UI",
                device.wait(Until.hasObject(By.textContains("Loaded GLB")), 45_000),
            )
            assertTrue(
                "Filament render loop never reached a live surface after the GLB loaded",
                device.wait(Until.hasObject(By.textContains("Renderer loop active")), 30_000),
            )

            val moveRight = requireNotNull(
                device.wait(Until.findObject(By.res("studio.artistscene.app", "move-right")), 10_000),
            ) { "Move-right control was not exposed to the real activity UI" }
            assertTrue(
                "Move-right is visible but Compose did not expose it as an actionable button",
                moveRight.isClickable,
            )
            moveRight.click()

            assertTrue(
                "Scene-owned prop transform did not update",
                device.wait(Until.hasObject(By.text("X 0.25")), 10_000),
            )

            val save = requireNotNull(
                device.wait(Until.findObject(By.res("studio.artistscene.app", "save-project")), 10_000),
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
