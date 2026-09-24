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
}
