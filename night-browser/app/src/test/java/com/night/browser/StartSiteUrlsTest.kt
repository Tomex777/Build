package com.night.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartSiteUrlsTest {
    @Test fun acceptsHttpsAndBareDomain() {
        assertEquals("https://example.com", StartSiteUrls.normalize("example.com"))
        assertEquals("https://example.com/watch?x=1",
            StartSiteUrls.normalize("https://example.com/watch?x=1"))
        assertEquals("http://localhost:8080/page",
            StartSiteUrls.normalize("http://localhost:8080/page"))
    }

    @Test fun neverSavesExecutablesOrLocalFiles() {
        assertNull(StartSiteUrls.normalize("javascript:alert(1)"))
        assertNull(StartSiteUrls.normalize("file:///sdcard/private"))
        assertNull(StartSiteUrls.normalize("data:text/html,test"))
        assertNull(StartSiteUrls.normalize("https://user:pass@example.com"))
        assertNull(StartSiteUrls.normalize("ftp://example.com"))
    }

    @Test fun excludesInvalidUrlsAndQueries() {
        assertNull(StartSiteUrls.normalize(""))
        assertNull(StartSiteUrls.normalize("two words"))
        assertNull(StartSiteUrls.normalize("https://"))
        assertNull(StartSiteUrls.normalize("https://example.com/my path"))
        assertNull(StartSiteUrls.normalize("http://example.com:65536"))
    }
}
