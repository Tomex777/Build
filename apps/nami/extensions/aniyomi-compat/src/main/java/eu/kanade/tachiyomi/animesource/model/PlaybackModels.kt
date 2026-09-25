/*
 * Compatibility API adapted from the pinned Aniyomi source API / extensions-lib.
 * Licensed under Apache-2.0.
 */
package eu.kanade.tachiyomi.animesource.model

import kotlinx.serialization.json.JsonObject
import okhttp3.Headers

data class Track(val url: String, val lang: String)

enum class ChapterType {
    Opening,
    Ending,
    Recap,
    MixedOp,
    Other,
}

data class TimeStamp(
    val start: Double,
    val end: Double,
    val name: String,
    val type: ChapterType = ChapterType.Other,
)

data class Video(
    var videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
    val memo: JsonObject = JsonObject(emptyMap()),
) {
    @Deprecated("Use videoTitle instead", ReplaceWith("videoTitle"))
    val quality: String
        get() = videoTitle

    @Deprecated("Legacy compatibility")
    val url: String
        get() = videoPageUrl

    private var videoPageUrl: String = ""

    // extensions-lib 16 constructor. Keep this exact parameter list for binary compatibility.
    @Deprecated(
        "Used only for compatibility with extensions-lib 16",
        level = DeprecationLevel.HIDDEN,
    )
    constructor(
        videoUrl: String = "",
        videoTitle: String = "",
        resolution: Int? = null,
        bitrate: Int? = null,
        headers: Headers? = null,
        preferred: Boolean = false,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
        timestamps: List<TimeStamp> = emptyList(),
        mpvArgs: List<Pair<String, String>> = emptyList(),
        ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
        ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
        internalData: String = "",
        initialized: Boolean = false,
    ) : this(
        videoUrl = videoUrl,
        videoTitle = videoTitle,
        resolution = resolution,
        bitrate = bitrate,
        headers = headers,
        preferred = preferred,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
        timestamps = timestamps,
        mpvArgs = mpvArgs,
        ffmpegStreamArgs = ffmpegStreamArgs,
        ffmpegVideoArgs = ffmpegVideoArgs,
        internalData = internalData,
        initialized = initialized,
        memo = JsonObject(emptyMap()),
    )

    // Pre-v16 legacy constructor retained by Aniyomi.
    @Deprecated("Legacy compatibility constructor")
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        headers: Headers? = null,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
    ) : this(
        videoTitle = quality,
        videoUrl = videoUrl ?: "null",
        headers = headers,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
    ) {
        videoPageUrl = url
    }
}

open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
    val memo: JsonObject = JsonObject(emptyMap()),
) {
    // extensions-lib 16 constructor. Keep this exact parameter list for binary compatibility.
    constructor(
        hosterUrl: String = "",
        hosterName: String = "",
        videoList: List<Video>? = null,
        internalData: String = "",
        lazy: Boolean = false,
    ) : this(
        hosterUrl = hosterUrl,
        hosterName = hosterName,
        videoList = videoList,
        internalData = internalData,
        lazy = lazy,
        memo = JsonObject(emptyMap()),
    )

    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
        memo: JsonObject = this.memo,
    ): Hoster = Hoster(hosterUrl, hosterName, videoList, internalData, lazy, memo)

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        @JvmStatic
        fun List<Video>.toHosterList(): List<Hoster> = listOf(
            Hoster(
                hosterUrl = "",
                hosterName = NO_HOSTER_LIST,
                videoList = this,
            ),
        )
    }
}
