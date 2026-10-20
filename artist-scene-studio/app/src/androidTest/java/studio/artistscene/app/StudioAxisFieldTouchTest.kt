package studio.artistscene.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.artistscene.core.TransformAxis

/** Exercises genuine Compose touch and IME semantics on a running Android device. */
class StudioAxisFieldTouchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingReadoutEditsExactXAndButtonsActOnTheSceneValue() {
        var x by mutableFloatStateOf(1.25f)
        compose.setContent {
            MaterialTheme {
                StudioAxisField(
                    inputKey = "actor:move",
                    axis = TransformAxis.X,
                    value = x,
                    step = .25f,
                    enabled = true,
                    onDelta = { x += it },
                    onSet = { x = it },
                )
            }
        }

        compose.onNodeWithTag("numeric-x").assertTextContains("1.25")
        compose.onNodeWithContentDescription("Increase X").performClick()
        compose.runOnIdle { assertEquals(1.5f, x, 0f) }

        compose.onNodeWithTag("numeric-x").performClick()
        compose.onNodeWithTag("numeric-x").performTextClearance()
        compose.onNodeWithTag("numeric-x").performTextInput("-0.125")
        compose.onNodeWithTag("numeric-x").performImeAction()
        compose.runOnIdle { assertEquals(-.125f, x, 0f) }
        compose.onNodeWithTag("numeric-x").assertTextContains("-0.125")
    }

    @Test fun lockedAxisCannotBeChangedThroughButtons() {
        var x by mutableFloatStateOf(2f)
        compose.setContent {
            MaterialTheme {
                StudioAxisField(
                    inputKey = "locked:scale",
                    axis = TransformAxis.Y,
                    value = x,
                    step = .1f,
                    enabled = false,
                    onDelta = { x += it },
                    onSet = { x = it },
                )
            }
        }
        compose.onNodeWithTag("numeric-y").assertExists()
        compose.onNodeWithContentDescription("Increase Y").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(2f, x, 0f) }
    }
}
