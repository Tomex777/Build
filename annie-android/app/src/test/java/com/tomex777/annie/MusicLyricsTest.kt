package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLyricsTest {
    @Test fun parsesAndSortsStandardLrcTimestamps() {
        val lines = parseTimedLyrics(
            """
            |[00:10.50]Second line
            |[00:02.125]First line
            |[01:03]Later line
            """.trimMargin()
        )

        assertEquals(
            listOf(
                TimedLyricLine(2_125, "First line"),
                TimedLyricLine(10_500, "Second line"),
                TimedLyricLine(63_000, "Later line"),
            ),
            lines,
        )
    }

    @Test fun supportsMultipleTimestampsForOneLyric() {
        assertEquals(
            listOf(
                TimedLyricLine(1_000, "Echo"),
                TimedLyricLine(2_000, "Echo"),
            ),
            parseTimedLyrics("[00:01][00:02]Echo"),
        )
    }

    @Test fun plainOrInvalidLyricsRemainUnsynced() {
        assertTrue(parseTimedLyrics("A plain lyric line\nAnother line").isEmpty())
        assertTrue(parseTimedLyrics("[00:99.00]Invalid seconds").isEmpty())
        assertTrue(parseTimedLyrics("[ar:Artist]\n[ti:Song]").isEmpty())
    }
}
