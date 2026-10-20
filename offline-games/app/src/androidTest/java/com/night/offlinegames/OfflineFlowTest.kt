package com.night.offlinegames

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class OfflineFlowTest {
    @Test fun nativeHubGamesWorkWithoutNetworkPermission() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val granted = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse("App must never request INTERNET", Manifest.permission.INTERNET in granted)

        val device = UiDevice.getInstance(instrument)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotNull("Hub is not visible", device.wait(Until.findObject(By.text("Offline collection")), 15000))
            saveScreenshot(device, context, "01-hub.png")

            val entry2048 = device.wait(Until.findObject(By.text("2048")), 6000)
            assertNotNull("2048 missing from hub", entry2048)
            entry2048.click()
            assertNotNull("Native 2048 failed to open", device.wait(Until.findObject(By.text("New game")), 7000))
            saveScreenshot(device, context, "02-2048.png")

            device.pressBack()
            assertNotNull("Did not return from 2048", device.wait(Until.findObject(By.text("Offline collection")), 7000))

            // Native Minesweeper must work without any system WebView provider.
            val mines = device.wait(Until.findObject(By.desc("Open Minesweeper")), 7000)
            assertNotNull("Native Minesweeper missing from hub", mines)
            mines!!.click()
            val center = device.wait(Until.findObject(By.desc("Mine cell 5, 5: hidden")), 9000)
            assertNotNull("Minesweeper native touch cells missing", center)
            saveScreenshot(device, context, "03-native-minesweeper-opening.png")
            center!!.click()
            SystemClock.sleep(700)
            saveScreenshot(device, context, "03-native-minesweeper-after-tap.png")
            val savedMineState = NativeMinesEngine.decode(
                context.getSharedPreferences("native_mines", android.content.Context.MODE_PRIVATE)
                    .getString("board", null))
            org.junit.Assert.assertTrue("Minesweeper tap did not reach Kotlin engine",
                savedMineState != null && savedMineState.revealed.isNotEmpty())
            // UIAutomator can retain an outdated accessibility snapshot of
            // Compose Canvas cells. Verify the actual tapped native cell and HUD.
            org.junit.Assert.assertTrue("Minesweeper center square was not revealed",
                4 * NativeMinesEngine.side + 4 in savedMineState!!.revealed)
            assertNotNull("Native Minesweeper HUD failed to render after reveal",
                device.wait(Until.findObject(By.textStartsWith("SAFE ")), 7000))
            saveScreenshot(device, context, "03-native-minesweeper-revealed.png")
            device.pressBack()
            assertNotNull("Did not return from native Minesweeper",
                device.wait(Until.findObject(By.text("Offline collection")), 7000))

            // This is now a genuine Kotlin/Compose Tic-Tac-Toe screen — not a DOM.
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull("Search is not discoverable", search)
            search!!.text = "Tic-Tac-Toe"
            val tic = device.wait(Until.findObject(By.desc("Open Tic-Tac-Toe")), 9000)
            assertNotNull("Native Tic-Tac-Toe absent", tic)
            tic!!.click()
            assertNotNull("Native Tic-Tac-Toe cells not mounted", device.wait(Until.findObject(By.descContains("Cell 1:")), 8000))
            device.findObject(By.text("New game")).click()
            assertNotNull("Native Tic-Tac-Toe didn't display its turn",
                device.wait(Until.findObject(By.text("Your move")), 8000))
            assertNotNull("The first native tap cell is missing",
                device.wait(Until.findObject(By.descContains("Cell 1: empty")), 8000))
            saveScreenshot(device, context, "04-native-tictactoe.png")
            device.findObject(By.descContains("Cell 1: empty")).click()
            assertNotNull("Touch failed to mark X",
                device.wait(Until.findObject(By.descContains("Cell 1: X")), 8000))
            assertNotNull("Kotlin opponent did not answer",
                device.wait(Until.findObject(By.descContains(": O")), 8000))
            saveScreenshot(device, context, "04-native-tictactoe-touched.png")
            device.pressBack()
            assertNotNull("Could not leave the native game",
                device.wait(Until.findObject(By.text("Offline collection")), 7000))
        }
    }

    /** UIAutomator text queries are not reliable for HTML inside Android WebView. */
    private fun awaitJavaScript(
        scenario: ActivityScenario<MainActivity>, expression: String, timeoutMillis: Long
    ): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var last = "WebView not available"
        while (SystemClock.elapsedRealtime() < deadline) {
            last = evaluateJavaScript(scenario, expression)
            if (last == "true") break
            SystemClock.sleep(400)
        }
        return last
    }

    private fun evaluateJavaScript(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val reply = AtomicReference("WebView not mounted")
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = findWebView(activity.window.decorView)
            if (webView == null) latch.countDown()
            else webView.evaluateJavascript(expression) { value ->
                reply.set(value ?: "null")
                latch.countDown()
            }
        }
        if (!latch.await(4, TimeUnit.SECONDS)) return "WebView evaluateJavascript timed out"
        return reply.get()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun diagnose(scenario: ActivityScenario<MainActivity>) = evaluateJavaScript(scenario, """
        (function(){
            return JSON.stringify({
                url:location.href,ready:document.readyState,
                title:document.title,body:document.body ? document.body.innerText.slice(0,180) : '',
                resources:performance.getEntriesByType('resource').length,
                userAgent:navigator.userAgent,
                gameErrors:window.__nightGameErrors || []
            });
        })()
    """.trimIndent())

    private fun saveScreenshot(device: UiDevice, context: android.content.Context, filename: String) {
        val directory = context.getExternalFilesDir(null)
        assertNotNull("Android screenshot directory unavailable", directory)
        device.waitForIdle()
        val file = File(directory!!, filename)
        org.junit.Assert.assertTrue("Capture failed: $filename", device.takeScreenshot(file))
        org.junit.Assert.assertTrue("Screenshot empty: $filename", file.exists() && file.length() > 1000L)
    }
}
