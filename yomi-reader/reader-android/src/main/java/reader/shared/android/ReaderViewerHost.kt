package reader.shared.android

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import reader.shared.android.model.ViewerChapter
import reader.shared.android.model.ViewerChapters
import reader.shared.android.model.ViewerPage

interface ReaderViewerHost {
    val context: Context
    val menuVisible: Boolean
    val isScrollingThroughPages: Boolean get() = false

    fun hideMenu()
    fun showMenu()
    fun toggleMenu()
    fun onPageSelected(page: ViewerPage, pageOffsetFraction: Double = 0.0)
    fun onPageLongTap(page: ViewerPage): Boolean = false
    fun requestPreloadChapter(chapter: ViewerChapter)
}

interface Viewer {
    fun getView(): View
    fun setChapters(chapters: ViewerChapters)
    fun moveToPage(page: ViewerPage, offsetFraction: Double = 0.0)
    fun handleKeyEvent(event: KeyEvent): Boolean = false
    fun handleGenericMotionEvent(event: MotionEvent): Boolean = false
    fun destroy() = Unit
}
