package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathRestoreTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun conversationReturnsAfterForceStopAndColdLaunch() {
        compose.onNodeWithText(PROCESS_DEATH_MESSAGE, substring = false).assertIsDisplayed()
        saveEmulatorScreenshot("annie-process-death-restored-chat")
    }
}
