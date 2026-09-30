package com.night.spotui

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val sourceId: String = "",
    val artistId: String = "",
    val album: String = "",
    val albumId: String = "",
    val artworkUrl: String? = null,
    val durationSeconds: Long = 0L,
    val explicit: Boolean = false,
    val license: String = "",
    val licenseUrl: String = "",
    val attribution: String = "",
) {
    val subtitle: String
        get() = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")

    /**
     * Local identity must include the provider because two music sources may expose
     * the same raw track ID. The provider-facing id remains unchanged for RPC calls.
     */
    val scopedId: String
        get() = if (sourceId.isBlank()) id else "$sourceId\u0000$id"
}

data class AlbumSummary(
    val id: String,
    val title: String,
    val artist: String,
    val sourceId: String = "",
    val artistId: String = "",
    val year: Int = 0,
    val type: String = "Album",
    val artworkUrl: String? = null,
)

data class ArtistCatalog(
    val id: String,
    val name: String,
    val sourceId: String = "",
    val artworkUrl: String? = null,
    val songs: List<Track> = emptyList(),
    val releases: List<AlbumSummary> = emptyList(),
)

data class AlbumCatalog(
    val id: String,
    val title: String,
    val artist: String,
    val sourceId: String = "",
    val artistId: String = "",
    val year: Int = 0,
    val artworkUrl: String? = null,
    val songs: List<Track> = emptyList(),
)

data class ResolvedAudio(
    val url: String,
    val label: String = "Audio",
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val contentLength: Long? = null,
    val cacheKey: String? = null,
    val cacheSourceId: String? = null,
    val resolverClient: String? = null,
    val fromCache: Boolean = false,
)
