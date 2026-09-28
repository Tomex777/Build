package reader.shared.android.navigation

import android.graphics.RectF
import reader.shared.android.navigation.ViewerNavigation.NavigationRegion

class DisabledNavigation : ViewerNavigation() {
    override val regionList: List<Region> = emptyList()
}

class EdgeNavigation : ViewerNavigation() {
    override val regionList = listOf(
        Region(RectF(0f, 0f, 0.33f, 1f), NavigationRegion.NEXT),
        Region(RectF(0.33f, 0.66f, 0.66f, 1f), NavigationRegion.PREV),
        Region(RectF(0.66f, 0f, 1f, 1f), NavigationRegion.NEXT),
    )
}

class KindlishNavigation : ViewerNavigation() {
    override val regionList = listOf(
        Region(RectF(0.33f, 0.33f, 1f, 1f), NavigationRegion.NEXT),
        Region(RectF(0f, 0.33f, 0.33f, 1f), NavigationRegion.PREV),
    )
}

open class LNavigation : ViewerNavigation() {
    override val regionList = listOf(
        Region(RectF(0f, 0.33f, 0.33f, 0.66f), NavigationRegion.PREV),
        Region(RectF(0f, 0f, 1f, 0.33f), NavigationRegion.PREV),
        Region(RectF(0.66f, 0.33f, 1f, 0.66f), NavigationRegion.NEXT),
        Region(RectF(0f, 0.66f, 1f, 1f), NavigationRegion.NEXT),
    )
}

class RightAndLeftNavigation : ViewerNavigation() {
    override val regionList = listOf(
        Region(RectF(0f, 0f, 0.33f, 1f), NavigationRegion.LEFT),
        Region(RectF(0.66f, 0f, 1f, 1f), NavigationRegion.RIGHT),
    )
}
