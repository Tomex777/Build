package com.tomex777.annie

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object AnnieIcons {
    val Add: ImageVector by lazy {
        ImageVector.Builder(
            name = "Add", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(19f, 13f); horizontalLineToRelative(-6f); verticalLineToRelative(6f)
                horizontalLineToRelative(-2f); verticalLineToRelative(-6f); horizontalLineTo(5f)
                verticalLineToRelative(-2f); horizontalLineToRelative(6f); verticalLineTo(5f)
                horizontalLineToRelative(2f); verticalLineToRelative(6f); horizontalLineToRelative(6f)
                verticalLineToRelative(2f); close()
            }
        }.build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder(
            name = "Send", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(2.01f, 21f); lineTo(23f, 12f); lineTo(2.01f, 3f)
                lineTo(2f, 10f); lineTo(17f, 12f); lineTo(2f, 14f); close()
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(8f, 5f); verticalLineTo(19f); lineTo(19f, 12f); close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(6f, 5f); horizontalLineToRelative(4f); verticalLineToRelative(14f)
                horizontalLineTo(6f); close()
                moveTo(14f, 5f); horizontalLineToRelative(4f); verticalLineToRelative(14f)
                horizontalLineToRelative(-4f); close()
            }
        }.build()
    }
}
