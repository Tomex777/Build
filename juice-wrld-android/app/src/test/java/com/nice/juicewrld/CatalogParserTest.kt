package com.nice.juicewrld

import org.junit.Assert.*
import org.junit.Test

class CatalogParserTest {
    private val sample = """
        {
          "release-group-count": 5,
          "release-groups": [
            {"id":"a", "title":"Goodbye & Good Riddance", "primary-type":"Album", "first-release-date":"2018-05-23"},
            {"id":"b", "title":"The Party Never Ends", "primary-type":"Album", "first-release-date":"2024-11-29"},
            {"id":"c", "title":"Fighting Demons", "primary-type":"Album", "first-release-date":"2021-12-10"},
            {"id":"d", "title":"Untyped bootleg", "primary-type":"Other", "first-release-date":"2025-02-01"}
          ]
        }
    """.trimIndent()

    @Test fun onlySupportedReleaseTypesAreDisplayedAndDatesSortNewestFirst() {
        val parsed = CatalogParser.parseReleaseGroups(sample)
        assertEquals(listOf("b", "c", "a"), parsed.entries.map { it.id })
        assertTrue(parsed.hasMore)
        assertEquals(4, parsed.nextOffset)
    }

    @Test fun paginationUsesRawNumberOfRecordsNotFilteredEntries() {
        val parsed = CatalogParser.parseReleaseGroups(sample, offset = 100)
        assertEquals(104, parsed.nextOffset)
        assertFalse(parsed.hasMore)
    }

    @Test fun albumAndTextFilteringAreExactAndCaseInsensitive() {
        val releases = CatalogParser.parseReleaseGroups(sample).entries
        assertEquals(1, CatalogParser.search(releases, "PARTY", "Album").size)
        assertEquals(1, CatalogParser.search(releases, "2021", "All").size)
        assertTrue(CatalogParser.search(releases, "PARTY", "EP").isEmpty())
    }

    @Test fun mergingPagesDoesNotDuplicateReleases() {
        val releases = CatalogParser.parseReleaseGroups(sample).entries
        val merged = CatalogParser.merge(releases, releases)
        assertEquals(3, merged.size)
    }

    @Test fun trackParsingPreservesTrackNumbersAndDurations() {
        val json = """
            {"media":[{"tracks":[
              {"number":"1","title":"Intro","length":66001},
              {"number":"2","title":"Lucid Dreams","length":239000}
            ]}]}
        """.trimIndent()
        val parsed = CatalogParser.parseTracks(json)
        assertEquals(2, parsed.size)
        assertEquals("1:06", parsed[0].formattedLength())
        assertEquals("3:59", parsed[1].formattedLength())
    }

    @Test fun missingTracklistIsHonestEmptyData() {
        assertEquals(emptyList<Track>(), CatalogParser.parseTracks("{}"))
        assertNull(CatalogParser.parseReleases("""{"releases":[]}"""))
    }

    @Test fun officialReleaseIsChosenWithoutInventingAnEdition() {
        val json = """
            {"releases":[
              {"id":"boot", "status":"Bootleg"},
              {"id":"good", "status":"Official"}
            ]}
        """.trimIndent()
        assertEquals("good", CatalogParser.parseReleases(json))
    }

    @Test fun malformedReleaseWithoutIdentityIsExcluded() {
        val json = """{"release-groups":[{"title":"No ID","primary-type":"Single"}]}"""
        assertTrue(CatalogParser.parseReleaseGroups(json).entries.isEmpty())
    }
}
