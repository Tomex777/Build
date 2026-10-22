package com.nice.juicewrld

import org.junit.Assert.*
import org.junit.Test

class FanSongsTest {
    @Test fun catalogContainsRealCuratedSongsWithoutDuplicates() {
        assertTrue(FanSongs.all.size >= 110)
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
        assertNull(SongFileMatcher.matchFilename("Fast.mp3"))
        assertEquals("fast", SongFileMatcher.matchFilename("Juice WRLD - Fast.mp3")?.id)
        assertEquals("im-still", SongFileMatcher.matchFilename("Juice WRLD - Im Still.mp3")?.id)
        assertEquals("go-hard-20", SongFileMatcher.matchFilename("Juice WRLD - Go Hard 2.0.mp3")?.id)
        // Safe bulk import should accept a precise title without an artist tag,
        // but never attach some other artist's song containing those words.
        assertEquals("lucid-dreams", SongFileMatcher.matchFilename("Lucid Dreams.mp3")?.id)
        assertEquals("lucid-dreams", SongFileMatcher.matchFilename("01 - Lucid Dreams.mp3")?.id)
        assertNull(SongFileMatcher.matchFilename("Dreamy Lucid Dreams Collection.mp3"))
        assertNull(SongFileMatcher.matchFilename("Super Fast Highway.mp3"))
        assertNull(SongFileMatcher.matchFilename("01 - Fast.mp3"))
    }

    @Test fun newProjectsAppearAcrossYearsWithoutLeakingDuplicates() {
        assertEquals(110, FanSongs.all.size)
        assertTrue(FanSongs.all.any { it.album == "WRLD ON DRUGS" && it.title == "Jet Lag" })
        assertTrue(FanSongs.all.any { it.album == "The Party Never Ends" && it.title == "KTM Drip" })
        assertTrue(FanSongs.all.any { it.album == "Fighting Demons" && it.title == "Feel Alone" })
    }

    @Test fun everyMoodHasListeningOptionsAndFavoritesOnlyContainsFavorites() {
        for (mood in listOf(Mood.LOVE, Mood.SAD, Mood.HEARTBREAK, Mood.CHILL, Mood.HYPE)) {
            assertTrue("Missing " + mood.name, FanSongs.count(mood, emptySet()) >= 5)
        }
        assertEquals(0, FanSongs.count(Mood.FAVORITES, emptySet()))
    }
}
