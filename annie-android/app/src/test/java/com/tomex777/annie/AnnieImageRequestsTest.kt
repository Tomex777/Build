package com.tomex777.annie

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnieImageRequestsTest {
    @Test fun inlinePngIsDecodedIntoRealImageBytes() {
        assertArrayEquals(byteArrayOf(1, 2, 3), annieImageRequestData("data:image/png;base64,AQID") as ByteArray)
    }
    @Test fun unsupportedOrMalformedInlineDataIsRejected() {
        assertNull(annieImageRequestData("data:text/html;base64,AQID"))
        assertNull(annieImageRequestData("data:image/svg+xml;base64,AQID"))
        assertNull(annieImageRequestData("data:image/png;base64,not#base64"))
        assertNull(annieImageRequestData("data:image/png;base64,"))
        assertNull(annieImageRequestData("data:image/png;base64"))
    }
    @Test fun localAndRemoteImageUrisRetainTheirOriginalIdentity() {
        assertEquals("content://photos/1", annieImageRequestData("content://photos/1"))
        assertEquals("https://example.com/photo.png", annieImageRequestData("https://example.com/photo.png"))
        assertNull(annieImageRequestData(null))
    }
}
