package com.night.cortex

import androidx.compose.ui.test.assertExists
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
        composeRule.onNodeWithText("Cortex").assertIsDisplayed()
        composeRule.onNodeWithText("Console").assertExists()
        composeRule.onNodeWithText("Health").assertExists()
        composeRule.onNodeWithText("Pairing").assertExists()
        composeRule.onNodeWithText("Files").assertExists()
        composeRule.onNodeWithText("Backups").assertExists()
        composeRule.onNodeWithText("Startup").assertExists()
        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithText("Activity").assertExists()
        composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()
    }
}
