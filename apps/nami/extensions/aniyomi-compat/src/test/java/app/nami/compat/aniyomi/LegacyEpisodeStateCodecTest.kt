package app.nami.compat.aniyomi

import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LegacyEpisodeStateCodecTest {

    @Test
    fun roundTripPreservesEpisodeMemoAndMetadata() {
        val original = SEpisode.create().apply {
            url = "/episode/7"
            name = "Episode 7"
            date_upload = 1_700_000_000_000L
            episode_number = 7f
            fillermark = true
            scanlator = "Fixture"
            summary = "Episode summary"
            preview_url = "https://example.invalid/preview.jpg"
            memo = JsonObject(mapOf("token" to JsonPrimitive("episode-state")))
        }

        val restored = assertNotNull(
            LegacyEpisodeStateCodec.decode(
                LegacyEpisodeStateCodec.encode(original),
            ),
        )

        assertEquals(original.url, restored.url)
        assertEquals(original.name, restored.name)
        assertEquals(original.date_upload, restored.date_upload)
        assertEquals(original.episode_number, restored.episode_number)
        assertEquals(original.fillermark, restored.fillermark)
        assertEquals(original.scanlator, restored.scanlator)
        assertEquals(original.summary, restored.summary)
        assertEquals(original.preview_url, restored.preview_url)
        assertEquals(original.memo, restored.memo)
    }

    @Test
    fun malformedEpisodeStateFailsClosed() {
        assertEquals(null, LegacyEpisodeStateCodec.decode("{broken"))
        assertEquals(null, LegacyEpisodeStateCodec.decode(null))
    }
}
