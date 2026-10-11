package com.tomex777.annie

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerBrowserCookieIsolationTest {
    @Test fun unregisteredAndCrossHostBrowserSourcesAreBlocked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionId = "player-proof-" + java.lang.System.nanoTime()
        val url = "https://player-proof.example.com/film.mp4"
        val allowed = PlayerSource("720p", Uri.parse(url), browserSessionId = sessionId)
        assertFalse(playerSourceAllowed(context, allowed))

        val spec = AnnieBrowserSpec(
            sessionId = sessionId,
            url = "https://player-proof.example.com/",
            allowedHosts = listOf("player-proof.example.com"),
            restricted = true,
        )
        try {
            AnnieBrowserSessionStore.register(context, spec)
            val expected = AnnieBrowserProfiles.isSupported()
            assertEquals(expected, playerSourceAllowed(context, allowed))
            assertFalse(playerSourceAllowed(context,
                allowed.copy(uri = Uri.parse("https://other.example.com/film.mp4"))))
            assertFalse(playerSourceAllowed(context,
                allowed.copy(uri = Uri.parse("file:///storage/emulated/0/secrets.mp4"))))
        } finally {
            AnnieBrowserSessionStore.clear(context, sessionId, clearCookies = false)
        }
    }

    @Test fun playerReadsOnlyItsOwnProfileCookies() {
        assumeTrue(AnnieBrowserProfiles.isSupported())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val id1 = "player-cookies-a-" + java.lang.System.nanoTime()
        val id2 = "player-cookies-b-" + java.lang.System.nanoTime()
        val url = "https://cookie-isolation.example.net/film.mp4"
        val sources = listOf(id1, id2).map { id ->
            AnnieBrowserSessionStore.register(context,
                AnnieBrowserSpec(sessionId = id, url = url,
                    allowedHosts = listOf("cookie-isolation.example.net"), restricted = true))
            PlayerSource("Source", Uri.parse(url), browserSessionId = id)
        }
        try {
            instrumentation.runOnMainSync {
                AnnieBrowserProfiles.cookieManagerOnMain(id1).apply {
                    setCookie(url, "annie_player=a; Path=/"); flush()
                }
                AnnieBrowserProfiles.cookieManagerOnMain(id2).apply {
                    setCookie(url, "annie_player=b; Path=/"); flush()
                }
                assertTrue(playerCookiesForSource(context, sources[0]).orEmpty().contains("annie_player=a"))
                assertFalse(playerCookiesForSource(context, sources[0]).orEmpty().contains("annie_player=b"))
                assertTrue(playerCookiesForSource(context, sources[1]).orEmpty().contains("annie_player=b"))
                assertFalse(playerCookiesForSource(context, sources[1]).orEmpty().contains("annie_player=a"))
                val forged = sources[0].copy(
                    headers = mapOf("Cookie" to "stolen=forged", "Referer" to "https://watch.example")
                )
                val sanitized = playerHeadersForSource(context, forged)
                assertFalse("Explicit cookies overrode browser isolation",
                    sanitized["Cookie"].orEmpty().contains("stolen=forged"))
                assertTrue(sanitized["Cookie"].orEmpty().contains("annie_player=a"))
                assertEquals("https://watch.example", sanitized["Referer"])
                assertTrue("Control-character header leaked to VLC",
                    "X-Test" !in playerHeadersForSource(context,
                        forged.copy(headers = forged.headers + ("X-Test" to "bad\r\nheader"))))
            }
        } finally {
            AnnieBrowserSessionStore.clear(context, id1, clearCookies = true)
            AnnieBrowserSessionStore.clear(context, id2, clearCookies = true)
        }
    }
}
