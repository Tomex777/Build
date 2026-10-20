package com.night.browser.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchPolicyTest {
    private val publicBookmark = SearchSource(
        id = "S1", title = "Kotlin coroutines documentation",
        url = "https://kotlinlang.org/docs/coroutines-overview.html",
        category = "Bookmark",
    )
    private val secondBookmark = SearchSource(
        id = "S2", title = "Android Developers",
        url = "https://developer.android.com/develop",
        category = "Bookmark",
    )

    @Test fun privateModeCannotPrepareRequestEvenWithSelectionAndConsent() {
        val result = UniversalSearchPolicy.prepare(
            "Find my Kotlin page", listOf(publicBookmark), setOf("S1"), true, true
        )
        assertTrue(result is SearchPreparation.Rejected)
    }

    @Test fun optOutNeverPreparesRequest() {
        val result = UniversalSearchPolicy.prepare(
            "Find my Kotlin page", listOf(publicBookmark), setOf("S1"), false, false
        )
        assertTrue(result is SearchPreparation.Rejected)
    }

    @Test fun individualPagePermissionsFilterOtherPages() {
        val result = UniversalSearchPolicy.prepare(
            "Find my Kotlin page",
            listOf(publicBookmark, secondBookmark),
            setOf("S1"),
            true, false,
        )
        assertTrue(result is SearchPreparation.Ready)
        val ready = result as SearchPreparation.Ready
        assertEquals(1, ready.sources.size)
        assertEquals("kotlinlang.org", ready.sources.first().host)
        assertEquals("S1", ready.sources.first().id)
        assertFalse(ready.sources.any { it.id == "S2" })
    }

    @Test fun sensitiveEntriesNeverReachTheNetworkPayload() {
        val raw = listOf(
            publicBookmark,
            SearchSource("S3", "Reset password", "https://example.com/reset-password", "Visit"),
            SearchSource("S4", "My account", "https://example.com/profile?token=leak", "Visit"),
            SearchSource("S5", "Patient report", "https://hospital.example.com/report", "Visit"),
            SearchSource("S6", "Local site", "https://localhost/home", "Visit"),
            SearchSource("S7", "Signed URL", "https://cdn.example.com/path#secret", "Visit"),
            SearchSource("S8", "Internal", "http://internal.example.com/", "Visit"),
        )
        val allowed = UniversalSearchPolicy.eligibleCandidates(raw)
        assertEquals(listOf("S1"), allowed.map { it.id })
    }

    @Test fun duplicatePagesAreNotRepeated() {
        val sameUrl = publicBookmark.copy(id = "S19", category = "Visit")
        assertEquals(1, UniversalSearchPolicy.eligibleCandidates(listOf(publicBookmark, sameUrl)).size)
    }

    @Test fun forgedModelCitationsAreDiscarded() {
        val ready = UniversalSearchPolicy.prepare(
            "My saved pages",
            listOf(publicBookmark, secondBookmark), setOf("S1"), true, false
        ) as SearchPreparation.Ready
        val confirmed = UniversalSearchPolicy.confirmedCitations(
            listOf("S2", "BAD", "S1", "S1"), ready.sources
        )
        assertEquals(listOf(publicBookmark), confirmed)
    }


    @Test fun urlDerivedIdsNeverLeaveTheDeviceAsSourceIdentifiers() {
        val encodedUrl = "https://example.com/some-specific-page"
        val localId = SearchSourceCatalog.stableId(encodedUrl)
        val page = SearchSource(localId, "Interesting page", encodedUrl, "Visit")
        val request = UniversalSearchPolicy.prepare(
            "Find it", listOf(page), setOf(localId), true, false
        ) as SearchPreparation.Ready
        assertEquals("S1", request.sources.single().id)
        assertFalse(request.sources.single().id == localId)
        assertEquals(page, UniversalSearchPolicy.confirmedCitations(listOf("S1"), request.sources).single())
    }

    @Test fun encodedSensitivePathMustBeSuppressed() {
        val page = SearchSource("sensitive", "Welcome", "https://example.com/%6cogin", "Visit")
        assertTrue(UniversalSearchPolicy.eligibleCandidates(listOf(page)).isEmpty())
    }

    @Test fun missingSelectionAndBlankQuestionAreRejected() {
        assertTrue(
            UniversalSearchPolicy.prepare("", listOf(publicBookmark), setOf("S1"), true, false)
                is SearchPreparation.Rejected
        )
        assertTrue(
            UniversalSearchPolicy.prepare("Find", listOf(publicBookmark), emptySet(), true, false)
                is SearchPreparation.Rejected
        )
    }

    @Test fun sourceLimitIsEnforcedBeforeTransport() {
        val many = (1..50).map {
            SearchSource("S" + it, "Page number " + it, "https://site" + it + ".example.com/", "Visit")
        }
        val ready = UniversalSearchPolicy.prepare(
            "Find a page", many, many.map { it.id }.toSet(), true, false
        ) as SearchPreparation.Ready
        assertEquals(UniversalSearchPolicy.MAX_SHARED_SOURCES, ready.sources.size)
    }
}
