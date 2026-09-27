package com.night.cortex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithText("Console").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Health").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Pairing").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Files").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Library").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backups").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Startup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Activity").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()
    }
}
