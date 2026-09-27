package app.nami.compat.aniyomi

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamiAnimeSourceFilterTest {

    @Test
    fun movieAndTvExtensionsAreExcludedFromNami() {
        assertFalse(
            isNamiAnimeSourceCandidate(
                packageName = "eu.kanade.tachiyomi.animeextension.en.cineby",
                extensionName = "Cineby",
                sourceName = "Cineby",
            ),
        )
        assertFalse(
            isNamiAnimeSourceCandidate(
                packageName = "eu.kanade.tachiyomi.animeextension.en.uniquestream",
                extensionName = "UniqueStream",
                sourceName = "UniqueStream",
            ),
        )
        assertFalse(
            isNamiAnimeSourceCandidate(
                packageName = "eu.kanade.tachiyomi.animeextension.all.streamingcommunity",
                extensionName = "StreamingCommunity",
                sourceName = "StreamingUnity (Movie)",
            ),
        )
        assertFalse(
            isNamiAnimeSourceCandidate(
                packageName = "eu.kanade.tachiyomi.animeextension.all.streamingcommunity",
                extensionName = "StreamingCommunity",
                sourceName = "StreamingUnity (TV)",
            ),
        )
    }

    @Test
    fun animeExtensionsRemainEligible() {
        assertTrue(
            isNamiAnimeSourceCandidate(
                packageName = "eu.kanade.tachiyomi.animeextension.en.animesogo",
                extensionName = "AnimeSogo",
                sourceName = "AnimeSogo",
            ),
        )
        assertTrue(
            isNamiAnimeSourceCandidate(
                packageName = "eu.kanade.tachiyomi.animeextension.en.animepahe",
                extensionName = "AnimePahe",
                sourceName = "AnimePahe",
            ),
        )
    }
}
