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
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.night.cortex.server.CortexPairingScreen
import com.night.cortex.server.BotProfileOption
import com.night.cortex.server.AccountDiagnostics
import com.night.cortex.server.AccountDiagnosticEvent
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
        composeRule.onNodeWithTag("pairing-account-list").performScrollToIndex(3)
        composeRule.onNodeWithText("Work").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing-account-list").performScrollToIndex(4)
        composeRule.onNodeWithText("Archive").assertIsDisplayed()
        composeRule.onNodeWithTag("pairing-account-list").performScrollToIndex(0)
        composeRule.onNodeWithText("Add number").assertIsDisplayed()
        composeRule.onNodeWithText("1 connected · 4 total · CC: Main [account-1]").assertIsDisplayed()
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

        composeRule.onNodeWithText("WhatsApp Accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Main Control").assertIsDisplayed()
        composeRule.onNodeWithText("Account B").assertIsDisplayed()
        // MSCC fixes the CC destination to Account A; Cortex must not offer a broken action.
        composeRule.onNodeWithText("Make destination").assertDoesNotExist()
        composeRule.onNodeWithTag("fixed-cc-destination-note").assertIsDisplayed()
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
        composeRule.onNodeWithText("QR appears only because you selected QR pairing.").performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithText("No WhatsApp accounts configured yet").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-cc-destination-note").assertDoesNotExist()
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
        composeRule.onNodeWithText("This code is temporary. If it expires, start pairing again.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for link…").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("Pair account").fetchSemanticsNodes().isEmpty()) {
            "Pairing-active state must not expose a second Pair account action"
        }
        saveVisualEvidence("cortex-pairing-code-emulator.png", "pairing-screen-root")
    }

    @Test
    fun duplicateJosiaNamesShowDistinctAccountIdsAndActualProfiles() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.3.1",
                        destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "account-2",
                                displayName = "Josia",
                                profile = "josiah",
                                profileDisplayName = "Josia",
                                enabled = true,
                                connected = true,
                                status = "connected",
                                numberMasked = "234••••0002",
                                indexCount = 0,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                            PairingAccount(
                                id = "account-3",
                                displayName = "Josia",
                                profile = "unassigned",
                                enabled = true,
                                connected = false,
                                status = "offline",
                                numberMasked = "234••••0003",
                                indexCount = 0,
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

        composeRule.onNodeWithTag("duplicate-session-name-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("session-title-account-2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Josia [account-2]").assertIsDisplayed()
        composeRule.onNodeWithTag("session-profile-account-2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Josia (josiah)").assertIsDisplayed()
        composeRule.onNodeWithTag("session-title-account-3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Josia [account-3]").assertIsDisplayed()
        composeRule.onNodeWithTag("session-profile-account-3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Unassigned").assertIsDisplayed()
    }

    @Test
    fun renameAndBotProfileSelectionRemainSeparate() {
        var renamed: Pair<String, String>? = null
        var assigned: Pair<String, String>? = null
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.3.1",
                        destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "account-2",
                                displayName = "Josia",
                                profile = "josiah",
                                profileDisplayName = "Josia",
                                enabled = true,
                                connected = true,
                                status = "connected",
                                numberMasked = "234••••0002",
                                indexCount = 0,
                                indexLimit = 5000,
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                            ),
                        ),
                        profiles = listOf(
                            BotProfileOption("control", "Control"),
                            BotProfileOption("josiah", "Josia"),
                            BotProfileOption("nami", "Nami"),
                            BotProfileOption("unassigned", "Unassigned"),
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
                    onRename = { id, name -> renamed = id to name },
                    onAssignProfile = { id, profile -> assigned = id to profile },
                )
            }
        }
        composeRule.onNodeWithTag("rename-account-account-2").performScrollTo().performClick()
        composeRule.onNodeWithTag("rename-session-name").performTextReplacement("Night Backup")
        composeRule.onNodeWithTag("confirm-rename-session").performClick()
        composeRule.runOnIdle {
            check(renamed == ("account-2" to "Night Backup"))
            check(assigned == null)
        }

        composeRule.onNodeWithTag("edit-profile-account-2").performScrollTo().performClick()
        composeRule.onNodeWithTag("choose-bot-profile-nami").performClick()
        composeRule.onNodeWithTag("confirm-bot-profile").performClick()
        composeRule.runOnIdle {
            check(assigned == ("account-2" to "nami"))
            check(renamed == ("account-2" to "Night Backup"))
        }
    }

    @Test
    fun pausedSessionCanResumeWithoutStartingFreshPairing() {
        var resumedId = ""
        var pairingStarted = false
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.3.1",
                        destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "account-2", displayName = "Night Backup",
                                enabled = true, connected = false,
                                status = "paused", paused = true, registered = true,
                                numberMasked = "234••••0002", indexCount = 0, indexLimit = 5000,
                                pairingMode = "", pairingCode = "", pairingQr = "", pairingError = "",
                                disconnectReason = "Manually paused. Resume this account to reconnect.",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> pairingStarted = true },
                    onReconnect = { resumedId = it },
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag("resume-session-account-2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("resume-session-account-2").performScrollTo().performClick()
        composeRule.runOnIdle {
            check(resumedId == "account-2")
            check(!pairingStarted)
        }
    }

    @Test
    fun registeredSessionRequiresConfirmationBeforeRepair() {
        var reconnectId = ""
        var repaired: Pair<String, String>? = null
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.3.1",
                        destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "account-2", displayName = "Josia",
                                enabled = true, connected = false,
                                registered = true, status = "offline",
                                numberMasked = "234••••0002", indexCount = 0, indexLimit = 5000,
                                pairingMode = "", pairingCode = "", pairingQr = "", pairingError = "",
                                disconnectReason = "Connection dropped.",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {},
                    onAddAccount = { _, _ -> },
                    onDestination = {},
                    onPair = { _, _ -> error("Existing auth should not be paired as a new account") },
                    onReconnect = { reconnectId = it },
                    onDisconnect = {},
                    onRemove = {},
                    onRepair = { id, mode -> repaired = id to mode },
                )
            }
        }
        composeRule.onNodeWithTag("reconnect-session-account-2").performScrollTo().performClick()
        composeRule.runOnIdle { check(reconnectId == "account-2") }
        composeRule.onNodeWithText("Re-pair").performClick()
        settleBottomSheet()
        composeRule.onNodeWithText("Link with phone number").performClick()
        composeRule.runOnIdle { check(repaired == null) }
        composeRule.onNodeWithTag("confirm-repair-session").performClick()
        composeRule.runOnIdle { check(repaired == ("account-2" to "code")) }
    }

    @Test
    fun sessionDiagnosticsShowsOnlyAccountEventsAndSupportsRefresh() {
        var refreshTarget = ""
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.3.1", destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "account-2", displayName = "Josia",
                                enabled = true, connected = false, status = "reconnecting",
                                numberMasked = "234••••0002", indexCount = 0, indexLimit = 5000,
                                pairingMode = "", pairingCode = "", pairingQr = "", pairingError = "",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {}, onAddAccount = { _, _ -> },
                    onDestination = {}, onPair = { _, _ -> }, onReconnect = {},
                    onDisconnect = {}, onRemove = {}, onRepair = { _, _ -> },
                    diagnosticsAccountId = "account-2",
                    diagnostics = AccountDiagnostics(
                        accountId = "account-2", accountName = "Josia",
                        status = "reconnecting", profile = "josiah", connected = false,
                        lastConnectedAt = 0L, lastDisconnectedAt = 0L,
                        reconnectAttempts = 2, nextReconnectAt = 0L,
                        disconnectReason = "Connection dropped. Retrying automatically.",
                        events = listOf(
                            AccountDiagnosticEvent(
                                id = "event-1", at = "2026-10-09T12:00:00Z",
                                action = "account.disconnected", detail = "reasonCode: 408",
                            ),
                        ),
                    ),
                    onDiagnostics = { refreshTarget = it },
                )
            }
        }
        composeRule.onNodeWithText("Session diagnostics").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostic-event-event-1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("refresh-account-diagnostics").performClick()
        composeRule.runOnIdle { check(refreshTarget == "account-2") }
    }

    @Test
    fun accountDashboardSearchAndFilterWorkWithoutChangingSessions() {
        composeRule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.3.1",
                        destination = "A",
                        accounts = listOf(
                            PairingAccount(
                                id = "A", displayName = "Main Control", enabled = true,
                                connected = true, status = "connected", numberMasked = "234••••0001",
                                indexCount = 0, indexLimit = 5000, pairingMode = "",
                                pairingCode = "", pairingQr = "", pairingError = "", profile = "control",
                            ),
                            PairingAccount(
                                id = "account-2", displayName = "Nami", enabled = true,
                                connected = false, status = "paused", paused = true,
                                numberMasked = "234••••0002", indexCount = 0, indexLimit = 5000,
                                pairingMode = "", pairingCode = "", pairingQr = "", pairingError = "",
                                profile = "nami",
                            ),
                        ),
                    ),
                    busy = false,
                    onRefresh = {}, onAddAccount = { _, _ -> }, onDestination = {},
                    onPair = { _, _ -> }, onReconnect = {}, onDisconnect = {},
                    onRemove = {}, onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("account-search-field").assertIsDisplayed()
        saveVisualEvidence("cortex-accounts-dashboard-emulator.png", "pairing-screen-root")
        composeRule.onNodeWithTag("account-filter-paused").performClick()
        composeRule.onNodeWithTag("session-title-account-2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("session-title-A").assertDoesNotExist()
        composeRule.onNodeWithTag("account-search-field").performTextReplacement("nothing here")
        composeRule.onNodeWithText("No matching accounts").assertIsDisplayed()
        composeRule.onNodeWithTag("reset-account-filters").performClick()
        composeRule.onNodeWithTag("session-title-A").performScrollTo().assertIsDisplayed()
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
