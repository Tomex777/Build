package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportAspectTest {
    @Test fun dimensionsWinOverStalePerspectiveCameraAspect() {
        assertEquals(1080.0 / 2400.0, viewportAspectOrFallback(1080, 2400, 16.0 / 9.0), 0.000001)
        assertEquals(2400.0 / 1080.0, viewportAspectOrFallback(2400, 1080, 9.0 / 16.0), 0.000001)
    }

    @Test fun viewportAspectHandlesActualOrthographicSceneSizes() {
        assertEquals(1.0, viewportAspectOrFallback(1024, 1024, 0.42), 0.000001)
        assertEquals(800.0 / 1280.0, viewportAspectOrFallback(800, 1280, 8.0), 0.000001)
    }

    @Test fun zeroSizedSurfaceFallsBackToLastValidProjection() {
        assertEquals(1.5, viewportAspectOrFallback(0, 1920, 1.5), 0.000001)
        assertEquals(1.0, viewportAspectOrFallback(720, 0, Double.NaN), 0.000001)
        assertEquals(1.0, viewportAspectOrFallback(0, 0, Double.POSITIVE_INFINITY), 0.000001)
        assertEquals(1.0, viewportAspectOrFallback(-1, 0, -5.0), 0.000001)
    }
}
