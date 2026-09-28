package reader.shared.android.navigation

import android.graphics.PointF
import android.graphics.RectF

abstract class ViewerNavigation {
    enum class NavigationRegion {
        MENU,
        PREV,
        NEXT,
        LEFT,
        RIGHT,
    }

    enum class InvertMode(val horizontal: Boolean, val vertical: Boolean) {
        NONE(false, false),
        HORIZONTAL(true, false),
        VERTICAL(false, true),
        BOTH(true, true),
    }

    data class Region(
        val rectF: RectF,
        val type: NavigationRegion,
    )

    var invertMode: InvertMode = InvertMode.NONE

    protected abstract val regionList: List<Region>

    fun getRegions(): List<Region> = regionList.map { region ->
        region.copy(rectF = region.rectF.invert(invertMode))
    }

    fun getAction(pos: PointF): NavigationRegion {
        val region = getRegions().find { it.rectF.contains(pos.x, pos.y) }
        return region?.type ?: NavigationRegion.MENU
    }

    private fun RectF.invert(mode: InvertMode): RectF {
        return when {
            mode.horizontal && mode.vertical -> RectF(1f - right, 1f - bottom, 1f - left, 1f - top)
            mode.vertical -> RectF(left, 1f - bottom, right, 1f - top)
            mode.horizontal -> RectF(1f - right, top, 1f - left, bottom)
            else -> RectF(this)
        }
    }
}
