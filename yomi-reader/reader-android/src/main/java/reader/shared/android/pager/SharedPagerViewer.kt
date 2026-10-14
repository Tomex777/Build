package reader.shared.android.pager

import android.graphics.PointF
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import reader.shared.android.model.ChapterTransition
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
    // Mihon's pager defers chapter-window replacement until it is idle.
    // Changing a ViewPager adapter during a swipe causes visible page jumps.
    private var isPagerIdle = true
    private var pendingChapters: ViewerChapters? = null
    private var changingChapterWindow = false

    val pager = Pager(host.context, isHorizontal = direction != PagerDirection.VERTICAL).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        offscreenPageLimit = 1
        isFocusable = false
        setBackgroundColor(config.backgroundColor)
        adapter = this@SharedPagerViewer.adapter
    }

    private val pagerListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            if (changingChapterWindow) return
            if (!host.isScrollingThroughPages) host.hideMenu()
            onPageChanged(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            isPagerIdle = state == ViewPager.SCROLL_STATE_IDLE
            if (isPagerIdle) {
                pendingChapters?.let { pending ->
                    pendingChapters = null
                    setChaptersInternal(pending)
                }
            }
        }
    }

    init {
        pager.addOnPageChangeListener(pagerListener)

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
        if (isPagerIdle) {
            setChaptersInternal(chapters)
        } else {
            pendingChapters = chapters
        }
    }

    private fun setChaptersInternal(window: ViewerChapters) {
        val original = currentItem
        // Adjacent page loads update the same chapter's window. Preserve the
        // visible page in that case, but do NOT preserve a page in the old
        // chapter when the user explicitly selects a different chapter.
        val sameChapter = chapters?.currChapter == window.currChapter
        pager.removeOnPageChangeListener(pagerListener)
        changingChapterWindow = true
        try {
            this.chapters = window
            adapter.setChapters(window)
            val pages = window.currChapter.pages.orEmpty()
            if (pages.isNotEmpty()) {
                val requested = min(window.currChapter.requestedPage, pages.lastIndex)
                // Keep the actually selected page when adjacent chapters finish
                // loading; otherwise use the restored/chapter-requested position.
                val target = original?.takeIf { sameChapter && it in adapter.items } ?: pages[requested]
                val position = adapter.items.indexOf(target)
                if (position >= 0) pager.setCurrentItem(position, false)
            }
        } finally {
            changingChapterWindow = false
            pager.addOnPageChangeListener(pagerListener)
        }
        onPageChanged(pager.currentItem)
    }

    override fun moveToPage(page: ViewerPage, offsetFraction: Double) {
        val position = adapter.items.indexOf(page)
        if (position >= 0) {
            val previous = pager.currentItem
            pager.setCurrentItem(position, config.pageTransitions)
            if (previous == position) onPageChanged(position)
        }
    }

    private fun onPageChanged(position: Int) {
        val item = adapter.items.getOrNull(position) ?: return
        if (item == currentItem) return
        val old = currentItem
        currentItem = item

        when (item) {
            is ViewerPage -> {
                val forward = when (old) {
                    is ViewerPage -> item.number >= old.number
                    else -> true
                }
                host.onPageSelected(item, 0.0)
                pageHolder(item)?.onPageSelected(forward)

                val pages = item.chapter.pages.orEmpty()
                if (pages.size - item.number < PRELOAD_DISTANCE && item.chapter == chapters?.currChapter) {
                    chapters?.nextChapter?.let(host::requestPreloadChapter)
                }
            }
            is ChapterTransition -> {
                // Match Mihon's transition-page preloader; the host publishes
                // an updated page window as soon as its local chapter is ready.
                item.to?.let(host::requestPreloadChapter)
                if (item is ChapterTransition.Next && item.to == null) {
                    host.showMenu()
                }
            }
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
        var items: List<Any> = emptyList()
            private set

        fun setChapters(window: ViewerChapters) {
            val previous = window.prevChapter
            val next = window.nextChapter
            val forceTransition = config.alwaysShowChapterTransition || currentItem is ChapterTransition
            val newItems = mutableListOf<Any>()
            previous?.pages?.let(newItems::addAll)
            if (forceTransition || previous?.pages == null) {
                newItems.add(ChapterTransition.Prev(window.currChapter, previous))
            }
            newItems.addAll(window.currChapter.pages.orEmpty())
            if (forceTransition || next?.pages == null) {
                newItems.add(ChapterTransition.Next(window.currChapter, next))
            }
            next?.pages?.let(newItems::addAll)
            if (direction == PagerDirection.RTL) newItems.reverse()
            items = newItems
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun createView(container: ViewGroup, position: Int): View =
            when (val item = items[position]) {
                is ViewerPage -> PageHolder(item)
                is ChapterTransition -> TransitionHolder(item)
                else -> error("Unsupported reader page type")
            }

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

    private inner class TransitionHolder(
        val transition: ChapterTransition,
    ) : FrameLayout(host.context), ViewPagerAdapter.PositionableView {
        override val item: Any get() = transition

        init {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(config.backgroundColor)
            val content = LinearLayout(host.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            content.addView(
                host.createChapterTransitionView(transition),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            if (transition.to?.pages == null && transition.to != null) {
                content.addView(ProgressBar(host.context).apply { isIndeterminate = true })
                content.addView(TextView(host.context).apply {
                    text = "Loading chapter…"
                    setTextColor(0xFFCFD8E5.toInt())
                    gravity = Gravity.CENTER
                })
            }
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
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
                            zoomStartPosition = config.zoomStartPosition,
                            landscapeZoom = config.landscapeZoom && direction != PagerDirection.VERTICAL,
                        ),
                        isAnimated = page.isAnimated,
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
