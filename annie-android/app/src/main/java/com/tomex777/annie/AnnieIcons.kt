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

    val Menu: ImageVector by lazy {
        ImageVector.Builder(name = "Menu", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(3f, 6f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(3f); close()
                moveTo(3f, 11f); horizontalLineTo(21f); verticalLineTo(13f); horizontalLineTo(3f); close()
                moveTo(3f, 16f); horizontalLineTo(21f); verticalLineTo(18f); horizontalLineTo(3f); close()
            }
        }.build()
    }

    val NewChat: ImageVector by lazy {
        ImageVector.Builder(name = "NewChat", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(4f, 4f); horizontalLineTo(15f); verticalLineTo(6f); horizontalLineTo(6f)
                verticalLineTo(18f); horizontalLineTo(15f); verticalLineTo(20f); horizontalLineTo(4f); close()
                moveTo(18.2f, 3f); lineTo(21f, 5.8f); lineTo(12.1f, 14.7f); lineTo(8.8f, 15.2f)
                lineTo(9.3f, 11.9f); close()
            }
        }.build()
    }

    val Library: ImageVector by lazy {
        ImageVector.Builder(name = "Library", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(4f, 4f); horizontalLineToRelative(7f); verticalLineToRelative(15f)
                lineTo(9f, 17.5f); lineTo(4f, 19f); close()
                moveTo(13f, 4f); horizontalLineToRelative(7f); verticalLineToRelative(15f)
                lineTo(15f, 17.5f); lineTo(13f, 19f); close()
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder(name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(11f, 3f); horizontalLineToRelative(2f); verticalLineToRelative(10f)
                lineToRelative(3.5f, -3.5f); lineToRelative(1.4f, 1.4f); lineTo(12f, 17f)
                lineToRelative(-5.9f, -6.1f); lineToRelative(1.4f, -1.4f); lineTo(11f, 13f); close()
                moveTo(5f, 19f); horizontalLineToRelative(14f); verticalLineToRelative(2f); horizontalLineTo(5f); close()
            }
        }.build()
    }

    val Package: ImageVector by lazy {
        ImageVector.Builder(name = "Package", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(12f, 2f); lineTo(21f, 7f); verticalLineTo(17f); lineTo(12f, 22f)
                lineTo(3f, 17f); verticalLineTo(7f); close()
                moveTo(12f, 4.3f); lineTo(6f, 7.6f); lineTo(12f, 11f); lineTo(18f, 7.6f); close()
                moveTo(5f, 9.3f); verticalLineTo(15.8f); lineTo(11f, 19.1f); verticalLineTo(12.7f); close()
                moveTo(13f, 12.7f); verticalLineTo(19.1f); lineTo(19f, 15.8f); verticalLineTo(9.3f); close()
            }
        }.build()
    }
}
