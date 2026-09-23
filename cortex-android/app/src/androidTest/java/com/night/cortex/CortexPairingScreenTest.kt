package com.night.cortex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.night.cortex.server.CortexPairingScreen
import com.night.cortex.server.PairingAccount
import com.night.cortex.server.PairingState
import com.night.cortex.ui.theme.CortexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortexPairingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

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
                    onPair = { _, _ -> },
                    onReconnect = {},
                    onRepair = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("WhatsApp Pairing").assertIsDisplayed()
        composeRule.onNodeWithText("Account A").assertIsDisplayed()
        composeRule.onNodeWithText("Account B").assertIsDisplayed()
        composeRule.onNodeWithText("Pair account").performClick()

        composeRule.onNodeWithText("Pair Account B").assertIsDisplayed()
        composeRule.onNodeWithText("Link with phone number").assertIsDisplayed()
        composeRule.onNodeWithText("PRIMARY").assertIsDisplayed()
        composeRule.onNodeWithText("Use QR code").assertIsDisplayed()
        composeRule.onNodeWithText("Only opens QR pairing when you explicitly choose it").assertIsDisplayed()
    }
}
