package com.night.poetry

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented test — runs on a real Android emulator, NOT a browser preview. */
@RunWith(AndroidJUnit4::class)
class PoetrySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun writeThenDesign() {
        compose.onNodeWithText("Write a poem").performClick()
        compose.onNodeWithTag("poem-title-input").performTextInput("Hardship")
        compose.onNodeWithTag("poem-body-input").performTextInput("Some days are heavy, but the dawn still comes.")
        compose.onNodeWithText("Design a card", substring = true).performClick()
        compose.onNodeWithTag("poetry-card-stage").assertExists()
        compose.onNodeWithText("Export").assertExists()
    }
}
