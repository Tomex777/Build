package com.nice.juicewrld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongSearchTest {
    @Test fun apostrophesAndAmpersandsDoNotHideSongs() {
        assertEquals("im-still", FanSongs.filter(Mood.ALL, "im still", emptySet()).single().id)
        assertEquals("come-and-go", FanSongs.filter(Mood.ALL, "come and go", emptySet()).single().id)
        assertEquals("flaws-and-sins", FanSongs.filter(Mood.ALL, "flaws & sins", emptySet()).single().id)
    }

    @Test fun searchMatchesPartialWordsAndAlbumYears() {
        assertEquals("lucid-dreams", FanSongs.filter(Mood.ALL, "luc dre", emptySet()).single().id)
        val fromParty = FanSongs.filter(Mood.ALL, "party 2024", emptySet())
        assertTrue(fromParty.isNotEmpty())
        assertTrue(fromParty.all { it.album == "The Party Never Ends" && it.year == "2024" })
    }

    @Test fun queriesStillRespectTheSelectedMoodAndFavorites() {
        assertEquals("lucid-dreams", FanSongs.filter(Mood.SAD, "DREAMS", emptySet()).single().id)
        assertTrue(FanSongs.filter(Mood.HYPE, "lucid", emptySet()).isEmpty())
        assertTrue(FanSongs.filter(Mood.FAVORITES, "lucid", emptySet()).isEmpty())
        assertEquals("lucid-dreams", FanSongs.filter(
            Mood.FAVORITES, "lucid", setOf("lucid-dreams", "fast")
        ).single().id)
        assertTrue(FanSongs.filter(Mood.ALL, "!!!", emptySet()).isEmpty())
    }
}
