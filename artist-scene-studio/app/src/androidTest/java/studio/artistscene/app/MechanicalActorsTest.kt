package studio.artistscene.app

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.SceneCamera
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneProjectStore
import studio.artistscene.core.Vec3

class MechanicalActorsTest {
    @Test fun bicyclePartsPersist() = verifyActor("bicycle")
    @Test fun carPartsPersist() = verifyActor("car")
    @Test fun treeRenders() = verifyActor("tree")

    private fun verifyActor(model: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val store = SceneProjectStore(context)
        val proof = requireNotNull(context.getExternalFilesDir(null))
        fun find(tag: String): androidx.test.uiautomator.UiObject2 {
            val result = device.wait(Until.findObject(By.res(tag)), 30_000)
            if (result == null) {
                device.takeScreenshot(File(proof, "mechanical-$model-missing-$tag.png"))
                device.dumpWindowHierarchy(File(proof, "mechanical-$model-missing-$tag.xml"))
            }
            return requireNotNull(result) { "Missing $tag" }
        }
        fun visible(tag: String): androidx.test.uiautomator.UiObject2 {
            repeat(10) {
                val content = find("context-sheet-content")
                device.findObject(By.res(tag))?.let {
                    val bounds = it.visibleBounds
                    if (bounds.height() >= (if (tag.endsWith("-value")) 12 else 32) && bounds.top >= content.visibleBounds.top &&
                        bounds.bottom < minOf(content.visibleBounds.bottom - 24, device.displayHeight - 72)) return it
                }
                content.scroll(Direction.DOWN, .35f)
                device.waitForIdle()
            }
            return find(tag)
        }
        fun shot(name: String): File {
            SystemClock.sleep(3_000)
            return File(proof, "mechanical-$name.png").also { assertTrue(device.takeScreenshot(it)) }
        }
        fun save() {
            find("save-project").click()
            assertTrue(device.wait(Until.hasObject(By.text("Saved scene")), 10_000))
        }
        if (model != "tree") {
            val id = "mechanical-$model-test"
            val actor = PrototypeScene.starterAssets().first { it.asset?.assetId == "starter.mise.$model" }.copy(id = "test-$model")
            store.save(SceneProject(id = id, name = "$model parts", actors = listOf(actor), cameras = listOf(
                SceneCamera("camera-main", "Camera", position = if (model == "car") Vec3(5.5f, 2.5f, 7f) else Vec3(2.4f, 1.4f, 3.2f), target = Vec3(0f, .65f, 0f)),
            )))
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    find("project-open-$id").click()
                    find("tool-rail-page").click()
                    find("camera-tools").click()
                    visible("frame-selected").click()
                    assertTrue(device.wait(Until.gone(By.res("close-context-sheet")), 5_000))
                    find("tool-rail-page").click()
                    find("inspector").click()
                    // The camera is on +X: the right door is visible and opens with -Y.
                    val control = if (model == "bicycle") "front-steering" else "right-door"
                    val direction = if (model == "bicycle") "positive" else "negative"
                    visible("part-$control-$direction")
                    find("close-context-sheet").click()
                    val before = shot("$model-before")
                    find("inspector").click()
                    repeat(3) { visible("part-$control-$direction").click(); device.waitForIdle() }
                    find("close-context-sheet").click()
                    val after = shot("$model-after")
                    val a = requireNotNull(BitmapFactory.decodeFile(before.path))
                    val b = requireNotNull(BitmapFactory.decodeFile(after.path))
                    if (model == "car") {
                        var cropped = 0
                        for (y in a.height/5 until a.height*4/5) for (x in listOf(2, a.width-3)) {
                            val pixel=a.getPixel(x,y)
                            if (android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel)*1.4f &&
                                android.graphics.Color.green(pixel) > android.graphics.Color.blue(pixel)*1.1f) cropped++
                        }
                        assertTrue("Frame selected cropped the car at the viewport edge", cropped == 0)
                    }
                    var changed = 0
                    for (y in a.height/4 until a.height*3/4 step 2) for (x in a.width/5 until a.width*4/5 step 2) {
                        val ca=a.getPixel(x,y); val cb=b.getPixel(x,y)
                        if (kotlin.math.abs(android.graphics.Color.red(ca)-android.graphics.Color.red(cb))+
                            kotlin.math.abs(android.graphics.Color.green(ca)-android.graphics.Color.green(cb))+
                            kotlin.math.abs(android.graphics.Color.blue(ca)-android.graphics.Color.blue(cb)) > 30) changed++
                    }
                    a.recycle(); b.recycle()
                    assertTrue("$model control did not move visible geometry ($changed pixels)", changed > 75)
                    if (model == "bicycle") {
                        find("inspector").click()
                        visible("bicycle-lean-positive").click()
                        find("close-context-sheet").click()
                    }
                    save()
                    val saved = store.load(id).actors.single()
                    val boneName = if (model == "bicycle") "Front steering" else "Right door"
                    val bone = saved.rigDefinition!!.bones.single { it.name == boneName }
                    assertEquals(Vec3(y = if (model == "bicycle") 30f else -30f), saved.rig!!.joints[bone.id])
                    if (model == "bicycle") assertEquals(5f, saved.transform.rotationEulerDegrees.z, .001f)
                    shot("$model-saved")
                }
                val expected=store.load(id).actors.single()
                ActivityScenario.launch(MainActivity::class.java).use {
                    find("project-open-$id").click()
                    find("inspector").click()
                    visible(if (model == "bicycle") "part-front-steering-value" else "part-right-door-value")
                    find("close-context-sheet").click()
                    save()
                    val restored=store.load(id).actors.single()
                    assertEquals(expected.rig,restored.rig)
                    assertEquals(expected.transform,restored.transform)
                }
            } finally { store.delete(id) }
            return
        }
        val treeId="mechanical-tree-test"
        val tree=PrototypeScene.starterAssets().first { it.name == "Tree" }.copy(id="test-tree")
        store.save(SceneProject(id=treeId,name="Tree",actors=listOf(tree),cameras=listOf(
            SceneCamera("camera-main","Camera",position=Vec3(4f,2.5f,6f),target=Vec3(0f,1.4f,0f)),
        )))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                find("project-open-$treeId").click(); find("inspector")
                // Sample the canopy above the green Y gizmo, so the editor overlay cannot
                // stand in for rendered foliage. Wait for a model frame on slow software GPUs.
                val deadline = SystemClock.uptimeMillis() + 45_000
                var foliage: Int
                do {
                    val bitmap = requireNotNull(BitmapFactory.decodeFile(shot("tree").path))
                    foliage = 0
                    for (y in bitmap.height*15/100 until bitmap.height*55/100 step 2) for (x in bitmap.width*15/100 until bitmap.width*85/100 step 2) {
                        val pixel=bitmap.getPixel(x,y)
                        if (android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel)*1.15f &&
                            android.graphics.Color.green(pixel) > android.graphics.Color.blue(pixel)*1.15f) foliage++
                    }
                    bitmap.recycle()
                } while (foliage <= 500 && SystemClock.uptimeMillis() < deadline)
                assertTrue("Tree foliage was not rendered ($foliage pixels)", foliage > 500)
            }
        } finally { store.delete(treeId) }
    }
}
