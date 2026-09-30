package com.night.cortex

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CortexSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensFamiliarServerPanelWithoutNetworkTab() {
        composeRule.onNodeWithText("Cortex").assertIsDisplayed()
        composeRule.onNodeWithText("Console").assertIsDisplayed()
        composeRule.onNodeWithText("Pairing").assertIsDisplayed()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Backups").assertIsDisplayed()
        composeRule.onNodeWithText("Startup").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Activity").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()

        composeRule.onNodeWithTag("server-tab-console").assertIsSelected()
        listOf("pairing", "files", "backups", "startup", "settings", "activity").forEach { tab ->
            composeRule.onNodeWithTag("server-tab-$tab").performClick().assertIsSelected()
            composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("server-tab-console").performClick().assertIsSelected()
        saveHomeVisualEvidence()
    }

    @Test
    fun connectionSetupUsesDarkSurfacesAndReadableFields() {
        // Drive this modal through Android accessibility rather than Compose's
        // idling bridge. ModalBottomSheet animations can keep Espresso busy on
        // software-emulated API 26/36 even when the real UI is responsive.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.uiAutomation
        check(waitForAccessibilityClick(uiAutomation, "Connect", 15_000L)) {
            "Cortex Connect action did not become clickable through the platform UI"
        }
        val expected = listOf("Cortex Agent", "HTTPS Agent URL", "Agent token", "Save connection")
        var visible = emptySet<String>()
        var platformAnrRecovered = false
        val deadline = SystemClock.uptimeMillis() + 20_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val root = uiAutomation.rootInActiveWindow
            visible = accessibilityStrings(root)
            if (expected.all { wanted -> visible.any { it.contains(wanted, ignoreCase = false) } }) break

            // Old/software-emulated Android images can surface a System UI ANR
            // while Cortex remains healthy. Recover only that exact platform
            // dialog by choosing Wait; never dismiss a Cortex/app ANR here.
            if (
                !platformAnrRecovered &&
                visible.any { it == "System UI isn't responding" || it == "Process system isn't responding" } &&
                clickAccessibilityWait(root)
            ) {
                platformAnrRecovered = true
                SystemClock.sleep(1_000L)
                continue
            }
            SystemClock.sleep(250L)
        }
        check(expected.all { wanted -> visible.any { it.contains(wanted, ignoreCase = false) } }) {
            "Cortex connection sheet accessibility content did not become ready. Visible: ${visible.sorted()}"
        }

        val bitmap = checkNotNull(uiAutomation.takeScreenshot()) {
            "Unable to capture Cortex connection setup on API ${android.os.Build.VERSION.SDK_INT}"
        }
        val screenshotPixel = bitmap.getPixel(
            bitmap.width / 2,
            (bitmap.height * 3 / 4).coerceAtMost(bitmap.height - 1),
        )
        val file = File(instrumentation.targetContext.cacheDir, "cortex-connection-setup-emulator.png")
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode Cortex connection setup screenshot"
            }
        }
        check(file.length() > 0L) { "Cortex connection setup screenshot is empty" }
        check(
            android.graphics.Color.red(screenshotPixel) < 220 &&
                android.graphics.Color.green(screenshotPixel) < 220 &&
                android.graphics.Color.blue(screenshotPixel) < 220
        ) { "Connection setup rendered a light fallback surface: #%06X".format(screenshotPixel and 0x00FFFFFF) }
    }

    private fun waitForAccessibilityClick(
        uiAutomation: android.app.UiAutomation,
        text: String,
        timeoutMs: Long,
    ): Boolean {
        var platformAnrRecovered = false
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val root = uiAutomation.rootInActiveWindow
            val visible = accessibilityStrings(root)

            if (
                !platformAnrRecovered &&
                visible.any { it == "System UI isn't responding" || it == "Process system isn't responding" } &&
                clickAccessibilityWait(root)
            ) {
                platformAnrRecovered = true
                SystemClock.sleep(1_000L)
                continue
            }

            if (clickAccessibilityText(root, text)) return true
            SystemClock.sleep(250L)
        }
        return false
    }

    private fun clickAccessibilityText(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false

        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) {
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            }
            for (index in 0 until node.childCount) {
                if (visit(node.getChild(index))) return true
            }
            return false
        }

        return visit(root)
    }

    private fun clickAccessibilityWait(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val isWait = node.text?.toString() == "Wait" ||
                node.viewIdResourceName == "android:id/aerr_wait"
            if (isWait) {
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            }
            for (index in 0 until node.childCount) {
                if (visit(node.getChild(index))) return true
            }
            return false
        }

        return visit(root)
    }

    private fun accessibilityStrings(root: AccessibilityNodeInfo?): Set<String> {
        if (root == null) return emptySet()
        val result = linkedSetOf<String>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(result::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(result::add)
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(root)
        return result
    }

    private fun saveHomeVisualEvidence() {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot(useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "cortex-home-compose.png",
        )
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode Cortex home visual evidence"
            }
        }
        check(file.length() > 0L) { "Cortex home visual evidence is empty" }
    }
}
