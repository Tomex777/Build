package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NullMetadataTest {
    @Test fun anilistNullTitlesFallBackToValidTitleAndInvalidRowsAreFiltered() {
        val payload = """{"data":{"Page":{"media":[
          {"id":1,"type":"MANGA","title":{"english":null,"romaji":"null","native":"Moonlit Archive"},"startDate":{"year":2024},"chapters":12,"status":"FINISHED","format":"MANGA","description":null,"genres":["Fantasy",null],"coverImage":{"large":null}},
          {"id":2,"type":"MANGA","title":{"english":null,"romaji":"null","native":null},"coverImage":{"large":null}}
        ]}}}"""
        val results = parseAniListSearchPayload(payload, "manga")
        assertEquals(1, results.size)
        assertEquals("Moonlit Archive", results.single().title)
        assertFalse(results.any { it.title.equals("null", ignoreCase = true) })
        assertFalse(results.single().genres.any { it.equals("null", ignoreCase = true) })
    }

    @Test fun tvMazeNullNamesAreDroppedInsteadOfRenderedAsNull() {
        val payload = """[
          {"show":{"id":1,"name":null,"status":"Running"}},
          {"show":{"id":2,"name":"Valid Series","status":"Running"}}
        ]"""
        val results = parseTvMazeSearchPayload(payload)
        assertEquals(1, results.size)
        assertEquals("Valid Series", results.single().title)
    }

    @Test fun relatedNarutoTitlesAreNotGuessedToBeSeasons() {
        val payload = """{"data":{"Page":{"media":[
          {"id":20,"type":"ANIME","title":{"english":"Naruto","romaji":"Naruto"},"format":"TV","episodes":220,"relations":{"edges":[
            {"relationType":"SEQUEL","node":{"id":21,"type":"ANIME","format":"TV","title":{"english":"Naruto: Shippuden"},"episodes":500}}
          ]},"coverImage":{"large":"https://example.test/naruto.jpg"}}
        ]}}}"""
        val naruto = parseAniListSearchPayload(payload, "anime").single()
        assertEquals("Naruto", naruto.title)
        assertEquals(emptyList<SeasonItem>(), naruto.seasons)
        assertEquals("series", selectedDetailsStage(naruto))
    }

    @Test fun nullableOptionalMetadataDoesNotBecomeTheStringNull() {
        val payload = """{"data":{"Page":{"media":[
          {"id":30,"type":"MANGA","title":{"english":"Archive","romaji":"Archive"},"format":"MANGA","status":null,"description":null,"coverImage":{"large":null},"genres":[null,"null"]}
        ]}}}"""
        val item = parseAniListSearchPayload(payload, "manga").single()
        assertEquals("", item.image)
        assertEquals("UNKNOWN", item.status)
        assertEquals("", item.summary)
        assertEquals(emptyList<String>(), item.genres)
    }
}
