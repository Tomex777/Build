package com.tomex777.annie

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicSeekBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tapDragAndDisabledStateRespectTheMusicSeekTarget() {
        val progress = mutableFloatStateOf(0f)
        val enabled = mutableStateOf(true)
        compose.setContent {
            AnnieTheme {
                MusicSeekBar(progress.floatValue, enabled.value, { progress.floatValue = it },
                    Modifier.width(240.dp).testTag("music_seek_proof"))
            }
        }
        compose.onNodeWithTag("music_seek_proof").performTouchInput {
            click(Offset(size.width * .7f, center.y))
        }
        compose.runOnIdle { assertTrue("Tapping the track did not seek", progress.floatValue in .65f.. .75f) }
        compose.onNodeWithTag("music_seek_proof").performTouchInput {
            swipe(Offset(size.width * .7f, center.y), Offset(size.width * .25f, center.y), 800)
        }
        compose.runOnIdle {
            assertTrue("The drag lost its callback during playback recomposition", progress.floatValue in .20f.. .30f)
            enabled.value = false
        }
        val before = progress.floatValue
        compose.onNodeWithTag("music_seek_proof").performTouchInput { click(Offset(size.width * .9f, center.y)) }
        compose.runOnIdle { assertTrue("An unavailable track accepted seeking", progress.floatValue == before) }
    }
}
