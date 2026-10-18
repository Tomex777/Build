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

/**
 * Screenshots are from Mise's live Filament Android viewport, never generated
 * images or composited mocks. The empty/background comparison catches black
 * screens, unloaded rigs and missing tree geometry.
 */
class UnderTreeSceneInstrumentedTest {
    @Test fun bothRealAnimeStudentsAndTreeRenderAndRemainEditable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = SceneProjectStore(context)
        val scene = UnderTreeScene.create()
        val device = UiDevice.getInstance(instrumentation)
        val proof = requireNotNull(context.getExternalFilesDir(null))

        fun open() {
            requireNotNull(device.wait(
                Until.findObject(By.res("project-open-${scene.id}")), 30_000,
            )) { "Under-tree project was not in Mise browser" }.click()
            requireNotNull(device.wait(Until.findObject(By.res("scene-viewport")), 45_000)) {
                "Real Mise 3D viewport did not open"
            }
        }

        fun capture(stage: String): File {
            // Android 16's first immersive launch shows an OS-owned educational
            // overlay: "Viewing full screen" / "Got it". A dimmed dialog is NOT
            // an app render, so dismiss it before using screenshots as evidence.
            // The user normally acknowledges this once. API 26 may show nothing.
            device.wait(Until.findObject(By.text("Got it")), 2_000)?.click()
            val candidate = File(proof, "under-tree-${stage}-pending.png")
            val image = File(proof, "under-tree-${stage}.png")
            SystemClock.sleep(4_000)
            assertTrue("Device screenshot failed", device.takeScreenshot(candidate))
            assertTrue("Device screenshot was empty", candidate.length() > 4096)
            image.delete()
            assertTrue(candidate.renameTo(image))
            return image
        }

        fun compare(aFile: File, bFile: File, yStart: Int, yEnd: Int): Int {
            val a = requireNotNull(BitmapFactory.decodeFile(aFile.path))
            val b = requireNotNull(BitmapFactory.decodeFile(bFile.path))
            assertEquals(a.width, b.width)
            assertEquals(a.height, b.height)
            var changed = 0
            for (y in a.height * yStart / 100 until a.height * yEnd / 100 step 3) {
                for (x in a.width * 12 / 100 until a.width * 88 / 100 step 3) {
                    val first = a.getPixel(x, y)
                    val second = b.getPixel(x, y)
                    if (kotlin.math.abs(Color.red(first) - Color.red(second)) +
                        kotlin.math.abs(Color.green(first) - Color.green(second)) +
                        kotlin.math.abs(Color.blue(first) - Color.blue(second)) >= 75) changed++
                }
            }
            a.recycle()
            b.recycle()
            return changed
        }

        // The empty frame may be black while its photographic environment is
        // still uploading on Android 16's software GPU. A simple diff between
        // black and an illuminated sky is NOT proof that the models rendered.
        // Require real green oak foliage ABOVE the horizon and white avatar
        // clothing BELOW it. These signatures were measured on genuine API26
        // renderer screenshots; sky blue/clouds and photo grass cannot satisfy
        // both independently.
        fun rendered3dActors(file: File): Pair<Int, Int> {
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
            var foliage = 0
            var clothing = 0
            for (y in bitmap.height * 8 / 100 until bitmap.height * 53 / 100 step 3) {
                for (x in bitmap.width * 6 / 100 until bitmap.width * 94 / 100 step 3) {
                    val c = bitmap.getPixel(x, y)
                    val red = Color.red(c)
                    val green = Color.green(c)
                    val blue = Color.blue(c)
                    if (green > red + 15 && green > blue + 9 && green > 35) foliage++
                }
            }
            for (y in bitmap.height * 44 / 100 until bitmap.height * 84 / 100 step 3) {
                for (x in bitmap.width * 5 / 100 until bitmap.width * 95 / 100 step 3) {
                    val c = bitmap.getPixel(x, y)
                    val red = Color.red(c)
                    val green = Color.green(c)
                    val blue = Color.blue(c)
                    if (red > 192 && green > 187 && blue > 186 &&
                        kotlin.math.abs(red - green) < 40 &&
                        kotlin.math.abs(green - blue) < 50) clothing++
                }
            }
            bitmap.recycle()
            return foliage to clothing
        }

