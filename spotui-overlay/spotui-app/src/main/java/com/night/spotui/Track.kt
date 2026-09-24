package com.night.spotui

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String? = null,
    val durationSeconds: Long = 0L,
    val explicit: Boolean = false,
) {
    val subtitle: String
        get() = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")
}

data class ResolvedAudio(
    val url: String,
    val label: String = "Audio",
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
