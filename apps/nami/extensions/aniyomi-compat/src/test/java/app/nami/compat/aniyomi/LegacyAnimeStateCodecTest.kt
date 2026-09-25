package app.nami.compat.aniyomi

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LegacyAnimeStateCodecTest {

    @Test
    fun roundTripPreservesV17MemoAndLegacyAnimeFields() {
        val original = SAnime.create().apply {
            url = "/anime/bleach"
            title = "Bleach"
            artist = "Studio Pierrot"
            author = "Tite Kubo"
            description = "Soul Reapers"
            genre = "Action, Supernatural"
            status = SAnime.COMPLETED
            thumbnail_url = "https://example.invalid/cover.jpg"
            background_url = "https://example.invalid/banner.jpg"
            update_strategy = AnimeUpdateStrategy.ONLY_FETCH_ONCE
            fetch_type = FetchType.Seasons
            season_number = 3.0
            initialized = true
            memo = JsonObject(
                mapOf(
                    "token" to JsonPrimitive("opaque-token"),
                    "cursor" to JsonPrimitive(42),
                ),
            )
        }

        val encoded = LegacyAnimeStateCodec.encode(original)
        val restored = assertNotNull(LegacyAnimeStateCodec.decode(encoded))

        assertEquals(original.url, restored.url)
        assertEquals(original.title, restored.title)
        assertEquals(original.artist, restored.artist)
        assertEquals(original.author, restored.author)
        assertEquals(original.description, restored.description)
        assertEquals(original.genre, restored.genre)
        assertEquals(original.status, restored.status)
        assertEquals(original.thumbnail_url, restored.thumbnail_url)
        assertEquals(original.background_url, restored.background_url)
        assertEquals(original.update_strategy, restored.update_strategy)
        assertEquals(original.fetch_type, restored.fetch_type)
        assertEquals(original.season_number, restored.season_number)
        assertEquals(original.initialized, restored.initialized)
        assertEquals(original.memo, restored.memo)
    }

    @Test
    fun malformedStateFailsClosedWithoutCrashingAdapter() {
        assertEquals(null, LegacyAnimeStateCodec.decode("{not-json"))
        assertEquals(null, LegacyAnimeStateCodec.decode(null))
        assertEquals(null, LegacyAnimeStateCodec.decode(""))
    }
}
