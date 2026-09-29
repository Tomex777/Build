package com.tomex777.annie

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object AnnieIcons {
    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(name = "ArrowBack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(20f, 11f); horizontalLineTo(7.83f); lineTo(13.42f, 5.41f); lineTo(12f, 4f)
                lineTo(4f, 12f); lineTo(12f, 20f); lineTo(13.41f, 18.59f); lineTo(7.83f, 13f)
                horizontalLineTo(20f); close()
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(name = "Lock", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(18f, 8f); horizontalLineTo(17f); verticalLineTo(6f)
                curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f); curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
                verticalLineTo(8f); horizontalLineTo(6f); curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
                verticalLineTo(20f); curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f); horizontalLineTo(18f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f); verticalLineTo(10f)
                curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f); close()
                moveTo(9f, 6f); curveTo(9f, 4.34f, 10.34f, 3f, 12f, 3f)
                curveTo(13.66f, 3f, 15f, 4.34f, 15f, 6f); verticalLineTo(8f); horizontalLineTo(9f); close()
                moveTo(13f, 16.73f); verticalLineTo(18f); horizontalLineTo(11f); verticalLineTo(16.73f)
                curveTo(10.4f, 16.38f, 10f, 15.73f, 10f, 15f); curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
                curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f); curveTo(14f, 15.73f, 13.6f, 16.38f, 13f, 16.73f); close()
            }
        }.build()
    }

    val LockOpen: ImageVector by lazy {
        ImageVector.Builder(name = "LockOpen", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(18f, 8f); horizontalLineTo(9f); verticalLineTo(6f)
                curveTo(9f, 4.34f, 10.34f, 3f, 12f, 3f); curveTo(13.66f, 3f, 15f, 4.34f, 15f, 6f)
                horizontalLineTo(17f); curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
                curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f); verticalLineTo(8f); horizontalLineTo(6f)
                curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f); verticalLineTo(20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f); horizontalLineTo(18f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f); verticalLineTo(10f)
                curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f); close()
                moveTo(13f, 16.73f); verticalLineTo(18f); horizontalLineTo(11f); verticalLineTo(16.73f)
                curveTo(10.4f, 16.38f, 10f, 15.73f, 10f, 15f); curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
                curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f); curveTo(14f, 15.73f, 13.6f, 16.38f, 13f, 16.73f); close()
            }
        }.build()
    }

    val Subtitles: ImageVector by lazy {
        ImageVector.Builder(name = "Subtitles", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(3f, 5f); horizontalLineTo(21f); verticalLineTo(7f); horizontalLineTo(3f); close()
                moveTo(3f, 17f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(3f); close()
                moveTo(3f, 7f); horizontalLineTo(5f); verticalLineTo(17f); horizontalLineTo(3f); close()
                moveTo(19f, 7f); horizontalLineTo(21f); verticalLineTo(17f); horizontalLineTo(19f); close()
                moveTo(7f, 10f); horizontalLineTo(17f); verticalLineTo(12f); horizontalLineTo(7f); close()
                moveTo(7f, 14f); horizontalLineTo(14f); verticalLineTo(16f); horizontalLineTo(7f); close()
            }
        }.build()
    }

    val AudioTrack: ImageVector by lazy {
        ImageVector.Builder(name = "AudioTrack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(3f, 9f); verticalLineTo(15f); horizontalLineTo(7f); lineTo(12f, 19f)
                verticalLineTo(5f); lineTo(7f, 9f); close()
                moveTo(16f, 12f); curveTo(16f, 10.23f, 14.97f, 8.71f, 13.5f, 7.97f)
                verticalLineTo(16.02f); curveTo(14.97f, 15.29f, 16f, 13.77f, 16f, 12f); close()
                moveTo(13.5f, 3.23f); verticalLineTo(5.29f); curveTo(16.39f, 6.15f, 18.5f, 8.83f, 18.5f, 12f)
                curveTo(18.5f, 15.17f, 16.39f, 17.85f, 13.5f, 18.71f); verticalLineTo(20.77f)
                curveTo(17.51f, 19.86f, 20.5f, 16.28f, 20.5f, 12f); curveTo(20.5f, 7.72f, 17.51f, 4.14f, 13.5f, 3.23f); close()
            }
        }.build()
    }

    val SkipBack: ImageVector by lazy {
        ImageVector.Builder(name = "SkipBack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(6f, 5f); horizontalLineTo(8f); verticalLineTo(19f); horizontalLineTo(6f); close()
                moveTo(19f, 5f); lineTo(10f, 12f); lineTo(19f, 19f); close()
            }
        }.build()
    }

    val SkipForward: ImageVector by lazy {
        ImageVector.Builder(name = "SkipForward", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(16f, 5f); horizontalLineTo(18f); verticalLineTo(19f); horizontalLineTo(16f); close()
                moveTo(5f, 5f); lineTo(14f, 12f); lineTo(5f, 19f); close()
            }
        }.build()
    }

    val Rotate: ImageVector by lazy {
        ImageVector.Builder(name = "Rotate", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(12f, 6f); verticalLineTo(3f); lineTo(8f, 7f); lineTo(12f, 11f); verticalLineTo(8f)
                curveTo(15.31f, 8f, 18f, 10.69f, 18f, 14f); curveTo(18f, 15.1f, 17.7f, 16.13f, 17.17f, 17f)
                lineTo(18.63f, 18.46f); curveTo(19.49f, 17.22f, 20f, 15.69f, 20f, 14f)
                curveTo(20f, 9.58f, 16.42f, 6f, 12f, 6f); close()
                moveTo(6f, 14f); curveTo(6f, 12.9f, 6.3f, 11.87f, 6.83f, 11f); lineTo(5.37f, 9.54f)
                curveTo(4.51f, 10.78f, 4f, 12.31f, 4f, 14f); curveTo(4f, 18.42f, 7.58f, 22f, 12f, 22f)
                verticalLineTo(24f); lineTo(16f, 20f); lineTo(12f, 16f); verticalLineTo(20f)
                curveTo(8.69f, 20f, 6f, 17.31f, 6f, 14f); close()
            }
        }.build()
    }

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

    val Close: ImageVector by lazy {
        ImageVector.Builder(name = "Close", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(6.4f, 5f); lineTo(12f, 10.6f); lineTo(17.6f, 5f); lineTo(19f, 6.4f)
                lineTo(13.4f, 12f); lineTo(19f, 17.6f); lineTo(17.6f, 19f); lineTo(12f, 13.4f)
                lineTo(6.4f, 19f); lineTo(5f, 17.6f); lineTo(10.6f, 12f); lineTo(5f, 6.4f); close()
            }
        }.build()
    }

    val ArrowForward: ImageVector by lazy {
        ImageVector.Builder(name = "ArrowForward", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(4f, 11f); horizontalLineTo(16.2f); lineTo(10.6f, 5.4f); lineTo(12f, 4f)
                lineTo(20f, 12f); lineTo(12f, 20f); lineTo(10.6f, 18.6f); lineTo(16.2f, 13f)
                horizontalLineTo(4f); close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(name = "Refresh", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(17.7f, 6.3f); curveTo(16.2f, 4.9f, 14.2f, 4f, 12f, 4f)
                curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f); horizontalLineTo(1f)
                lineTo(5f, 16f); lineTo(9f, 12f); horizontalLineTo(6f)
                curveTo(6f, 8.7f, 8.7f, 6f, 12f, 6f); curveTo(13.7f, 6f, 15.1f, 6.7f, 16.2f, 7.8f)
                lineTo(17.7f, 6.3f); close()
                moveTo(20f, 12f); curveTo(20f, 15.3f, 17.3f, 18f, 14f, 18f)
                curveTo(12.3f, 18f, 10.9f, 17.3f, 9.8f, 16.2f); lineTo(8.3f, 17.7f)
                curveTo(9.8f, 19.1f, 11.8f, 20f, 14f, 20f); curveTo(18.4f, 20f, 22f, 16.4f, 22f, 12f)
                close()
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(name = "ChevronDown", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(6.6f, 8.6f); lineTo(12f, 14f); lineTo(17.4f, 8.6f); lineTo(18.8f, 10f)
                lineTo(12f, 16.8f); lineTo(5.2f, 10f); close()
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(name = "ChevronRight", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(8.6f, 5.2f); lineTo(15.4f, 12f); lineTo(8.6f, 18.8f); lineTo(7.2f, 17.4f)
                lineTo(12.6f, 12f); lineTo(7.2f, 6.6f); close()
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
