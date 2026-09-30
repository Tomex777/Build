package com.veya.app.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {
    @Test fun watchUrl() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun shortUrl() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.videoId("https://youtu.be/dQw4w9WgXcQ?t=20"))
    }

    @Test fun shortsUrl() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.videoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test fun rejectsOtherHosts() {
        assertNull(YouTubeUrlParser.videoId("https://example.com/watch?v=dQw4w9WgXcQ"))
    }
}
