package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnieImagePanBoundsTest {
    @Test fun widePhotoRemainsVerticallyCenteredWhenZoomedOnPortraitPhone() {
        // A 16:9 photo fits at 1080x607.5. At 2x it is still shorter than the screen.
        val (x, y) = annieImagePanBounds(1920, 1080, 1080, 1920, 2f)
        assertEquals(540f, x, .01f)
        assertEquals(0f, y, .01f)
    }

    @Test fun tallPhotoRemainsHorizontallyCenteredOnLandscapePhone() {
        val (x, y) = annieImagePanBounds(1080, 1920, 1920, 1080, 2f)
        assertEquals(0f, x, .01f)
        assertEquals(540f, y, .01f)
    }

    @Test fun squarePhotoCanPanOnlyToItsActualEdgesAtFiveTimesZoom() {
        val (x, y) = annieImagePanBounds(1000, 1000, 1080, 1920, 5f)
        assertEquals(2160f, x, .01f)
        assertEquals(1740f, y, .01f)
        assertEquals(0f to 0f, annieImagePanBounds(1000, 1000, 1080, 1920, 1f))
    }

    @Test fun missingImageDimensionsDoNotAllowDraggingAnEmptySurface() {
        assertEquals(0f to 0f, annieImagePanBounds(0, 0, 1080, 1920, 2f))
        assertEquals(0f to 0f, annieImagePanBounds(1000, 1000, 1080, 1920, Float.NaN))
    }
}
