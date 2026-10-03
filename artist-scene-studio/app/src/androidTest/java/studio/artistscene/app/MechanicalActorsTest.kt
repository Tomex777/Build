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
    @Test fun steeringAndDoorsMoveRenderedPartsAndSurviveReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val store = SceneProjectStore(context)
        val proof = requireNotNull(context.getExternalFilesDir(null))
        fun find(tag: String) = requireNotNull(device.wait(Until.findObject(By.res(tag)), 30_000)) { "Missing $tag" }
        fun visible(tag: String): androidx.test.uiautomator.UiObject2 {
            repeat(10) {
                val content = find("context-sheet-content")
                device.findObject(By.res(tag))?.let {
                    val bounds = it.visibleBounds
                    if (bounds.height() >= 32 && bounds.top >= content.visibleBounds.top &&
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
        listOf("bicycle", "car").forEach { model ->
            val id = "mechanical-$model-test"
            val actor = PrototypeScene.starterAssets().first { it.asset?.assetId == "starter.mise.$model" }.copy(id = "test-$model")
            store.save(SceneProject(id = id, name = "$model parts", actors = listOf(actor), cameras = listOf(
                SceneCamera("camera-main", "Camera", position = Vec3(3.5f, 2.3f, 4.5f), target = Vec3(0f, .65f, 0f)),
            )))
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    find("project-open-$id").click()
                    find("inspector").click()
                    val control = if (model == "bicycle") "front-steering" else "left-door"
                    visible("part-$control-positive")
                    find("close-context-sheet").click()
                    val before = shot("$model-before")
                    find("inspector").click()
                    repeat(3) { visible("part-$control-positive").click(); device.waitForIdle() }
                    find("close-context-sheet").click()
                    val after = shot("$model-after")
                    val a = requireNotNull(BitmapFactory.decodeFile(before.path))
                    val b = requireNotNull(BitmapFactory.decodeFile(after.path))
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
                    val boneName = if (model == "bicycle") "Front steering" else "Left door"
                    val bone = saved.rigDefinition!!.bones.single { it.name == boneName }
                    assertEquals(Vec3(y = 30f), saved.rig!!.joints[bone.id])
                    if (model == "bicycle") assertEquals(5f, saved.transform.rotationEulerDegrees.z, .001f)
                    shot("$model-saved")
                }
                val expected=store.load(id).actors.single()
                ActivityScenario.launch(MainActivity::class.java).use {
                    find("project-open-$id").click()
                    find("inspector").click()
                    visible(if (model == "bicycle") "part-front-steering-value" else "part-left-door-value")
                    find("close-context-sheet").click()
                    save()
                    val restored=store.load(id).actors.single()
                    assertEquals(expected.rig,restored.rig)
                    assertEquals(expected.transform,restored.transform)
                }
            } finally { store.delete(id) }
        }
        val treeId="mechanical-tree-test"
        val tree=PrototypeScene.starterAssets().first { it.name == "Tree" }.copy(id="test-tree")
        store.save(SceneProject(id=treeId,name="Tree",actors=listOf(tree),cameras=listOf(
            SceneCamera("camera-main","Camera",position=Vec3(4f,2.5f,6f),target=Vec3(0f,1.4f,0f)),
        )))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                find("project-open-$treeId").click(); find("inspector")
                shot("tree")
            }
        } finally { store.delete(treeId) }
    }
}
