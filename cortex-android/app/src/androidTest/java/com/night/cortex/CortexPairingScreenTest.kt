package com.night.cortex

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.night.cortex.server.CortexPairingScreen
import com.night.cortex.server.PairingAccount
import com.night.cortex.server.PairingState
import com.night.cortex.ui.theme.CortexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CortexPairingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersDynamicAccountRegistryAndAddNumber() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.0.0",
                        destination = "account-1",
                        maxAccounts = 5,
                        canAddAccount = true,
                        accounts = listOf(
                            PairingAccount(
                                id = "account-1",
                                displayName = "Main",
                                enabled = true,
                                connected = true,
                                status = "connected",
                                numberMasked = "234••••0001",
                                indexCount = 12,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                            PairingAccount(
                                id = "account-2",
                                displayName = "Second",
                                enabled = true,
                                connected = false,
                                status = "offline",
                                numberMasked = "234••••0002",
                                indexCount = 4,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                            PairingAccount(
                                id = "account-3",
                                displayName = "Work",
                                enabled = true,
                                connected = false,
                                status = "offline",
                                numberMasked = "234••••0003",
                                indexCount = 2,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                            PairingAccount(
                                id = "account-4",
                                displayName = "Archive",
                                enabled = true,
                                connected = false,
                                status = "offline",
                                numberMasked = "234••••0004",
                                indexCount = 1,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> },
                    onReconnect = {},
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Main").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertIsDisplayed()
        composeRule.onNodeWithText("Work").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing-account-list").performScrollToIndex(4)
        composeRule.onNodeWithText("Archive").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing-account-list").performScrollToIndex(0)
        composeRule.onNodeWithText("Add number").assertIsDisplayed()
        composeRule.onNodeWithText("Destination: Main").assertIsDisplayed()
        saveVisualEvidence("cortex-session-active-emulator.png", "pairing-screen-root")
    }

    @Test
    fun codePairingIsPrimaryAndQrRequiresExplicitChoice() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "1.8.1",
                        destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "A",
                                enabled = true,
                                connected = true,
                                status = "connected",
                                numberMasked = "234••••0001",
                                indexCount = 12,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                                profile = "control",
                            ),
                            PairingAccount(
                                id = "B",
                                enabled = true,
                                connected = false,
                                status = "offline",
                                numberMasked = "234••••0002",
                                indexCount = 4,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                                profile = "nami",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> },
                    onReconnect = {},
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("WhatsApp Pairing").assertIsDisplayed()
        composeRule.onNodeWithText("Account A").assertIsDisplayed()
        composeRule.onNodeWithText("Account B").assertIsDisplayed()
        composeRule.onNodeWithText("Profile · Control").assertIsDisplayed()
        composeRule.onNodeWithText("Profile · Nami").assertIsDisplayed()
        saveVisualEvidence("cortex-pairing-profiles-emulator.png", "pairing-screen-root")
        composeRule.onNodeWithText("Pair account").performClick()
        settleBottomSheet()

        composeRule.onNodeWithText("Pair Account B").assertIsDisplayed()
        composeRule.onNodeWithText("Link with phone number").assertIsDisplayed()
        composeRule.onNodeWithText("PRIMARY").assertIsDisplayed()
        composeRule.onNodeWithText("Use QR code").assertIsDisplayed()
        composeRule.onNodeWithText("Only opens QR pairing when you explicitly choose it").assertIsDisplayed()
        saveVisualEvidence("cortex-pairing-method-emulator.png", "pair-method-sheet")
    }
    @Test
    fun explicitQrPairingRendersQrVisualEvidence() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.0.0",
                        destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "A",
                                displayName = "Main",
                                enabled = true,
                                connected = false,
                                status = "pairing",
                                numberMasked = "234••••0001",
                                indexCount = 0,
                                indexLimit = 5000,
                                pairingMode = "qr",
                                pairingCode = "",
                                pairingQr = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAHElEQVR4nGNggIH////DSQYSRBEkdlFydZDmKgAGTEe5xFvhIwAAAABJRU5ErkJggg==",
                                pairingError = "",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> },
                    onReconnect = {},
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("QR PAIRING").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("WhatsApp pairing QR")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("QR appears only because you selected QR pairing.").assertIsDisplayed()
        saveVisualEvidence("cortex-pairing-qr-emulator.png", "pairing-screen-root")
    }

    @Test
    fun emptyRegistryExplainsFirstPairFlow() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.0.0",
                        destination = "",
                        maxAccounts = 5,
                        canAddAccount = true,
                        accounts = emptyList(),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> },
                    onReconnect = {},
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("No accounts paired yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Add number").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Add a number, then link it with the phone-number pairing code. QR remains an explicit alternative."
        ).assertIsDisplayed()
        saveVisualEvidence("cortex-unpaired-emulator.png", "pairing-screen-root")
    }

    @Test
    fun authInvalidAccountRoutesDirectlyToRepairFlow() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.0.0",
                        destination = "B",
                        accounts = listOf(
                            PairingAccount(
                                id = "A",
                                displayName = "Expired",
                                enabled = true,
                                connected = false,
                                status = "auth-invalid",
                                numberMasked = "234••••0001",
                                indexCount = 12,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                            PairingAccount(
                                id = "B",
                                displayName = "Main",
                                enabled = true,
                                connected = true,
                                status = "connected",
                                numberMasked = "234••••0002",
                                indexCount = 4,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> },
                    onReconnect = {},
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("SIGN-IN REQUIRED").assertIsDisplayed()
        saveVisualEvidence("cortex-session-expired-emulator.png", "pairing-screen-root")
        composeRule.onNodeWithText("Re-pair account").performClick()
        settleBottomSheet()
        composeRule.onNodeWithText("Re-pair Expired").assertIsDisplayed()
        composeRule.onNodeWithText("Link with phone number").assertIsDisplayed()
        composeRule.onNodeWithText("PRIMARY").assertIsDisplayed()
        composeRule.onNodeWithText("Use QR code").assertIsDisplayed()
        saveVisualEvidence("cortex-repair-emulator.png", "pair-method-sheet")
    }

    @Test
    fun pairingCodeLifecycleRendersVisualEvidence() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.0.0",
                        destination = "A",
                        maxAccounts = 5,
                        canAddAccount = true,
                        accounts = listOf(
                            PairingAccount(
                                id = "A",
                                displayName = "Main",
                                enabled = true,
                                connected = false,
                                status = "pairing",
                                numberMasked = "234••••0001",
                                indexCount = 12,
                                indexLimit = 5000,
                                pairingMode = "code",
                                pairingCode = "ABCD-EFGH",
                                pairingQr = "",
                                pairingError = "",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> },
                    onReconnect = {},
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("PAIRING CODE").assertIsDisplayed()
        composeRule.onNodeWithText("ABCD-EFGH").assertIsDisplayed()
        composeRule.onNodeWithText("WhatsApp → Linked devices → Link with phone number").assertIsDisplayed()
        composeRule.onNodeWithText("This code is temporary. If it expires, start pairing again.").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for link…").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("Pair account").fetchSemanticsNodes().isEmpty()) {
            "Pairing-active state must not expose a second Pair account action"
        }
        saveVisualEvidence("cortex-pairing-code-emulator.png", "pairing-screen-root")
    }

    private fun settleBottomSheet() {
        // Material3's modal sheet is driven by the Compose animation clock.
        // Advance that clock explicitly so software-emulated API 36 does not
        // spend the Espresso timeout waiting for a transition frame.
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun saveVisualEvidence(name: String, tag: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        node.assertIsDisplayed()

        // Compose cannot capture dialog/window content below API 28. API 26
        // therefore uses UiAutomation for visual evidence, while API 28+
        // captures the rendered Compose surface directly so headless API 36
        // compositor glitches cannot turn valid app evidence black.
        val bitmap = if (android.os.Build.VERSION.SDK_INT < 28) {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
                "Unable to capture Cortex pairing visual evidence"
            }
        } else {
            node.captureToImage().asAndroidBitmap()
        }
        val file = File(instrumentation.targetContext.cacheDir, name)
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode Cortex pairing visual evidence"
            }
        }
        check(file.length() > 0L) { "Cortex pairing visual evidence is empty" }

        val backgroundPixel = bitmap.getPixel(
            (bitmap.width / 2).coerceAtMost(bitmap.width - 1),
            (bitmap.height / 4).coerceAtMost(bitmap.height - 1),
        )
        check(
            android.graphics.Color.red(backgroundPixel) < 220 &&
                android.graphics.Color.green(backgroundPixel) < 220 &&
                android.graphics.Color.blue(backgroundPixel) < 220
        ) {
            "Cortex pairing evidence contains a light fallback background: " +
                "#%06X".format(backgroundPixel and 0x00FFFFFF)
        }
    }
}
