package studio.artistscene.app

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class RendererLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun realGlbLoadsAndTransformPersistsForProcessRestore() {
        compose.waitUntil(timeoutMillis = 45_000) {
            runCatching {
                compose.onNodeWithTag("asset-status").assertTextContains("Loaded GLB")
            }.isSuccess
        }
        compose.onNodeWithTag("asset-status").assertTextContains("Loaded GLB · Boom Box")
        compose.onNodeWithTag("move-right").performClick()
        compose.onNodeWithTag("actor-x").assertTextContains("0.25")
        compose.onNodeWithTag("save-project").performClick()
        compose.onNodeWithTag("save-status").assertTextContains("Saved scene")
    }
}
