@file:Suppress("PackageDirectoryMismatch")

package androidx.recyclerview.widget

import android.content.Context
import androidx.recyclerview.widget.RecyclerView.NO_POSITION

class WebtoonLayoutManager(
    context: Context,
    private val extraLayoutSpace: Int,
) : LinearLayoutManager(context) {
    init {
        isItemPrefetchEnabled = false
    }

    @Deprecated("Deprecated in Java")
    override fun getExtraLayoutSpace(state: RecyclerView.State): Int = extraLayoutSpace

    fun findLastEndVisibleItemPosition(): Int {
        ensureLayoutState()
        val callback = if (mOrientation == HORIZONTAL) {
            mHorizontalBoundCheck
        } else {
            mVerticalBoundCheck
        }.mCallback
        val start = callback.parentStart
        val end = callback.parentEnd
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)!!
            val childStart = callback.getChildStart(child)
            val childEnd = callback.getChildEnd(child)
            if (childEnd <= end || childStart < start) {
                return getPosition(child)
            }
        }
        return NO_POSITION
    }
}
