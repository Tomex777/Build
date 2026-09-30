package app.nami.android.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the real, minified, signer-matched production APK without depending on
 * Compose UI Test classes from the target classloader. UIAutomator proves the product
 * semantics are present and PixelCopy captures the rendered app window directly.
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
            instrumentation.waitForIdleSync()

            val bitmap = captureWindow(activity)
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

            val destination = File(
                instrumentationContext.filesDir,
                "nami-release-launch-compose.png",
            )
            FileOutputStream(destination).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
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

    private fun captureWindow(activity: MainActivity): Bitmap {
        var width = 0
        var height = 0
        instrumentation.runOnMainSync {
            width = activity.window.decorView.width
            height = activity.window.decorView.height
        }
        assertTrue("Production window has no drawable size", width > 0 && height > 0)

        var lastResult = PixelCopy.ERROR_UNKNOWN
        repeat(5) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val completed = CountDownLatch(1)
            val result = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
            PixelCopy.request(
                activity.window,
                bitmap,
                { copyResult ->
                    result.set(copyResult)
                    completed.countDown()
                },
                Handler(Looper.getMainLooper()),
            )
            assertTrue(
                "Timed out while copying the production window",
                completed.await(10, TimeUnit.SECONDS),
            )
            lastResult = result.get()
            if (lastResult == PixelCopy.SUCCESS) {
                return bitmap
            }
            bitmap.recycle()
            Thread.sleep(250)
        }

        assertEquals("PixelCopy failed for production window", PixelCopy.SUCCESS, lastResult)
        throw AssertionError("PixelCopy did not return a production frame")
    }
}
