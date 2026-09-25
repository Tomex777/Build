package app.nami.compat.aniyomi

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps extension-owned anime state opaque to Nami's core/database.
 * Only the Aniyomi adapter knows how to encode/decode this payload.
 */
internal object LegacyAnimeStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(anime: SAnime): String = buildJsonObject {
        put("url", JsonPrimitive(anime.url))
        put("title", JsonPrimitive(anime.title))
        put("artist", anime.artist?.let(::JsonPrimitive) ?: JsonNull)
        put("author", anime.author?.let(::JsonPrimitive) ?: JsonNull)
        put("description", anime.description?.let(::JsonPrimitive) ?: JsonNull)
        put("genre", anime.genre?.let(::JsonPrimitive) ?: JsonNull)
        put("status", JsonPrimitive(anime.status))
        put("thumbnailUrl", anime.thumbnail_url?.let(::JsonPrimitive) ?: JsonNull)
        put("backgroundUrl", anime.background_url?.let(::JsonPrimitive) ?: JsonNull)
        put("updateStrategy", JsonPrimitive(anime.update_strategy.name))
        put("fetchType", JsonPrimitive(anime.fetch_type.name))
        put("seasonNumber", JsonPrimitive(anime.season_number))
        put("initialized", JsonPrimitive(anime.initialized))
        put("memo", anime.memo)
    }.toString()

    fun decode(encoded: String?): SAnime? {
        if (encoded.isNullOrBlank()) return null

        return runCatching {
            val root = json.parseToJsonElement(encoded).jsonObject
            SAnime.create().apply {
                url = root.string("url").orEmpty()
                title = root.string("title").orEmpty()
                artist = root.string("artist")
                author = root.string("author")
                description = root.string("description")
                genre = root.string("genre")
                status = root["status"]?.jsonPrimitive?.intOrNull ?: SAnime.UNKNOWN
                thumbnail_url = root.string("thumbnailUrl")
                background_url = root.string("backgroundUrl")
                update_strategy = root.string("updateStrategy")
                    ?.let { runCatching { AnimeUpdateStrategy.valueOf(it) }.getOrNull() }
                    ?: AnimeUpdateStrategy.ALWAYS_UPDATE
                fetch_type = root.string("fetchType")
                    ?.let { runCatching { FetchType.valueOf(it) }.getOrNull() }
                    ?: FetchType.Episodes
                season_number = root["seasonNumber"]?.jsonPrimitive?.doubleOrNull ?: -1.0
                initialized = root["initialized"]?.jsonPrimitive?.booleanOrNull ?: false
                memo = root["memo"] as? JsonObject ?: JsonObject(emptyMap())
            }
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? {
        val value = this[key] ?: return null
        if (value === JsonNull) return null
        return value.jsonPrimitive.contentOrNull
    }
}
