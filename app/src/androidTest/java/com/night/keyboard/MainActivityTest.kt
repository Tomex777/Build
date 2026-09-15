package com.night.keyboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun homeAndClipboardNavigationRender() {
        compose.onNodeWithText("Keyboard", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Clipboard", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Search clipboard", useUnmergedTree = true).assertIsDisplayed()
    }
}
