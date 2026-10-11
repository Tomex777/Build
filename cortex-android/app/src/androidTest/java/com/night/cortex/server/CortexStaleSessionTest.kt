package com.night.cortex.server

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.night.cortex.ui.theme.CortexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortexStaleSessionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun offlineAccountsShowLastKnownStatusAndBlockRepair() {
        rule.setContent {
            CortexTheme {
                CortexPairingScreen(
                    state = PairingState(
                        version = "2.3.0",
                        destination = "A",
                        accounts = listOf(PairingAccount(
                            id = "A",
                            enabled = true,
                            connected = false,
                            status = "auth-invalid",
                            numberMasked = "234••••0531",
                            indexCount = 0,
                            indexLimit = 5000,
                            pairingMode = "",
                            pairingCode = "",
                            pairingQr = "",
                            pairingError = "",
                            displayName = "main",
                        )),
                    ),
                    busy = true,
                    offline = true,
                    refreshEnabled = true,
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
        rule.onNodeWithTag("stale-account-status-warning").assertIsDisplayed()
        rule.onNodeWithText("Re-pair account").assertIsNotEnabled()
    }
}
