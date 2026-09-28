package reader.shared.android

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import reader.shared.android.navigation.LNavigation
import reader.shared.android.navigation.RightAndLeftNavigation
import reader.shared.android.navigation.ViewerNavigation

data class ReaderRenderConfig(
    val cropBorders: Boolean = false,
    val backgroundColor: Int = 0xFF000000.toInt(),
    val pageTransitions: Boolean = true,
    val volumeKeysEnabled: Boolean = false,
    val volumeKeysInverted: Boolean = false,
    val longTapEnabled: Boolean = true,
    val zoomDurationMillis: Int = 300,
    val minimumScaleType: Int = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
    val webtoonSidePaddingPercent: Int = 0,
    val doubleTapZoom: Boolean = true,
    val webtoonZoomOutDisabled: Boolean = false,
    val pagerNavigation: ViewerNavigation = RightAndLeftNavigation(),
    val webtoonNavigation: ViewerNavigation = LNavigation(),
)
