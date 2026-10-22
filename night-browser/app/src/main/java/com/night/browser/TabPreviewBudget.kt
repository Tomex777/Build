package com.night.browser

/** A soft budget for Gecko pixel previews: the grid must not keep dozens of full images alive. */
internal object TabPreviewBudget {
    const val MAX_CARDS = 24
    const val MAX_BYTES = 14L * 1024L * 1024L

    data class Preview(val tabId: String, val bytes: Long)

    /**
     * Input order is creation order (oldest -> newest). Prefer the active tab,
     * then recent tabs. Clear references rather than calling Bitmap.recycle():
     * Compose may still be drawing the image in this frame.
     */
    fun retainedIds(items: List<Preview>, activeId: String?): Set<String> {
        val ordered = items.asReversed().sortedByDescending { it.tabId == activeId }
        val keep = linkedSetOf<String>()
        var usedBytes = 0L
        for (preview in ordered) {
            if (preview.bytes <= 0L || preview.bytes > MAX_BYTES) continue
            if (keep.size >= MAX_CARDS) break
            if (usedBytes + preview.bytes > MAX_BYTES) continue
            keep += preview.tabId
            usedBytes += preview.bytes
        }
        return keep
    }
}
