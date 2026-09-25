package app.nami.android

import app.nami.domain.AnimeEpisode
import app.nami.domain.ResolvedMedia

object PlaybackMediaSelector {
    private val heightPattern = Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE)

    fun height(media: ResolvedMedia): Int? = media.quality
        ?.let { heightPattern.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

    fun choose(
        media: List<ResolvedMedia>,
        preferredHeight: Int? = null,
        preferredHost: String? = null,
    ): ResolvedMedia? {
        val playable = media.filter { it.url.isNotBlank() }
        if (playable.isEmpty()) return null
        val hostFiltered = preferredHost
            ?.takeIf { it.isNotBlank() }
            ?.let { host ->
                playable.filter { it.hosterName.equals(host, ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
            }
            ?: playable

        if (preferredHeight == null) {
            return hostFiltered.maxWithOrNull(
                compareBy<ResolvedMedia> { height(it) ?: -1 }
                    .thenBy { it.quality.orEmpty() },
            ) ?: hostFiltered.first()
        }

        hostFiltered.firstOrNull { height(it) == preferredHeight }?.let { return it }
        val known = hostFiltered.mapNotNull { candidate ->
            height(candidate)?.let { it to candidate }
        }
        known.filter { it.first < preferredHeight }.maxByOrNull { it.first }?.second?.let { return it }
        known.filter { it.first > preferredHeight }.minByOrNull { it.first }?.second?.let { return it }
        return hostFiltered.first()
    }

    fun label(media: ResolvedMedia): String = listOfNotNull(
        media.quality?.takeIf { it.isNotBlank() },
        media.hosterName?.takeIf { it.isNotBlank() },
    ).joinToString(" • ").ifBlank { "Stream" }
}

object PlaybackEpisodeNavigator {
    fun previousIndex(episodes: List<AnimeEpisode>, currentIndex: Int): Int? =
        neighborIndex(episodes, currentIndex, forward = false)

    fun nextIndex(episodes: List<AnimeEpisode>, currentIndex: Int): Int? =
        neighborIndex(episodes, currentIndex, forward = true)

    private fun neighborIndex(
        episodes: List<AnimeEpisode>,
        currentIndex: Int,
        forward: Boolean,
    ): Int? {
        if (currentIndex !in episodes.indices) return null
        val currentNumber = episodes[currentIndex].number
        if (currentNumber != null) {
            val candidates = episodes.mapIndexedNotNull { index, episode ->
                episode.number?.let { number -> index to number }
            }
            return if (forward) {
                candidates.filter { it.second > currentNumber }.minByOrNull { it.second }?.first
            } else {
                candidates.filter { it.second < currentNumber }.maxByOrNull { it.second }?.first
            }
        }
        val fallback = currentIndex + if (forward) 1 else -1
        return fallback.takeIf { it in episodes.indices }
    }
}
