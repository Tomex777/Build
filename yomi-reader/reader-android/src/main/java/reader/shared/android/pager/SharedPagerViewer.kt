package reader.shared.android.pager

import android.graphics.PointF
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.viewpager.widget.ViewPager
import app.yomi.reader.core.assembleContinuousPagedWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import reader.shared.android.ReaderPageImageView
import reader.shared.android.ReaderRenderConfig
import reader.shared.android.ReaderViewerHost
import reader.shared.android.Viewer
import reader.shared.android.model.ViewerChapter
import reader.shared.android.model.ViewerChapters
import reader.shared.android.model.ViewerPage
import reader.shared.android.navigation.ViewerNavigation.NavigationRegion
import kotlin.math.min

enum class PagerDirection {
    LTR,
    RTL,
    VERTICAL,
}

class SharedPagerViewer(
    private val host: ReaderViewerHost,
    private val direction: PagerDirection,
    private val config: ReaderRenderConfig,
) : Viewer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adapter = Adapter()
    private var currentItem: Any? = null
    private var chapters: ViewerChapters? = null

    val pager = Pager(host.context, isHorizontal = direction != PagerDirection.VERTICAL).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        offscreenPageLimit = 1
        isFocusable = false
        setBackgroundColor(config.backgroundColor)
        adapter = this@SharedPagerViewer.adapter
    }

    init {
        pager.addOnPageChangeListener(
            object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) {
                    if (!host.isScrollingThroughPages) host.hideMenu()
                    onPageChanged(position)
                }
            },
        )

        pager.tapListener = { event ->
            val position = IntArray(2)
            pager.getLocationOnScreen(position)
            val pos = PointF(
                (event.rawX - position[0]) / pager.width.coerceAtLeast(1),
                (event.rawY - position[1]) / pager.height.coerceAtLeast(1),
            )
            when (config.pagerNavigation.getAction(pos)) {
                NavigationRegion.MENU -> host.toggleMenu()
                NavigationRegion.NEXT -> moveToNext()
                NavigationRegion.PREV -> moveToPrevious()
                NavigationRegion.RIGHT -> moveRight()
                NavigationRegion.LEFT -> moveLeft()
            }
        }

        pager.longTapListener = {
            val item = adapter.items.getOrNull(pager.currentItem)
            if ((host.menuVisible || config.longTapEnabled) && item is ViewerPage) {
                host.onPageLongTap(item)
            } else {
                false
            }
        }
    }

    override fun getView(): View = pager

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters = chapters
        adapter.setChapters(chapters)

        val pages = chapters.currChapter.pages.orEmpty()
        if (pages.isNotEmpty()) {
            val requested = min(chapters.currChapter.requestedPage, pages.lastIndex)
            moveToPage(pages[requested], chapters.currChapter.requestedOffsetFraction)
        }
    }

    override fun moveToPage(page: ViewerPage, offsetFraction: Double) {
        val position = adapter.items.indexOf(page)
        if (position >= 0) {
            val previous = pager.currentItem
            pager.setCurrentItem(position, false)
            if (previous == position) onPageChanged(position)
        }
    }

    private fun onPageChanged(position: Int) {
        val item = adapter.items.getOrNull(position) ?: return
        if (item == currentItem) return
        val old = currentItem
        currentItem = item

        val page = item
        val forward = when (old) {
            is ViewerPage -> page.number >= old.number
            else -> true
        }
        host.onPageSelected(page, 0.0)
        pageHolder(page)?.onPageSelected(forward)

        val pages = page.chapter.pages.orEmpty()
        if (pages.size - page.number < PRELOAD_DISTANCE && page.chapter == chapters?.currChapter) {
            chapters?.nextChapter?.let(host::requestPreloadChapter)
        }
    }

    private fun pageHolder(page: ViewerPage): PageHolder? =
        pager.children.filterIsInstance<PageHolder>().firstOrNull { it.page == page }

    fun moveToNext() {
        if (direction == PagerDirection.RTL) moveLeft() else moveRight()
    }

    fun moveToPrevious() {
        if (direction == PagerDirection.RTL) moveRight() else moveLeft()
    }

    private fun moveRight() {
        if (pager.currentItem >= adapter.count - 1) return
        val holder = (currentItem as? ViewerPage)?.let(::pageHolder)
        if (holder != null && holder.canPanRight()) {
            holder.panRight()
        } else {
            pager.setCurrentItem(pager.currentItem + 1, config.pageTransitions)
        }
    }

    private fun moveLeft() {
        if (pager.currentItem <= 0) return
        val holder = (currentItem as? ViewerPage)?.let(::pageHolder)
        if (holder != null && holder.canPanLeft()) {
            holder.panLeft()
        } else {
            pager.setCurrentItem(pager.currentItem - 1, config.pageTransitions)
        }
    }

    private fun moveUp() = moveToPrevious()
    private fun moveDown() = moveToNext()

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || host.menuVisible) return false
                if (isUp) if (config.volumeKeysInverted) moveUp() else moveDown()
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || host.menuVisible) return false
                if (isUp) if (config.volumeKeysInverted) moveDown() else moveUp()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isUp) moveRight()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isUp) moveLeft()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) host.toggleMenu()
            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0 && event.action == MotionEvent.ACTION_SCROLL) {
            if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0f) moveDown() else moveUp()
            return true
        }
        return false
    }

    override fun destroy() {
        scope.cancel()
        adapter.destroyAll()
    }

    private inner class Adapter : ViewPagerAdapter() {
        var items: List<ViewerPage> = emptyList()
            private set

        fun setChapters(window: ViewerChapters) {
            items = assembleContinuousPagedWindow(
                previous = window.prevChapter?.pages,
                current = window.currChapter.pages.orEmpty(),
                next = window.nextChapter?.pages,
                reversed = direction == PagerDirection.RTL,
            )
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun createView(container: ViewGroup, position: Int): View = PageHolder(items[position])

        override fun getItemPosition(view: Any): Int {
            val item = (view as? PositionableView)?.item ?: return POSITION_NONE
            val position = items.indexOf(item)
            return if (position >= 0) position else POSITION_NONE
        }

        override fun destroyView(container: ViewGroup, position: Int, view: View) {
            (view as? PageHolder)?.destroy()
        }

        fun destroyAll() {
            pager.children.filterIsInstance<PageHolder>().forEach(PageHolder::destroy)
        }
    }

    private inner class PageHolder(
        val page: ViewerPage,
    ) : ReaderPageImageView(host.context), ViewPagerAdapter.PositionableView {
        override val item: Any get() = page
        private var loadJob: Job? = null
        private val spinner = ProgressBar(host.context).apply { isIndeterminate = true }
        private var errorMessage: TextView? = null

        init {
            setBackgroundColor(config.backgroundColor)
            addView(
                spinner,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            onScaleChanged = { host.hideMenu() }
            onImageLoaded = {
                spinner.visibility = View.GONE
                Log.i("YomiReader", "page-image-ready name=${page.displayName}")
            }
            onImageLoadError = { error -> showPageError(error) }
            startPageLoad()
        }

        private fun startPageLoad() {
            errorMessage?.let(::removeView)
            errorMessage = null
            spinner.visibility = View.VISIBLE
            loadJob?.cancel()
            loadJob = scope.launch {
                try {
                    val stream = withContext(Dispatchers.IO) { page.open() }
                    setImage(
                        stream,
                        Config(
                            zoomDurationMillis = config.zoomDurationMillis,
                            minimumScaleType = config.minimumScaleType,
                            cropBorders = config.cropBorders,
                            landscapeZoom = direction != PagerDirection.VERTICAL,
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    showPageError(error)
                }
            }
        }

        private fun showPageError(error: Throwable?) {
            Log.e("YomiReader", "page-image-failed name=${page.displayName}", error)
            spinner.visibility = View.GONE
            errorMessage?.let(::removeView)
            errorMessage = TextView(host.context).apply {
                text = "Unable to display this page. Tap to retry."
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(24, 24, 24, 24)
                setOnClickListener { startPageLoad() }
            }
            addView(
                errorMessage,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }

        fun destroy() {
            loadJob?.cancel()
            loadJob = null
            recycle()
        }
    }
}

private const val PRELOAD_DISTANCE = 5
