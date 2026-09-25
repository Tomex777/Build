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

/**
 * Explicit compatibility class rather than a Kotlin data class.
 *
 * extensions-lib 16 and 17 compiled different generated copy/copy$default signatures because v17
 * added [memo]. Both JVM ABIs are exposed manually so separately compiled extension APKs can call
 * either shape safely.
 */
class Video(
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
    @Deprecated(
        "extensions-lib 16 constructor retained for binary compatibility",
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

    @Deprecated("Use videoTitle instead", ReplaceWith("videoTitle"))
    val quality: String
        get() = videoTitle

    @Deprecated("Legacy compatibility")
    val url: String
        get() = videoPageUrl

    private var videoPageUrl: String = ""

    fun copy(
        videoUrl: String = this.videoUrl,
        videoTitle: String = this.videoTitle,
        resolution: Int? = this.resolution,
        bitrate: Int? = this.bitrate,
        headers: Headers? = this.headers,
        preferred: Boolean = this.preferred,
        subtitleTracks: List<Track> = this.subtitleTracks,
        audioTracks: List<Track> = this.audioTracks,
        timestamps: List<TimeStamp> = this.timestamps,
        mpvArgs: List<Pair<String, String>> = this.mpvArgs,
        ffmpegStreamArgs: List<Pair<String, String>> = this.ffmpegStreamArgs,
        ffmpegVideoArgs: List<Pair<String, String>> = this.ffmpegVideoArgs,
        internalData: String = this.internalData,
        initialized: Boolean = this.initialized,
        memo: JsonObject = this.memo,
    ): Video = Video(
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
        memo = memo,
    ).also {
        it.videoPageUrl = videoPageUrl
    }

    @Deprecated(
        "extensions-lib 16 copy retained for binary compatibility",
        level = DeprecationLevel.HIDDEN,
    )
    fun copy(
        videoUrl: String,
        videoTitle: String,
        resolution: Int?,
        bitrate: Int?,
        headers: Headers?,
        preferred: Boolean,
        subtitleTracks: List<Track>,
        audioTracks: List<Track>,
        timestamps: List<TimeStamp>,
        mpvArgs: List<Pair<String, String>>,
        ffmpegStreamArgs: List<Pair<String, String>>,
        ffmpegVideoArgs: List<Pair<String, String>>,
        internalData: String,
        initialized: Boolean,
    ): Video = copy(
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
        memo = memo,
    )

    operator fun component1(): String = videoUrl
    operator fun component2(): String = videoTitle
    operator fun component3(): Int? = resolution
    operator fun component4(): Int? = bitrate
    operator fun component5(): Headers? = headers
    operator fun component6(): Boolean = preferred
    operator fun component7(): List<Track> = subtitleTracks
    operator fun component8(): List<Track> = audioTracks
    operator fun component9(): List<TimeStamp> = timestamps
    operator fun component10(): List<Pair<String, String>> = mpvArgs
    operator fun component11(): List<Pair<String, String>> = ffmpegStreamArgs
    operator fun component12(): List<Pair<String, String>> = ffmpegVideoArgs
    operator fun component13(): String = internalData
    operator fun component14(): Boolean = initialized
    operator fun component15(): JsonObject = memo

    override fun equals(other: Any?): Boolean =
        other is Video &&
            videoUrl == other.videoUrl &&
            videoTitle == other.videoTitle &&
            resolution == other.resolution &&
            bitrate == other.bitrate &&
            headers == other.headers &&
            preferred == other.preferred &&
            subtitleTracks == other.subtitleTracks &&
            audioTracks == other.audioTracks &&
            timestamps == other.timestamps &&
            mpvArgs == other.mpvArgs &&
            ffmpegStreamArgs == other.ffmpegStreamArgs &&
            ffmpegVideoArgs == other.ffmpegVideoArgs &&
            internalData == other.internalData &&
            initialized == other.initialized &&
            memo == other.memo

    override fun hashCode(): Int {
        var result = videoUrl.hashCode()
        result = 31 * result + videoTitle.hashCode()
        result = 31 * result + (resolution ?: 0)
        result = 31 * result + (bitrate ?: 0)
        result = 31 * result + (headers?.hashCode() ?: 0)
        result = 31 * result + preferred.hashCode()
        result = 31 * result + subtitleTracks.hashCode()
        result = 31 * result + audioTracks.hashCode()
        result = 31 * result + timestamps.hashCode()
        result = 31 * result + mpvArgs.hashCode()
        result = 31 * result + ffmpegStreamArgs.hashCode()
        result = 31 * result + ffmpegVideoArgs.hashCode()
        result = 31 * result + internalData.hashCode()
        result = 31 * result + initialized.hashCode()
        result = 31 * result + memo.hashCode()
        return result
    }

    override fun toString(): String =
        "Video(videoUrl=$videoUrl, videoTitle=$videoTitle, resolution=$resolution, " +
            "bitrate=$bitrate, headers=$headers, preferred=$preferred, " +
            "subtitleTracks=$subtitleTracks, audioTracks=$audioTracks, timestamps=$timestamps, " +
            "mpvArgs=$mpvArgs, ffmpegStreamArgs=$ffmpegStreamArgs, " +
            "ffmpegVideoArgs=$ffmpegVideoArgs, internalData=$internalData, " +
            "initialized=$initialized, memo=$memo)"

    companion object {
        /**
         * Kotlin-generated extensions-lib 16 data-class ABI:
         * Video + 14 fields + mask + marker.
         */
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun `copy$default`(
            self: Video,
            videoUrl: String?,
            videoTitle: String?,
            resolution: Int?,
            bitrate: Int?,
            headers: Headers?,
            preferred: Boolean,
            subtitleTracks: List<Track>?,
            audioTracks: List<Track>?,
            timestamps: List<TimeStamp>?,
            mpvArgs: List<Pair<String, String>>?,
            ffmpegStreamArgs: List<Pair<String, String>>?,
            ffmpegVideoArgs: List<Pair<String, String>>?,
            internalData: String?,
            initialized: Boolean,
            mask: Int,
            marker: Any?,
        ): Video = self.copy(
            videoUrl = if (mask and 0x1 != 0) self.videoUrl else videoUrl.orEmpty(),
            videoTitle = if (mask and 0x2 != 0) self.videoTitle else videoTitle.orEmpty(),
            resolution = if (mask and 0x4 != 0) self.resolution else resolution,
            bitrate = if (mask and 0x8 != 0) self.bitrate else bitrate,
            headers = if (mask and 0x10 != 0) self.headers else headers,
            preferred = if (mask and 0x20 != 0) self.preferred else preferred,
            subtitleTracks = if (mask and 0x40 != 0) self.subtitleTracks else subtitleTracks.orEmpty(),
            audioTracks = if (mask and 0x80 != 0) self.audioTracks else audioTracks.orEmpty(),
            timestamps = if (mask and 0x100 != 0) self.timestamps else timestamps.orEmpty(),
            mpvArgs = if (mask and 0x200 != 0) self.mpvArgs else mpvArgs.orEmpty(),
            ffmpegStreamArgs = if (mask and 0x400 != 0) self.ffmpegStreamArgs else ffmpegStreamArgs.orEmpty(),
            ffmpegVideoArgs = if (mask and 0x800 != 0) self.ffmpegVideoArgs else ffmpegVideoArgs.orEmpty(),
            internalData = if (mask and 0x1000 != 0) self.internalData else internalData.orEmpty(),
            initialized = if (mask and 0x2000 != 0) self.initialized else initialized,
        )


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
    @Deprecated(
        "extensions-lib 16 constructor retained for binary compatibility",
        level = DeprecationLevel.HIDDEN,
    )
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
