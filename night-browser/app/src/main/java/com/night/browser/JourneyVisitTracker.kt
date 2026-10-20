package com.night.browser

/**
 * Per-tab browser-history cursor mapped onto persistent Journey IDs.
 *
 * Gecko's back/forward revisits must move this cursor, not create a new child of
 * the page we are leaving. New navigations after Back fork the persistent tree.
 * The small per-tab cursor is intentionally not itself browsing-history storage.
 */
class JourneyVisitTracker(
    private val lookup: (String) -> JourneyNode?,
    private val saveVisit: (url: String, title: String, parentId: String?) -> String?,
    seedNodeId: String? = null
) {
    private enum class Expected { NONE, BACK, FORWARD, RELOAD }

    private val trail = mutableListOf<String>()
    private var index = -1
    private var expected = Expected.NONE

    init {
        if (seedNodeId != null && lookup(seedNodeId) != null) {
            trail.add(seedNodeId)
            index = 0
        }
    }

    val nodeId: String? get() = trail.getOrNull(index)

    fun expectBack() { expected = Expected.BACK }
    fun expectForward() { expected = Expected.FORWARD }
    fun expectReload() { expected = Expected.RELOAD }

    /**
     * Call only for committed page destinations (end of network load, or an
     * in-document location change). Redirect intermediates should not be saved.
     */
    fun visited(rawUrl: String, title: String): String? {
        val safe = JourneyGraph.safeUrl(rawUrl) ?: return null
        if (nodeId?.let(lookup)?.url == safe) {
            expected = Expected.NONE
            return nodeId
        }
        val match = when (expected) {
            Expected.BACK -> (index - 1 downTo 0).firstOrNull {
                lookup(trail[it])?.url == safe
            }
            Expected.FORWARD -> ((index + 1)..trail.lastIndex).firstOrNull {
                lookup(trail[it])?.url == safe
            }
            Expected.RELOAD, Expected.NONE -> null
        }
        expected = Expected.NONE
        if (match != null) {
            index = match
            return nodeId
        }

        val parent = nodeId
        val created = saveVisit(safe, title, parent) ?: run {
            // Privacy pause or a removed Journey: do not later attach new
            // visits to a page that was traversed without recording.
            trail.clear()
            index = -1
            return null
        }
        // When Back was used, forward history becomes a separate, still-saved branch.
        if (index < trail.lastIndex) trail.subList(index + 1, trail.size).clear()
        trail.add(created)
        index = trail.lastIndex
        return created
    }
}
