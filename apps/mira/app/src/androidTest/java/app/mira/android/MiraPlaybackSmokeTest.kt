package app.mira.android

import android.content.Context
import android.os.SystemClock
import android.view.TextureView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.mira.domain.ResolvedMedia
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MiraPlaybackSmokeTest {
    @Test fun localVideoDecodesAdvancesSeeksAndPauses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        val file = File(context.cacheDir, "mira-playback-fixture.mp4")
        instrumentation.context.assets.open("mira-player-fixture.mp4").use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        val player = MiraVlcPlayer(context)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity -> activity.setContent {
                AndroidView(factory = { TextureView(it).also(player::attach) }, modifier = Modifier.fillMaxSize(), onRelease = { player.detach(it) })
            } }
            instrumentation.runOnMainSync { player.play(ResolvedMedia(file.toURI().toString())) }
            waitFor { player.state.value.videoOutputCount > 0 && player.state.value.positionMs > 250L }
            instrumentation.runOnMainSync { player.seekTo(1000L) }
            waitFor { player.state.value.positionMs >= 1000L }
            instrumentation.runOnMainSync { player.pause() }
            waitFor { !player.state.value.isPlaying }
            val paused = player.state.value.positionMs
            SystemClock.sleep(500L)
            assertTrue("Paused video kept advancing", kotlin.math.abs(player.state.value.positionMs - paused) < 200L)
        } finally {
            instrumentation.runOnMainSync { player.release() }
            scenario.close()
            file.delete()
        }
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100L)
        assertTrue("VLC did not reach required decoded playback state", condition())
    }
}
