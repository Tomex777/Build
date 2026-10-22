package com.tomex777.annie

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Canvas-specific vector controls; independent of browser and chat agents. */
internal object AnnieCanvasIcons {
    val Expand: ImageVector by lazy {
        ImageVector.Builder(name = "Canvas expand", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 4f); horizontalLineTo(10f); verticalLineTo(6f); horizontalLineTo(6f)
                verticalLineTo(10f); horizontalLineTo(4f); close()
                moveTo(14f, 4f); horizontalLineTo(20f); verticalLineTo(10f); horizontalLineTo(18f)
                verticalLineTo(6f); horizontalLineTo(14f); close()
                moveTo(4f, 14f); horizontalLineTo(6f); verticalLineTo(18f)
                horizontalLineTo(10f); verticalLineTo(20f); horizontalLineTo(4f); close()
                moveTo(18f, 14f); horizontalLineTo(20f); verticalLineTo(20f)
                horizontalLineTo(14f); verticalLineTo(18f); horizontalLineTo(18f); close()
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(name = "Canvas close", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(5.4f, 4f); lineTo(12f, 10.6f); lineTo(18.6f, 4f)
                lineTo(20f, 5.4f); lineTo(13.4f, 12f); lineTo(20f, 18.6f)
                lineTo(18.6f, 20f); lineTo(12f, 13.4f); lineTo(5.4f, 20f)
                lineTo(4f, 18.6f); lineTo(10.6f, 12f); lineTo(4f, 5.4f); close()
            }
        }.build()
    }
}
