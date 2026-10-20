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
            // A late Gecko location callback can arrive *after* onPageStop.
            // Do not consume an expected Back/Forward move on the stale URL.
            if (expected == Expected.RELOAD || expected == Expected.NONE) {
                expected = Expected.NONE
            }
            return nodeId
        }
        val navigation = expected
        val match = when (navigation) {
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

        // A restored Gecko tab may have its complete web history but only a
        // single saved Journey cursor (the current page). Reconstruct the
        // missing backward trail from persisted parent edges, instead of
        // wrongly recording Back as a *new child* of the page being left.
        if (navigation == Expected.BACK && index >= 0) {
            val ancestors = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            var ancestorId = nodeId?.let(lookup)?.parent
            while (ancestorId != null && seen.add(ancestorId)) {
                val ancestor = lookup(ancestorId) ?: break
                ancestors.add(ancestorId)
                if (ancestor.url == safe) {
                    trail.addAll(index, ancestors.asReversed())
                    // The inserted first element is now the page reached by Back.
                    return nodeId
                }
                ancestorId = ancestor.parent
            }
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
