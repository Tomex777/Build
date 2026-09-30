package com.night.cortex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.server.CortexPowerControls
import com.night.cortex.ui.theme.CortexTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortexPowerControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restartAndStopRequireDeliberateConfirmation() {
        var sent: HostingPowerAction? = null
        composeRule.setContent {
            CortexTheme {
                CortexPowerControls(
                    busy = false,
                    onPower = { sent = it },
                )
            }
        }

        composeRule.onNodeWithTag("power-restart").performClick()
        assertNull(sent)
        composeRule.onNodeWithText("Restart Night?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-power-restart").performClick()
        assertEquals(HostingPowerAction.RESTART, sent)

        sent = null
        composeRule.onNodeWithTag("power-stop").performClick()
        assertNull(sent)
        composeRule.onNodeWithText("Stop Night?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-power-stop").performClick()
        assertEquals(HostingPowerAction.STOP, sent)
    }
}