        try {
            // Camera and world remain unchanged, only 3D actors are withheld.
            store.save(scene.copy(actors = scene.actors.filter { it.kind == ActorKind.LIGHT }))
            val empty: File
            ActivityScenario.launch(MainActivity::class.java).use {
                open()
                requireNotNull(device.wait(Until.findObject(By.res("reference-mode")), 10_000))
                    .click()
                empty = capture("empty")
            }
            store.save(scene)
            val finished: File
            ActivityScenario.launch(MainActivity::class.java).use {
                open()
                val deadline = SystemClock.uptimeMillis() + 90_000
                while (SystemClock.uptimeMillis() < deadline) {
                    val students = store.load(scene.id).actors.filter {
                        it.kind == ActorKind.CHARACTER
                    }
                    if (students.size == 2 && students.all {
                            (it.rigDefinition?.bones?.size ?: 0) >= 60 &&
                                it.rig!!.joints.size >= 8 &&
                                it.rig!!.joints.keys.all { key ->
                                    key in it.rigDefinition!!.bones.map { bone -> bone.id }
                                }
                        }) break
                    SystemClock.sleep(450)
                }
                val students = store.load(scene.id).actors.filter { it.kind == ActorKind.CHARACTER }
                assertEquals(2, students.size)
                assertTrue("Both REAL VRoid skins must bind their individual pose bones",
                    students.all { (it.rigDefinition?.bones?.size ?: 0) >= 60 &&
                        (it.rig?.joints?.size ?: 0) >= 8 &&
                        it.rig!!.joints.keys.all { key ->
                            key in it.rigDefinition!!.bones.map { bone -> bone.id }
                        }
                    })
                // This button removes the editor overlays: the resulting PNG is a
                // real render, not a Compose UI screenshot pretending to be art.
                requireNotNull(device.wait(Until.findObject(By.res("reference-mode")), 10_000))
                    .click()
                // GPU texture uploads and SceneView node registration can continue
                // after the native skeleton has been discovered. Never accept a
                // blank "proof" frame: retry real renderer captures until the
                // three-dimensional scene is genuinely visible or time out.
                val readyDeadline = SystemClock.uptimeMillis() + 115_000
                var accepted = capture("clean")
                var foreground = compare(empty, accepted, 19, 81)
                var (foliage, clothing) = rendered3dActors(accepted)
                while ((foreground < 450 || foliage < 150 || clothing < 150) &&
                    SystemClock.uptimeMillis() < readyDeadline) {
                    SystemClock.sleep(1_200)
                    accepted = capture("clean")
                    foreground = compare(empty, accepted, 19, 81)
                    val coverage = rendered3dActors(accepted)
                    foliage = coverage.first
                    clothing = coverage.second
                }
                assertTrue("Photographic sky/ground is visible but no genuine 3D oak " +
                    "canopy pixels rendered; green=$foliage. Background alone is NOT proof.",
                    foliage >= 150)
                assertTrue("Photographic sky/ground is visible but two VRoid meshes " +
                    "are missing; white-clothing pixels=$clothing.", clothing >= 150)
                finished = accepted
            }

            val coverage = rendered3dActors(finished)
            assertTrue("Real oak and both white-clothed actors must render",
                coverage.first >= 150 && coverage.second >= 150)

            // More than one major region must contain actual scene geometry.
            assertTrue("Live Mise render is empty or invisible",
                compare(empty, finished, 19, 81) >= 450)
            assertTrue("Tree canopy / upper bodies are not visible",
                compare(empty, finished, 19, 46) >= 90)
            assertTrue("No grounded pair of students is visible",
                compare(empty, finished, 43, 81) >= 180)
            assertEquals(scene.actors.size, store.load(scene.id).actors.size)
        } finally {
            // The installed app keeps the composed scene, not an empty QA fixture.
            store.save(scene)
        }
    }
}
