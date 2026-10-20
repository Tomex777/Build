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
    @Test fun hubNativeAndBundledGameWorkWithoutNetworkPermission() {
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

            val mines = device.wait(Until.findObject(By.text("Minesweeper")), 7000)
            assertNotNull("Minesweeper missing from hub", mines)
            mines.click()
            // WebView accessibility text exposure varies with the Android System WebView
            // version. Assert on the actual rendered DOM and a complete 81-cell game.
            val minesReady = awaitJavaScript(scenario, """
                (function() {
                    var title = document.getElementById('title');
                    return !!title && title.textContent === 'Minesweeper' &&
                        document.querySelectorAll('#mineboard [data-tile]').length === 81;
                })()
            """.trimIndent(), 18000)
            saveScreenshot(device, context, "03-minesweeper.png") // also save the failure state
            assertEquals("Bundled Minesweeper did not render: ${diagnose(scenario)}", "true", minesReady)
            assertEquals("Minesweeper does not respond to a click",
                "true", evaluateJavaScript(scenario, """
                    (function(){
                        document.querySelector('#mineboard [data-tile="0"]').click();
                        return document.querySelectorAll('#mineboard button.revealed').length > 0;
                    })()
                """.trimIndent()))
            saveScreenshot(device, context, "03-minesweeper-touched.png")

            device.pressBack()
            assertNotNull("Did not return from arcade", device.wait(Until.findObject(By.text("Offline collection")), 7000))

            var newGame = device.wait(Until.findObject(By.text("Tic-Tac-Toe")), 1500)
            var swipes = 0
            while (newGame == null && swipes < 7) {
                device.swipe(
                    device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 15
                )
                swipes++
                newGame = device.wait(Until.findObject(By.text("Tic-Tac-Toe")), 1200)
            }
            assertNotNull("Tic-Tac-Toe not listed after scrolling", newGame)
            newGame!!.click()
            val ticTacToeReady = awaitJavaScript(scenario, """
                (function(){
                    var title = document.getElementById('title');
                    var status = document.getElementById('status');
                    return !!title && !!status && title.textContent === 'Tic-Tac-Toe' &&
                        document.querySelectorAll('#board button.cell').length === 9 &&
                        status.textContent === 'Your turn · X';
                })()
            """.trimIndent(), 15000)
            saveScreenshot(device, context, "04-tictactoe.png")
            assertEquals("Tic-Tac-Toe did not render: ${diagnose(scenario)}", "true", ticTacToeReady)
            assertEquals("Tic-Tac-Toe move did not reach the game engine", "true",
                evaluateJavaScript(scenario, """
                    (function(){
                        document.querySelector('#board button.cell').click();
                        return document.querySelectorAll('#board button.cell[disabled]').length >= 2;
                    })()
                """.trimIndent()))
            saveScreenshot(device, context, "04-tictactoe-touched.png")

            device.pressBack()
            assertNotNull("Could not leave the new game", device.wait(Until.findObject(By.text("Offline collection")), 7000))
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
                resources:performance.getEntriesByType('resource').length
            });
        })()
    """.trimIndent())

    private fun saveScreenshot(device: UiDevice, context: android.content.Context, filename: String) {
        val directory = context.getExternalFilesDir(null) ?: return
        device.takeScreenshot(File(directory, filename))
    }
}
