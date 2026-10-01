package com.night.cortex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.night.cortex.hosting.canSaveHttpsConnection
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
        composeRule.onNodeWithText("Connect server").assertIsDisplayed()

        composeRule.onNodeWithTag("server-tab-console").assertIsSelected()
        listOf("pairing", "files", "environment", "backups", "startup", "settings", "activity").forEach { tab ->
            composeRule.onNodeWithTag("server-tab-$tab")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
                .assertIsSelected()
            composeRule.onNodeWithText("Connect server").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("server-tab-console")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
    }

    @Test
    fun savedTokenCannotCrossServerBoundary() {
        check(
            canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "https://cortex-one.example/",
                hasSavedToken = true,
                enteredToken = "",
            )
        )
        check(
            !canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "https://cortex-two.example",
                hasSavedToken = true,
                enteredToken = "",
            )
        )
        check(
            canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "https://cortex-two.example",
                hasSavedToken = true,
                enteredToken = "new-server-token",
            )
        )
        check(
            canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "HTTPS://cortex-one.example/",
                hasSavedToken = true,
                enteredToken = "",
            )
        )
        check(
            !canSaveHttpsConnection(
                savedEndpoint = "",
                candidateEndpoint = "https://cortex-one.example",
                hasSavedToken = false,
                enteredToken = "",
            )
        )
        check(
            !canSaveHttpsConnection(
                savedEndpoint = "https://cortex-one.example",
                candidateEndpoint = "http://cortex-one.example",
                hasSavedToken = true,
                enteredToken = "token",
            )
        )
    }

}
