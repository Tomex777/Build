package com.night.cortex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    }
}
