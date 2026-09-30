package app.nami.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamiPlaybackProgressPolicyTest {

    @Test
    fun shortEpisodeIsNotCompletedJustBecauseLessThanNinetySecondsRemain() {
        assertFalse(
            isCompleted(
                positionMs = 12_000L,
                durationMs = 30_000L,
            ),
        )
    }

    @Test
    fun completionUsesRatioForShortEpisodes() {
        assertTrue(
            isCompleted(
                positionMs = 28_000L,
                durationMs = 30_000L,
            ),
        )
    }

    @Test
    fun longEpisodeNearEndIsCompleted() {
        assertTrue(
            isCompleted(
                positionMs = 23 * 60_000L,
                durationMs = 24 * 60_000L,
            ),
        )
    }

    @Test
    fun persistenceRejectsUnknownOrTrivialPlaybackSnapshots() {
        assertFalse(shouldPersistWatchProgress(positionMs = 0L, durationMs = 0L))
        assertFalse(shouldPersistWatchProgress(positionMs = 2_000L, durationMs = 24 * 60_000L))
        assertTrue(shouldPersistWatchProgress(positionMs = 5_000L, durationMs = 24 * 60_000L))
        assertTrue(shouldPersistWatchProgress(positionMs = 3_000L, durationMs = 3_000L))
    }

    @Test
    fun incognitoRejectsOtherwisePersistablePlaybackSnapshots() {
        assertFalse(
            shouldPersistWatchProgress(
                positionMs = 12_000L,
                durationMs = 30_000L,
                persistWatchActivity = false,
            ),
        )
        assertTrue(
            shouldPersistWatchProgress(
                positionMs = 12_000L,
                durationMs = 30_000L,
                persistWatchActivity = true,
            ),
        )
    }

    @Test
    fun resumeRejectsTinyCompletedAndInvalidPositions() {
        assertNull(resumablePositionOrNull(2_000L, 24 * 60_000L, completed = false))
        assertNull(resumablePositionOrNull(12_000L, 0L, completed = false))
        assertNull(resumablePositionOrNull(23 * 60_000L, 24 * 60_000L, completed = true))
        assertNull(resumablePositionOrNull(24 * 60_000L, 24 * 60_000L, completed = false))
    }

    @Test
    fun resumeKeepsMeaningfulMidEpisodeProgress() {
        assertEquals(
            12_000L,
            resumablePositionOrNull(
                positionMs = 12_000L,
                durationMs = 30_000L,
                completed = false,
            ),
        )
    }
}
