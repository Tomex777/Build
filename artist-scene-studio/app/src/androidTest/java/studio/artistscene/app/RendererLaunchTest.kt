package studio.artistscene.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class RendererLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun editorAndLiveRendererFixtureAppear() {
        compose.onNodeWithText("Artist Scene Studio").assertIsDisplayed()
        compose.onNodeWithText("LIVE RENDERER · ENGINEERING FIXTURE").assertIsDisplayed()
    }
}
