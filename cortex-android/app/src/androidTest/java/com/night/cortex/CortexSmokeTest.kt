package com.night.cortex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        composeRule.onNodeWithText("Bot").assertIsDisplayed()
        composeRule.onNodeWithText("Console").assertIsDisplayed()
        composeRule.onNodeWithText("Pairing").assertIsDisplayed()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Backups").assertIsDisplayed()
        composeRule.onNodeWithText("Startup").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Activity").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()
    }
}
