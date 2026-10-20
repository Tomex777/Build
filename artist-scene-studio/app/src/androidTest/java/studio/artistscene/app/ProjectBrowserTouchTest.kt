package studio.artistscene.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneProjectStore

/** Real Android taps and keyboard editing, not a screenshot-only Compose preview. */
class ProjectBrowserTouchTest {
    @Test fun searchesRenamesAndOpensSavedScene() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val store = SceneProjectStore(context)
        val ids = (0 until 4).map { "mise-a2-ui-fixture-$it" }
        val selectedId = ids.first()
        val proof = requireNotNull(context.getExternalFilesDir(null))

        fun find(tag: String): androidx.test.uiautomator.UiObject2 {
            val result = device.wait(Until.findObject(By.res(tag)), 25_000)
            if (result == null) {
                device.takeScreenshot(File(proof, "mise-a2-missing-$tag.png"))
                device.dumpWindowHierarchy(File(proof, "mise-a2-missing-$tag.xml"))
            }
            return requireNotNull(result) { "Missing $tag" }
        }

        ids.forEachIndexed { index, id ->
            store.save(
                SceneProject(
                    id = id,
                    name = if (index == 0) "Mise A2 Touch Scene" else "Fixture landscape $index",
                ),
            )
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val search = find("search-projects")
                search.setText("  TOUCH scene  ")
                assertTrue(device.wait(Until.hasObject(By.res("project-open-$selectedId")), 10_000))
                for (hiddenId in ids.drop(1)) {
                    assertTrue("Search leaked an unrelated scene", !device.hasObject(By.res("project-open-$hiddenId")))
                }
                assertTrue(device.takeScreenshot(File(proof, "mise-a2-filtered-browser.png")))

                find("project-menu-$selectedId").click()
                find("rename-project-$selectedId").click()
                val field = find("project-name-input")
                field.setText("Mise A2 Renamed")
                find("confirm-project-name").click()
                assertEquals("Mise A2 Renamed", store.load(selectedId).name)

                find("search-projects").setText("renamed")
                val row = find("project-open-$selectedId")
                assertTrue("Full card was not tappable", row.isClickable)
                row.click()
                assertTrue(device.wait(Until.hasObject(By.res("back-to-projects")), 25_000))
                find("back-to-projects").click()
                assertTrue(device.wait(Until.hasObject(By.res("project-open-$selectedId")), 10_000))
                assertEquals("Mise A2 Renamed", store.load(selectedId).name)
            }
        } finally {
            ids.forEach { id -> runCatching { store.delete(id) } }
        }
    }
}
