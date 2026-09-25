package app.nami.compat.aniyomi

import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object LegacyEpisodeStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(episode: SEpisode): String = buildJsonObject {
        put("url", JsonPrimitive(episode.url))
        put("name", JsonPrimitive(episode.name))
        put("dateUpload", JsonPrimitive(episode.date_upload))
        put("episodeNumber", JsonPrimitive(episode.episode_number))
        put("filler", JsonPrimitive(episode.fillermark))
        put("scanlator", episode.scanlator?.let(::JsonPrimitive) ?: JsonNull)
        put("summary", episode.summary?.let(::JsonPrimitive) ?: JsonNull)
        put("previewUrl", episode.preview_url?.let(::JsonPrimitive) ?: JsonNull)
        put("memo", episode.memo)
    }.toString()

    fun decode(encoded: String?): SEpisode? {
        if (encoded.isNullOrBlank()) return null

        return runCatching {
            val root = json.parseToJsonElement(encoded).jsonObject
            SEpisode.create().apply {
                url = root.string("url").orEmpty()
                name = root.string("name").orEmpty()
                date_upload = root["dateUpload"]?.jsonPrimitive?.longOrNull ?: 0L
                episode_number = root["episodeNumber"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: -1f
                fillermark = root["filler"]?.jsonPrimitive?.booleanOrNull ?: false
                scanlator = root.string("scanlator")
                summary = root.string("summary")
                preview_url = root.string("previewUrl")
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
