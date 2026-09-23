package com.night.cortex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortexSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensHomeAndCreatesPhoneLocalProjectFile() {
        composeRule.onNodeWithText("Night control center").assertIsDisplayed()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Your local project is empty").assertIsDisplayed()
        composeRule.onNodeWithText("New file").performClick()
        composeRule.onNodeWithText("File path").performTextInput("index.js")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Start writing…")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Saved on this phone.")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Saved on this phone.").assertIsDisplayed()
    }
}
