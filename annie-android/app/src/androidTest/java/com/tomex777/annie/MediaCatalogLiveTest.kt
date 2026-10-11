package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaCatalogLiveTest {
    @Test fun publicMetadataApisReturnSeparateAnimeMangaMovieAndTvResults() = runBlocking {
        val anime = searchCatalog("anime", "Frieren")
        val manga = searchCatalog("manga", "Blue Lock")
        val movies = searchCatalog("movie", "Inception")
        val tv = searchCatalog("tv", "The Office")

        assertTrue("AniList should return anime metadata", anime.any { it.mediaType == "ANIME" && it.title.contains("Frieren", ignoreCase = true) })
        assertTrue("AniList should return manga metadata", manga.any { it.mediaType == "MANGA" && it.title.contains("Blue Lock", ignoreCase = true) })
        assertTrue("Wikidata should return movie metadata, not arbitrary entities", movies.any { it.mediaType == "MOVIE" && it.title.equals("Inception", ignoreCase = true) })
        assertTrue("TVmaze should return TV series metadata", tv.any { it.mediaType == "TV" && it.title.contains("Office", ignoreCase = true) })
        assertTrue("Metadata results must not imply playable sources", movies.all { it.sourceLabel == "Wikidata" && it.status == "METADATA" })
    }
}
