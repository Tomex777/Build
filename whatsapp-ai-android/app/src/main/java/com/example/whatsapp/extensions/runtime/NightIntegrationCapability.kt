package com.example.whatsapp.extensions.runtime

import java.util.Locale
import org.json.JSONObject

/**
 * Open-ended integration capability key.
 *
 * Night ships a few convenient well-known keys, but extensions are free to
 * declare any capability id. This lets unrelated future domains participate in
 * the same preferred-provider/fallback system without requiring a Night app
 * update first.
 */
@JvmInline
value class NightIntegrationCapability private constructor(
    val wireName: String,
) {
    val label: String
        get() =
            wireName
                .replace('-', ' ')
                .replace('_', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { token ->
                    token.replaceFirstChar { ch -> ch.uppercase() }
                }

    companion object {
        val Anime = require("anime")
        val Manga = require("manga")
        val Music = require("music")
        val Browser = require("browser")
        val Files = require("files")
        val Productivity = require("productivity")
        val Search = require("search")
        val GeneralTool = require("tool")

        /** Compatibility list of Night's well-known capabilities only. */
        val entries: List<NightIntegrationCapability> =
            listOf(
                Anime,
                Manga,
                Music,
                Browser,
                Files,
                Productivity,
                Search,
                GeneralTool,
            )

        fun fromWireName(
            value: String,
        ): NightIntegrationCapability? =
            normalize(value)
                ?.let(::NightIntegrationCapability)

        fun require(
            value: String,
        ): NightIntegrationCapability =
            requireNotNull(fromWireName(value)) {
                "Invalid Night integration capability: $value"
            }

        /**
         * Reads explicit arbitrary capabilities/tags first, then adds conservative
         * descriptor hints. Name hints use significant adjacent words only, so
         * "Unreal Engine Tools" and "Unreal Engine Assets" both yield
         * "unreal_engine" without hard-coding that domain in Night.
         */
        fun fromDescriptor(
            json: JSONObject,
        ): Set<NightIntegrationCapability> =
            buildSet {
                json.optJSONArray("capabilities")?.let { array ->
                    for (index in 0 until array.length()) {
                        val raw =
                            when (val item = array.opt(index)) {
                                is JSONObject ->
                                    item.optString("id")
                                        .ifBlank { item.optString("name") }
                                else -> item?.toString().orEmpty()
                            }
                        fromWireName(raw)?.let(::add)
                    }
                }

                fromWireName(json.optString("category"))
                    ?.let(::add)

                json.optJSONArray("tags")?.let { array ->
                    for (index in 0 until array.length()) {
                        fromWireName(array.optString(index))
                            ?.let(::add)
                    }
                }

                inferAdjacentNameHints(
                    json.optString("name")
                        .ifBlank { json.optString("extensionName") }
                ).forEach(::add)
            }

        private fun inferAdjacentNameHints(
            value: String,
        ): Set<NightIntegrationCapability> {
            val stopWords =
                setOf(
                    "night",
                    "extension",
                    "extensions",
                    "plugin",
                    "plugins",
                    "provider",
                    "providers",
                    "tool",
                    "tools",
                    "client",
                    "service",
                    "app",
                )

            val tokens =
                value
                    .lowercase(Locale.US)
                    .split(Regex("[^a-z0-9]+"))
                    .map { it.trim() }
                    .filter {
                        it.length >= 3 &&
                            it !in stopWords
                    }

            if (tokens.size < 2) return emptySet()

            return tokens
                .windowed(size = 2, step = 1)
                .mapNotNull { pair ->
                    fromWireName(pair.joinToString("_"))
                }
                .toSet()
        }

        private fun normalize(
            value: String,
        ): String? {
            val normalized =
                value
                    .trim()
                    .lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9._-]+"), "_")
                    .replace(Regex("_+"), "_")
                    .trim('_', '-', '.')
                    .take(80)

            if (normalized.length < 2) return null
            return normalized
        }
    }
}
