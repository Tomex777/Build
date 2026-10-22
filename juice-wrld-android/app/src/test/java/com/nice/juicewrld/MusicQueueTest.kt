package com.nice.juicewrld

import org.junit.Assert.*
import org.junit.Test

class MusicQueueTest {
    @Test fun normalQueuePreservesOrderAndDeduplicates() {
        assertEquals(listOf("a", "b"), MusicQueue.build(listOf("a", "a", "b"), "b", false))
    }
    @Test fun shuffleKeepsCurrentSongAtFront() {
        val shuffled = MusicQueue.build(listOf("a", "b", "c"), "b", true)
        assertEquals("b", shuffled.first())
        assertEquals(setOf("a", "b", "c"), shuffled.toSet())
        assertEquals(3, shuffled.size)
    }
    @Test fun unavailableSongCannotBeQueued() {
        assertTrue(MusicQueue.build(listOf("a", "b"), "c", true).isEmpty())
    }
    @Test fun autoAdvanceStopsAtEndByDefault() {
        assertNull(MusicQueue.step(listOf("a","b"), "b", 1, RepeatMode.OFF, true))
        assertEquals("b", MusicQueue.step(listOf("a","b"), "a", 1, RepeatMode.OFF, true))
    }
    @Test fun repeatAllWrapsAndRepeatOneRepeats() {
        assertEquals("a", MusicQueue.step(listOf("a","b"), "b", 1, RepeatMode.ALL, true))
        assertEquals("b", MusicQueue.step(listOf("a","b"), "b", 1, RepeatMode.ONE, true))
    }
    @Test fun previousDoesNotWrapUnlessRepeatAll() {
        assertEquals("a", MusicQueue.step(listOf("a","b"), "a", -1, RepeatMode.OFF))
        assertEquals("b", MusicQueue.step(listOf("a","b"), "a", -1, RepeatMode.ALL))
    }
    @Test fun repeatCyclesAcrossAllThreeModes() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }
}
