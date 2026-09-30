package app.nami.android.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.nami.android.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the real, minified, signer-matched production APK without depending on
 * Compose UI Test classes from the target classloader. UIAutomator proves the product
 * semantics are present and the real activity hierarchy is rendered into the evidence frame.
 */
@RunWith(AndroidJUnit4::class)
class NamiReleaseLaunchUiTest {

    private val instrumentation by lazy {
        InstrumentationRegistry.getInstrumentation()
    }
    private val instrumentationContext by lazy {
        instrumentation.context
    }
    private val device by lazy {
        UiDevice.getInstance(instrumentation)
    }

    @Test
    fun releaseCandidateRendersProductionHome() {
        val targetContext = instrumentation.targetContext
        val intent = Intent(targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as MainActivity

        try {
            assertTextVisible("Library")
            assertTextVisible("Browse")
            assertTextVisible("More")
            assertEquals(
                "Production Nami package must be foreground before visual capture",
                targetContext.packageName,
                device.currentPackageName,
            )
            instrumentation.waitForIdleSync()

            val bitmap = captureDecorView(activity)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(
                pixels,
                0,
                bitmap.width,
                0,
                0,
                bitmap.width,
                bitmap.height,
            )
            assertTrue(
                "Release visual proof must contain rendered product pixels",
                pixels.any { pixel -> pixel and 0x00FFFFFF != 0 },
            )

            val fileName = "nami-release-launch-compose.png"
            instrumentationContext.openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val destination = instrumentationContext.getFileStreamPath(fileName)
            assertTrue(destination.isFile && destination.length() > 0L)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun assertTextVisible(text: String, timeoutMillis: Long = 30_000L) {
        assertTrue(
            "Timed out waiting for production text: $text",
            device.wait(Until.hasObject(By.text(text)), timeoutMillis),
        )
    }

    private fun captureDecorView(activity: MainActivity): Bitmap {
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val decorView = activity.window.decorView
            val width = decorView.width
            val height = decorView.height
            assertTrue("Production window has no drawable size", width > 0 && height > 0)

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            decorView.draw(Canvas(bitmap))
        }
        return bitmap
    }
}
