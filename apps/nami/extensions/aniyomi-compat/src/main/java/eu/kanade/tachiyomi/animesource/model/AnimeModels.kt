/*
 * Compatibility API adapted from the pinned Aniyomi source API / extensions-lib.
 * Licensed under Apache-2.0; see THIRD_PARTY_NOTICES.md.
 */
@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.animesource.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SAnime : Serializable {
    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?
    var status: Int
    var thumbnail_url: String?
    var background_url: String?
    var update_strategy: AnimeUpdateStrategy
    var fetch_type: FetchType
    var season_number: Double
    var initialized: Boolean
    var memo: JsonObject

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    fun copy(): SAnime = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnail_url
        it.background_url = background_url
        it.update_strategy = update_strategy
        it.fetch_type = fetch_type
        it.season_number = season_number
        it.initialized = initialized
        it.memo = memo
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
        const val UPCOMING = 7

        @JvmStatic
        fun create(): SAnime = SAnimeImpl()
    }
}

class SAnimeImpl : SAnime {
    override var url: String = ""
    override var title: String = ""
    override var artist: String? = null
    override var author: String? = null
    override var description: String? = null
    override var genre: String? = null
    override var status: Int = SAnime.UNKNOWN
    override var thumbnail_url: String? = null
    override var background_url: String? = null
    override var update_strategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE
    override var fetch_type: FetchType = FetchType.Episodes
    override var season_number: Double = -1.0
    override var initialized: Boolean = false
    override var memo: JsonObject = JsonObject(emptyMap())
}

interface SEpisode : Serializable {
    var url: String
    var name: String
    var date_upload: Long
    var episode_number: Float
    var fillermark: Boolean
    var scanlator: String?
    var summary: String?
    var preview_url: String?
    var memo: JsonObject

    fun copyFrom(other: SEpisode) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        episode_number = other.episode_number
        fillermark = other.fillermark
        scanlator = other.scanlator
        summary = other.summary
        preview_url = other.preview_url
        memo = other.memo
    }

    companion object {
        @JvmStatic
        fun create(): SEpisode = SEpisodeImpl()
    }
}

class SEpisodeImpl : SEpisode {
    override var url: String = ""
    override var name: String = ""
    override var date_upload: Long = 0L
    override var episode_number: Float = -1f
    override var fillermark: Boolean = false
    override var scanlator: String? = null
    override var summary: String? = null
    override var preview_url: String? = null
    override var memo: JsonObject = JsonObject(emptyMap())
}

enum class AnimeUpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}

enum class FetchType {
    Seasons,
    Episodes,
}

class AnimesPage(
    val animes: List<SAnime>,
    val hasNextPage: Boolean,
) {
    @Deprecated("AnimesPage is now a regular class")
    operator fun component1(): List<SAnime> = animes

    @Deprecated("AnimesPage is now a regular class")
    operator fun component2(): Boolean = hasNextPage

    @Deprecated("AnimesPage is now a regular class")
    fun copy(
        animes: List<SAnime> = this.animes,
        hasNextPage: Boolean = this.hasNextPage,
    ): AnimesPage = AnimesPage(animes, hasNextPage)
}

class SAnimeEpisodeUpdate(
    val anime: SAnime,
    val episodes: List<SEpisode>,
)

class SAnimeSeasonUpdate(
    val anime: SAnime,
    val seasons: List<SAnime>,
)

class AnimeRelation(
    val name: String,
    val animes: List<SAnime>,
)
