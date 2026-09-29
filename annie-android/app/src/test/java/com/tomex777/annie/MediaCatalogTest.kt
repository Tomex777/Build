package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCatalogTest {
    @Test fun anilistAnimeAndMangaKeepTheirTypeAndMetadataSource() {
        val result = parseAniListSearchPayload(
            """{"data":{"Page":{"media":[{"id":42,"type":"MANGA","title":{"english":"Sample Manga","romaji":""},"startDate":{"year":2022},"chapters":18,"status":"RELEASING","format":"MANGA","genres":["Action"],"description":"A sample story.","coverImage":{"large":"https://img.test/cover.jpg"}}]}}}""",
            "manga"
        ).single()

        assertEquals("MANGA", result.mediaType)
        assertEquals("Sample Manga", result.title)
        assertEquals(18, result.chapters)
        assertEquals(listOf("Action"), result.genres)
        assertEquals("AniList", result.sourceLabel)
        assertEquals("https://anilist.co/manga/42", result.sourceUrl)
    }

    @Test fun tvmazeSearchCleansSummaryAndRetainsSourceDetails() {
        val result = parseTvMazeSearchPayload(
            """[{"score":1,"show":{"id":100,"name":"Sample Series","premiered":"2020-02-03","status":"Ended","averageRuntime":45,"genres":["Drama"],"summary":"<p>A <b>sample</b> story &amp; more.</p>","image":{"medium":"https://img.test/poster.jpg"},"url":"http://www.tvmaze.com/shows/100/sample-series"}}]"""
        ).single()

        assertEquals("TV", result.mediaType)
        assertEquals("Sample Series", result.title)
        assertEquals(2020, result.year)
        assertEquals(45, result.runtimeMinutes)
        assertEquals("A sample story & more.", result.summary)
        assertEquals("TVmaze", result.sourceLabel)
        assertEquals("https://www.tvmaze.com/shows/100/sample-series", result.sourceUrl)
    }

    @Test fun wikidataMovieFieldsAreParsedWithoutInferringPlayback() {
        val entity = org.json.JSONObject(
            """{"labels":{"en":{"value":"Sample Film"}},"descriptions":{"en":{"value":"A sample film."}},"claims":{"P577":[{"mainsnak":{"datavalue":{"value":{"time":"+2019-05-01T00:00:00Z"}}}}],"P2047":[{"mainsnak":{"datavalue":{"value":{"amount":"+107","unit":"http://www.wikidata.org/entity/Q7727"}}}}],"P18":[{"mainsnak":{"datavalue":{"value":"Sample poster.jpg"}}}],"P57":[{"mainsnak":{"datavalue":{"value":{"id":"Q1"}}}}],"P136":[{"mainsnak":{"datavalue":{"value":{"id":"Q2"}}}}]}}"""
        )
        val result = parseWikidataMovie("Q123", entity, mapOf("Q1" to "Example Director", "Q2" to "Drama"))

        assertEquals("MOVIE", result.mediaType)
        assertEquals("Sample Film", result.title)
        assertEquals(2019, result.year)
        assertEquals(107, result.runtimeMinutes)
        assertEquals("Example Director", result.creator)
        assertEquals(listOf("Drama"), result.genres)
        assertTrue(result.image.contains("Special:FilePath/Sample%20poster.jpg"))
        assertEquals("https://www.wikidata.org/wiki/Q123", result.sourceUrl)
        assertEquals("METADATA", result.status)
    }

    @Test fun titleSelectionRoutesEachMediaTypeToItsOwnDetailFlow() {
        assertEquals("manga", selectedDetailsStage(CatalogItem(1, "MANGA", "M", "", null, "UNKNOWN", null, null)))
        assertEquals("movie", selectedDetailsStage(CatalogItem(2, "MOVIE", "F", "", null, "METADATA", null, null)))
        assertEquals("tv", selectedDetailsStage(CatalogItem(3, "TV", "S", "", null, "Ended", null, null)))
        assertEquals("movie", selectedDetailsStage(CatalogItem(4, "ANIME", "A", "", null, "FINISHED", null, null, format = "MOVIE")))
        assertEquals("series", selectedDetailsStage(CatalogItem(5, "ANIME", "A", "", null, "RELEASING", 12, null, format = "TV")))
        assertEquals("series", selectedDetailsStage(CatalogItem(6, "ANIME", "B", "", null, "UNKNOWN", null, null)))
    }
}
