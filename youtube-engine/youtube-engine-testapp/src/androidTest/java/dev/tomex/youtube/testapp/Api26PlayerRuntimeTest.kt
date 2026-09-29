package dev.tomex.youtube.testapp

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.core.CachedPlayerScriptSource
import dev.tomex.youtube.core.PlayerScriptUrlTransformer
import dev.tomex.youtube.core.PlayerUrlTransforms
import dev.tomex.youtube.core.QuickJsPlayerScriptRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API 26 safety gate for the native QuickJS path.
 *
 * Repeated live-player evaluations on API 26 were terminated by the OS/runtime without a Java
 * exception or tombstone. Production must therefore fail closed before native evaluation rather
 * than risking a process death. API 36 separately proves the current live player transform and
 * real CDN bytes; API 26 separately proves 4K/audio transport through supported client strategies.
 */
@RunWith(AndroidJUnit4::class)
class Api26PlayerRuntimeTest {
    @Test
    fun playerRuntimeFailsClosedBeforeNativeEvaluation() = runBlocking {
        assumeTrue("API 26-specific player-runtime safety gate", Build.VERSION.SDK_INT == 26)

        val fixture = """
            var g={};
            g.g7=function(m){this.value=m};
            g.g7.prototype.set=function(k,v){
                var separator=this.value.indexOf("?")>=0?"&":"?";
                this.value+=separator+encodeURIComponent(k)+"="+encodeURIComponent(v)
            };
            g.g7.prototype.toString=function(){return this.value};
            y2=function(m,Z="",J=""){
                m=new g.g7(m,!0);
                m.set("alr","yes");
                m.value=m.value.replace(/([?&])n=([^&#]*)/,function(all,prefix,n){
                    return prefix+"n="+n.split("").reverse().join("")
                });
                return m
            };
        """.trimIndent()
        val diagnostics = mutableListOf<String>()
        val source = CachedPlayerScriptSource(object : dev.tomex.youtube.core.PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String = fixture
        })
        val input = "https://media.example.invalid/videoplayback?itag=313&n=abcdef"
        val transformed = PlayerScriptUrlTransformer(
            source = source,
            runtime = QuickJsPlayerScriptRuntime(
                diagnosticSink = { diagnostic -> diagnostics += diagnostic }
            )
        ).transform(
            playerJavaScriptUrl = "https://www.youtube.com/s/player/api26-safety/base.js",
            mediaUrl = input
        )

        assertNull("API 26 must not enter the unsafe native player runtime", transformed)
        assertTrue(
            diagnostics.any { it == "runtime-unavailable-api=26 fail-closed" }
        )
        assertTrue(PlayerUrlTransforms.extractN(input) == "abcdef")
        println(
            "YT_PROOF api26-player-runtime=FAIL_CLOSED_NO_TRANSPORT_CLAIM " +
                "nativeEvaluation=false alternateTransportProvedSeparately=true diagnostics=" + diagnostics
        )
    }
}
