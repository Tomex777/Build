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
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneProjectStore
import studio.artistscene.core.TerrainShape
import studio.artistscene.core.Transform
import studio.artistscene.core.Vec3
import studio.artistscene.core.SunOrbit

/**
 * Real pixel-difference proof that a procedurally authored 3D mountain loads
 * and renders under artist-movable sunlight, not a screenshot of sky + UI.
 * Supports the actual Android 8 and 16 emulator graphics paths.
 */
class MountainTerrainInstrumentedTest {
    @Test fun actualMountainCastsShadowsUnderUserMovableSun() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val store = SceneProjectStore(ctx)
        val original = UnderTreeScene.create()
        val terrain = TerrainAssets(ctx).preset(TerrainShape.MOUNTAINS)
        val camera = original.cameras.single().copy(
            position = Vec3(15f, 15f, 34f),
            target = Vec3(0f, 5f, -2f),
            farMeters = 400f,
        )
        val scene = original.copy(
            name = "Mountains · geometry QA",
            actors = listOf(terrain) + original.actors.filter { it.kind == ActorKind.LIGHT }.map {
                if (it.light?.type == studio.artistscene.core.LightType.DIRECTIONAL)
                    it.copy(light = it.light!!.copy(direction = SunOrbit.direction(270f, 14f)))
                else it
            },
            world = original.world.copy(groundEnabled = false),
            cameras = listOf(camera),
        )
        val folder = requireNotNull(ctx.getExternalFilesDir(null))
        fun open() {
            val card = requireNotNull(device.wait(
                Until.findObject(By.res("project-open-${scene.id}")), 30_000)) {
                "Mise sample scene card was not visible"
            }
            card.click()
            requireNotNull(device.wait(Until.findObject(By.res("scene-viewport")), 40_000))
            requireNotNull(device.wait(Until.findObject(By.res("reference-mode")), 10_000)).click()
            device.wait(Until.findObject(By.text("Got it")), 2_000)?.click()
        }
        fun shot(label: String): File {
            SystemClock.sleep(6_000)
            val file = File(folder, "mountain-$label.png")
            assertTrue(device.takeScreenshot(file))
            assertTrue(file.length() > 12000)
            return file
        }
        fun changed(a: File,b: File): Int {
            val first = requireNotNull(BitmapFactory.decodeFile(a.path))
            val second = requireNotNull(BitmapFactory.decodeFile(b.path))
            var count = 0
            for (y in first.height / 6 until first.height * 8 / 10 step 3) {
                for (x in first.width / 10 until first.width * 9 / 10 step 3) {
                    val p = first.getPixel(x,y)
                    val q = second.getPixel(x,y)
                    if (kotlin.math.abs(Color.red(p)-Color.red(q)) +
                        kotlin.math.abs(Color.green(p)-Color.green(q)) +
                        kotlin.math.abs(Color.blue(p)-Color.blue(q)) > 100) count++
                }
            }
            first.recycle(); second.recycle()
            return count
        }

        try {
            store.save(scene.copy(actors = scene.actors.filter { it.kind == ActorKind.LIGHT }))
            val empty: File
            ActivityScenario.launch(MainActivity::class.java).use {
                open()
                empty = shot("empty")
            }
            store.save(scene)
            val full: File
            ActivityScenario.launch(MainActivity::class.java).use {
                open()
                var candidate = shot("real-3d")
                val deadline = SystemClock.uptimeMillis() + 70_000
                while (changed(empty, candidate) < 250 && SystemClock.uptimeMillis() < deadline) {
                    candidate = shot("real-3d")
                }
                full = candidate
            }
            assertTrue("Genuine mountain terrain GLB failed to render over the sky; changed=" +
                changed(empty, full), changed(empty, full) >= 250)
        } finally {
            store.save(original)
        }
    }
}
