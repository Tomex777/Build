package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnieGeckoBrowserAndVoiceTest {
    @Test fun omniboxSearchAndAddress() {
        assertEquals("https://example.com/", AnnieBrowserAddress.resolve("https://example.com/"))
        assertEquals("https://example.com", AnnieBrowserAddress.resolve("example.com"))
        assertEquals("https://www.google.com/search?q=hello+world", AnnieBrowserAddress.resolve("hello world"))
        assertNull(AnnieBrowserAddress.resolve("javascript:alert(1)"))
        assertNull(AnnieBrowserAddress.resolve("file:///data/user/0/secret"))
        assertNull(AnnieBrowserAddress.resolve("https://user:pass@example.com/"))
        assertEquals("http://localhost:8080", AnnieBrowserAddress.resolve("localhost:8080"))
        assertEquals("http://127.0.0.1:9000/test",
            AnnieBrowserAddress.resolve("127.0.0.1:9000/test"))
        assertEquals("https://example.org:443/login",
            AnnieBrowserAddress.resolve("example.org:443/login"))
        assertEquals("https://EXAMPLE.COM/path",
            AnnieBrowserAddress.resolve("HTTPS://EXAMPLE.COM/path"))
        assertNull(AnnieBrowserAddress.resolve("about:blank"))
        assertNull(AnnieBrowserAddress.resolve("intent://open/#Intent"))
        assertNull(AnnieBrowserAddress.resolve(""))
    }

    @Test fun waveformAlwaysHasVisibleBoundedSamples() {
        val result = AnnieVoiceWaveform.reduce(listOf(0.1f, 0.8f, 0.0f))
        assertEquals(48, result.size)
        assertTrue(result.all { it in 0.06f..1f })
        assertTrue(result.any { it >= 0.8f })
        assertEquals(48, AnnieVoiceWaveform.reduce(emptyList()).size)
    }
}
