package dev.tomex.youtube.testapp

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.core.CachedPlayerScriptSource
import dev.tomex.youtube.core.HttpPlayerScriptSource
import dev.tomex.youtube.core.NativeYouTubeEngine
import dev.tomex.youtube.core.PlayerScriptUrlTransformer
import dev.tomex.youtube.core.PlayerUrlTransforms
import dev.tomex.youtube.core.QuickJsPlayerScriptRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fresh-process API 26 proof that the current live YouTube player bundle can be executed by the
 * bounded QuickJS URL-builder runtime without relying on hard-coded minified function names.
 */
@RunWith(AndroidJUnit4::class)
class Api26PlayerRuntimeTest {
    @Test
    fun currentPlayerUrlBuilderTransformsNInFreshProcess() = runBlocking {
        assumeTrue("API 26-specific player-runtime gate", Build.VERSION.SDK_INT <= 26)

        val source = CachedPlayerScriptSource(HttpPlayerScriptSource())
        val engine = NativeYouTubeEngine(playerScriptSource = source)
        val diagnostics = engine.currentPlayerScriptDiagnostics()
        assertTrue(diagnostics.scriptBytes >= 16 * 1024)
        assertTrue(diagnostics.nParameter.urlBuilderCandidates.isNotEmpty())

        val inputN = "abcdefghijklmnopqrstuvwxyz"
        val transformed = PlayerScriptUrlTransformer(
            source = source,
            runtime = QuickJsPlayerScriptRuntime(
                diagnosticSink = { diagnostic ->
                    println("YT_PROOF api26-player-runtime-stage " + diagnostic)
                }
            )
        ).transform(
            playerJavaScriptUrl = diagnostics.playerJavaScriptUrl,
            mediaUrl = "https://rr1---sn.example.googlevideo.com/videoplayback?itag=313&n=" + inputN
        )
        assertNotNull("Current live player URL builder did not execute on API 26", transformed)
        val outputN = PlayerUrlTransforms.extractN(requireNotNull(transformed).url)
        assertNotNull(outputN)
        assertNotEquals(inputN, outputN)
        assertTrue(requireNotNull(transformed).nTransformed)

        println(
            "YT_PROOF api26-player-runtime=TRANSFORM_ONLY_UNVERIFIED " +
                "player=" + diagnostics.playerJavaScriptUrl +
                " candidate=" + diagnostics.nParameter.urlBuilderCandidates.first() +
                " n=" + outputN
        )
    }
}
