package com.tomex777.annie

/** Pan only where the fitted image exceeds the viewport, keeping letterboxed axes centered. */
internal fun annieImagePanBounds(
    imageWidth: Int, imageHeight: Int, viewportWidth: Int, viewportHeight: Int, scale: Float,
): Pair<Float, Float> {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0 ||
        !scale.isFinite()) return 0f to 0f
    val fit = minOf(viewportWidth.toFloat() / imageWidth, viewportHeight.toFloat() / imageHeight)
    val zoom = scale.coerceAtLeast(1f)
    return ((imageWidth * fit * zoom - viewportWidth) / 2f).coerceAtLeast(0f) to
        ((imageHeight * fit * zoom - viewportHeight) / 2f).coerceAtLeast(0f)
}
