package eu.kanade.tachiyomi.ui.reader.setting

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.ui.graphics.vector.ImageVector
import app.yomi.reader.R

/**
 * Yomi's local-book adapters for the reading mode/orientation types expected
 * by Mihon's v0.19.9 presentation components.
 */
enum class ReadingMode(@DrawableRes val iconRes: Int) {
    DEFAULT(R.drawable.ic_reader_default_24dp),
    LEFT_TO_RIGHT(R.drawable.ic_reader_ltr_24dp),
    RIGHT_TO_LEFT(R.drawable.ic_reader_rtl_24dp),
    VERTICAL(R.drawable.ic_reader_vertical_24dp),
    WEBTOON(R.drawable.ic_reader_webtoon_24dp),
    CONTINUOUS_VERTICAL(R.drawable.ic_reader_continuous_vertical_24dp),
}

enum class ReaderOrientation(val icon: ImageVector) {
    DEFAULT(Icons.Default.ScreenRotation),
    PORTRAIT(Icons.Default.StayCurrentPortrait),
    LANDSCAPE(Icons.Default.StayCurrentLandscape),
}
