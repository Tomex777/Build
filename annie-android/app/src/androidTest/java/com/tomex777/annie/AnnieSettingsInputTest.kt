package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** ABR regression: all config text inputs use Annie's own field, not stock OutlinedTextField. */
@RunWith(AndroidJUnit4::class)
class AnnieSettingsInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun customFieldsEditPlainNumericAndSecretValues() {
        val number = mutableStateOf("")
        val secret = mutableStateOf("")
        compose.setContent {
            AnnieTheme {
                Column {
                    AnnieSettingsInput(
                        value = number.value, onValueChange = { number.value = it },
                        label = "Threshold", numeric = true, tag = "abr_number",
                    )
                    AnnieSettingsInput(
                        value = secret.value, onValueChange = { secret.value = it },
                        label = "API key", secret = true, tag = "abr_secret",
                    )
                }
            }
        }
        compose.onNodeWithTag("abr_number").assertIsDisplayed().performTextInput("12.5")
        compose.onNodeWithTag("abr_secret").assertIsDisplayed().performTextInput("test-only-not-a-real-key")
        compose.runOnIdle {
            assertEquals("12.5", number.value)
            assertEquals("test-only-not-a-real-key", secret.value)
        }
    }

    @Test fun hostAiFormHasExplicitCancelAndCustomInputs() {
        val dismissed = mutableStateOf(false)
        compose.setContent { AnnieTheme { HostAiSettings(onDismiss = { dismissed.value = true }) } }
        compose.onNodeWithTag("host_ai_key_input").assertIsDisplayed()
            .performTextInput("test-only-not-a-real-key")
        compose.onNodeWithTag("host_ai_model_input").assertIsDisplayed()
            .performTextClearance()
        compose.onNodeWithTag("host_ai_model_input").performTextInput("claude-test-model")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed.value) }
    }
}
