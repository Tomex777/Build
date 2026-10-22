package com.nice.juicewrld

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class JuiceSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun basicNavigationAndSavedEmptyStateWorkOnDevice() {
        rule.onNodeWithText("JUICE WRLD").assertExists()
        rule.onNodeWithText("Saved").performClick()
        rule.onNodeWithText("No saved music yet").assertExists()
        rule.onNodeWithText("Discover").performClick()
        rule.onNodeWithText("The discography").assertExists()
    }
}
