package com.night.cortex

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.night.cortex.hosting.canSaveHttpsConnection
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
        composeRule.onNodeWithText("Accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Backups").assertIsDisplayed()
        composeRule.onNodeWithText("Startup").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        // The Activity tab lives to the right of a horizontally scrollable tab row.
        // Scroll to it before asserting visibility, especially on API 26 devices.
        composeRule.onNodeWithText("Activity").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Connect server").assertIsDisplayed()

        composeRule.onNodeWithTag("server-tab-console").assertIsSelected()
        listOf("pairing", "files", "backups", "startup", "settings", "activity").forEach { tab ->
            composeRule.onNodeWithTag("server-tab-$tab")
                .performScrollTo()
                .performClick()
                .assertIsSelected()
            composeRule.onNodeWithText("Connect server").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("server-tab-console").performScrollTo().performClick().assertIsSelected()
        saveHomeVisualEvidence()
    }

    @Test
    fun connectionSheetOpensFromUnconfiguredConsole() {
        // Keep modal-opening acceptance independent from rapidly switching
        // every screen tab. A software-rendered API 36 emulator may defer a
        // sheet transition after many navigation/recomposition operations.
        composeRule.onNodeWithText("Connect server").assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Connect").performClick()
        composeRule.waitUntil(timeoutMillis = 40_000L) {
            composeRule.onAllNodesWithTag("connection-sheet-root")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("connection-sheet-root").assertIsDisplayed()
        composeRule.onNodeWithText("Server connection").assertIsDisplayed()
        composeRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeRule.onNodeWithText("Access token").assertIsDisplayed()
        saveConnectionVisualEvidence()
    }

    @Test
    fun savedTokenCannotCrossServerBoundary() {
        check(
            canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "https://cortex-one.example/",
                hasSavedToken = true,
                enteredToken = "",
            )
        )
        check(
            !canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "https://cortex-two.example",
                hasSavedToken = true,
                enteredToken = "",
            )
        )
        check(
            canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "https://cortex-two.example",
                hasSavedToken = true,
                enteredToken = "new-server-token",
            )
        )
        check(
            canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "HTTPS://cortex-one.example/",
                hasSavedToken = true,
                enteredToken = "",
            )
        )
        check(
            !canSaveHttpsConnection(
                savedEndpoint = "",
                candidateEndpoint = "https://cortex-one.example",
                hasSavedToken = false,
                enteredToken = "",
            )
        )
        check(
            !canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "http://cortex-one.example",
                hasSavedToken = true,
                enteredToken = "token",
            )
        )
    }

    private fun saveHomeVisualEvidence() {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot(useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        saveVisualEvidence(bitmap, "cortex-home-compose.png", "home")
    }

    private fun saveConnectionVisualEvidence() {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = if (android.os.Build.VERSION.SDK_INT < 28) {
            // Compose cannot capture dialog-window content on API 26/27.
            // Take an actual device screenshot while the sheet is visibly open.
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
                "Unable to capture Cortex connection dialog on API below 28"
            }
        } else {
            composeRule.onNodeWithTag("connection-sheet-root", useUnmergedTree = true)
                .captureToImage()
                .asAndroidBitmap()
        }
        saveVisualEvidence(bitmap, "cortex-connection-setup-compose.png", "connection setup")
    }

    private fun saveVisualEvidence(
        bitmap: android.graphics.Bitmap,
        fileName: String,
        label: String,
    ) {
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            fileName,
        )
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode Cortex $label visual evidence"
            }
        }
        check(file.length() > 0L) { "Cortex $label visual evidence is empty" }
    }
}
