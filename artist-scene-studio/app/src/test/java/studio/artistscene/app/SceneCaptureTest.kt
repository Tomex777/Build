package studio.artistscene.app

import android.view.PixelCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCaptureTest {
    @Test fun recoverablePickerResumeCopyFailuresRetryOnlyWithinBound() {
        for (attempt in 0..2) {
            assertTrue(shouldRetryWindowPixelCopy(PixelCopy.ERROR_SOURCE_NO_DATA, attempt))
            assertTrue(shouldRetryWindowPixelCopy(PixelCopy.ERROR_TIMEOUT, attempt))
        }
        assertFalse(shouldRetryWindowPixelCopy(PixelCopy.ERROR_TIMEOUT, 3))
        assertFalse(shouldRetryWindowPixelCopy(PixelCopy.ERROR_SOURCE_NO_DATA, 3))
    }

    @Test fun permanentPixelCopyErrorsAndSuccessNeverRetry() {
        for (result in listOf(
            PixelCopy.SUCCESS,
            PixelCopy.ERROR_UNKNOWN,
            PixelCopy.ERROR_SOURCE_INVALID,
            PixelCopy.ERROR_DESTINATION_INVALID,
        )) {
            assertFalse(shouldRetryWindowPixelCopy(result, 0))
            assertFalse(shouldRetryWindowPixelCopy(result, 2))
        }
        assertFalse(shouldRetryWindowPixelCopy(PixelCopy.ERROR_TIMEOUT, -1))
    }

    @Test fun pngNameRemovesExistingExtensionAndSanitizesInvalidNames() {
        assertEquals("Character study.png", scenePngFilename("  Character study.PNG  "))
        assertEquals("sunset.png", scenePngFilename("sunset.png"))
        assertEquals("Mise Scene.png", scenePngFilename(".."))
        assertEquals("Mise Scene.png", scenePngFilename("😺"))
        assertEquals("V1 New.png", scenePngFilename("V1 / New"))
        assertEquals("Mise Scene.png", scenePngFilename(" "))
    }

    @Test fun pngFilenameHasBoundedLength() {
        assertEquals(64, scenePngFilename("A".repeat(100)).length)
    }
}
