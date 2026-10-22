package com.nice.juicewrld

enum class RepeatMode(val label: String) {
    OFF("Repeat off"), ALL("Repeat queue"), ONE("Repeat song");

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

/** Pure song queue: no network, platform clock, UI, or Android dependency. */
object MusicQueue {
    /** The queue must reflect the current feeling even when the selected track is unchanged. */
    fun hasSameSongs(queue: List<String>, songs: List<String>): Boolean =
        queue.isNotEmpty() && queue.size == songs.distinct().size && queue.toSet() == songs.toSet()

    fun build(songIds: List<String>, current: String, shuffled: Boolean): List<String> {
        val unique = songIds.distinct()
        if (current !in unique) return emptyList()
        if (!shuffled) return unique
        return listOf(current) + unique.filterNot { it == current }.shuffled()
    }

    fun step(
        queue: List<String>,
        current: String,
        direction: Int,
        repeat: RepeatMode,
        completedNaturally: Boolean = false,
    ): String? {
        if (current !in queue || queue.isEmpty()) return null
        if (completedNaturally && repeat == RepeatMode.ONE) return current
        val index = queue.indexOf(current)
        val next = index + direction
        if (next in queue.indices) return queue[next]
        if (repeat == RepeatMode.ALL) return if (direction >= 0) queue.first() else queue.last()
        // Manual previous on the first song keeps the song selected.
        if (!completedNaturally && direction < 0) return current
        return null
    }
}
