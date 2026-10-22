package com.nice.juicewrld

import org.junit.Assert.*
import org.junit.Test

class FanSongsTest {
    @Test fun catalogContainsRealCuratedSongsWithoutDuplicates() {
        assertTrue(FanSongs.all.size >= 50)
        assertEquals(FanSongs.all.size, FanSongs.all.map { it.id }.distinct().size)
        assertTrue(FanSongs.all.none { it.title.isBlank() || it.album.isBlank() })
    }

    @Test fun emotionalGenresAreEditableEditorialGroupsWithOverlap() {
        val lucid = FanSongs.find("lucid-dreams")!!
        assertTrue(Mood.HEARTBREAK in lucid.moods)
        assertTrue(Mood.SAD in lucid.moods)
        assertFalse(Mood.HYPE in lucid.moods)
        assertTrue(Mood.LOVE in FanSongs.find("flaws-and-sins")!!.moods)
    }

    @Test fun filteringDoesNotConfuseSavedWithNavigation() {
        val saved = setOf("lucid-dreams", "robbery")
        val result = FanSongs.filter(Mood.FAVORITES, "", saved)
        assertEquals(2, result.size)
        assertTrue(result.all { it.id in saved })
        assertTrue(FanSongs.filter(Mood.FAVORITES, "", emptySet()).isEmpty())
    }

    @Test fun searchMatchesSongAlbumAndYearWithoutIgnoringMood() {
        assertEquals(
            1, FanSongs.filter(Mood.HEARTBREAK, "lucid DREAMS", emptySet()).size
        )
        assertTrue(FanSongs.filter(Mood.HYPE, "lucid dreams", emptySet()).isEmpty())
        assertTrue(FanSongs.filter(Mood.ALL, "Death Race for Love", emptySet()).size > 5)
        assertTrue(FanSongs.filter(Mood.ALL, "2024", emptySet()).isNotEmpty())
    }

    @Test fun folderMatcherFindsRealFileNamesWithoutGuessingWrongVersion() {
        assertEquals("lucid-dreams",
            SongFileMatcher.matchFilename("01 - Juice WRLD - Lucid Dreams.mp3")?.id)
        assertEquals("flaws-and-sins",
            SongFileMatcher.matchFilename("Flaws & Sins - Juice WRLD.flac")?.id)
        assertNull(SongFileMatcher.matchFilename("Lucid Dreams (slowed).mp3"))
        assertNull(SongFileMatcher.matchFilename("Somebody Else.mp3"))
        assertNull(SongFileMatcher.matchFilename("Lucid Dreams.exe"))
    }

    @Test fun everyMoodHasListeningOptionsAndFavoritesOnlyContainsFavorites() {
        for (mood in listOf(Mood.LOVE, Mood.SAD, Mood.HEARTBREAK, Mood.CHILL, Mood.HYPE)) {
            assertTrue("Missing " + mood.name, FanSongs.count(mood, emptySet()) >= 5)
        }
        assertEquals(0, FanSongs.count(Mood.FAVORITES, emptySet()))
    }
}
