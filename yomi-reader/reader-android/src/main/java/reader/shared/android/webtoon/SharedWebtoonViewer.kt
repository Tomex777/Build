package reader.shared.android.webtoon

import android.graphics.PointF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
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
import reader.shared.android.model.ViewerChapters
import reader.shared.android.model.ViewerPage
import reader.shared.android.navigation.ViewerNavigation.NavigationRegion
import kotlin.math.max

class SharedWebtoonViewer(
    private val host: ReaderViewerHost,
    private val config: ReaderRenderConfig,
    private val isContinuous: Boolean = true,
) : Viewer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scrollDistance = host.context.resources.displayMetrics.heightPixels * 3 / 4
    private val layoutManager = WebtoonLayoutManager(host.context, scrollDistance)
    private val adapter = Adapter()
    private var chapters: ViewerChapters? = null
    private var currentPage: ViewerPage? = null

    val recycler = WebtoonRecyclerView(host.context).apply {
        setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        isFocusable = false
        itemAnimator = null
        setBackgroundColor(config.backgroundColor)
        layoutManager = this@SharedWebtoonViewer.layoutManager
        adapter = this@SharedWebtoonViewer.adapter
        doubleTapZoom = config.doubleTapZoom
        zoomOutDisabled = config.webtoonZoomOutDisabled
    }

    private val frame = WebtoonFrame(host.context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        doubleTapZoom = config.doubleTapZoom
        zoomOutDisabled = config.webtoonZoomOutDisabled
        addView(recycler)
    }

    init {
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (kotlin.math.abs(dy) > host.context.resources.displayMetrics.density * 8 && host.menuVisible) {
                        host.hideMenu()
                    }
                    updatePosition()
                }
            },
        )

        recycler.tapListener = { event ->
            val position = IntArray(2)
            recycler.getLocationOnScreen(position)
            val pos = PointF(
                (event.rawX - position[0]) / recycler.width.coerceAtLeast(1),
                (event.rawY - position[1]) / recycler.originalHeight.coerceAtLeast(1),
            )
            when (config.webtoonNavigation.getAction(pos)) {
                NavigationRegion.MENU -> host.toggleMenu()
                NavigationRegion.NEXT, NavigationRegion.RIGHT -> scrollDown()
                NavigationRegion.PREV, NavigationRegion.LEFT -> scrollUp()
            }
        }

        recycler.longTapListener = { event ->
            val child = recycler.findChildViewUnder(event.x, event.y)
            val position = child?.let(recycler::getChildAdapterPosition) ?: RecyclerView.NO_POSITION
            val item = adapter.items.getOrNull(position)
            if ((host.menuVisible || config.longTapEnabled) && item is ViewerPage) {
                host.onPageLongTap(item)
            } else {
                false
            }
        }
    }

    override fun getView(): View = frame

    override fun setChapters(chapters: ViewerChapters) {
        this.chapters = chapters
        adapter.setChapters(chapters)
        val pages = chapters.currChapter.pages.orEmpty()
        if (pages.isNotEmpty()) {
            val index = chapters.currChapter.requestedPage.coerceIn(0, pages.lastIndex)
            moveToPage(pages[index], chapters.currChapter.requestedOffsetFraction)
        }
    }

    override fun moveToPage(page: ViewerPage, offsetFraction: Double) {
        val position = adapter.items.indexOf(page)
        if (position < 0) return
        layoutManager.scrollToPositionWithOffset(position, 0)
        recycler.post {
            val child = layoutManager.findViewByPosition(position)
            if (child != null && offsetFraction > 0.0) {
                recycler.scrollBy(0, (child.height * offsetFraction).toInt())
            }
            updatePosition()
        }
    }

    private fun updatePosition() {
        val first = layoutManager.findFirstVisibleItemPosition()
        val firstPagePosition = (first until adapter.items.size).firstOrNull {
            adapter.items.getOrNull(it) is ViewerPage && layoutManager.findViewByPosition(it) != null
        } ?: return
        val page = adapter.items[firstPagePosition] as ViewerPage
        val child = layoutManager.findViewByPosition(firstPagePosition)
        val offset = if (child == null || child.height <= 0) 0.0 else {
            (-child.top).toDouble().div(child.height.toDouble()).coerceIn(0.0, 1.0)
        }

        if (page != currentPage || offset > 0.0) {
            currentPage = page
            host.onPageSelected(page, offset)
        }

        val pages = page.chapter.pages.orEmpty()
        if (pages.size - page.number < PRELOAD_DISTANCE && page.chapter == chapters?.currChapter) {
            chapters?.nextChapter?.let(host::requestPreloadChapter)
        }

        val last = layoutManager.findLastEndVisibleItemPosition()
        val transition = adapter.items.getOrNull(last)
        if (transition is ChapterTransition) {
            transition.to?.let(host::requestPreloadChapter)
        }
    }

    private fun scrollUp() {
        if (config.pageTransitions) recycler.smoothScrollBy(0, -scrollDistance) else recycler.scrollBy(0, -scrollDistance)
    }

    private fun scrollDown() {
        if (config.pageTransitions) recycler.smoothScrollBy(0, scrollDistance) else recycler.scrollBy(0, scrollDistance)
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || host.menuVisible) return false
                if (isUp) if (config.volumeKeysInverted) scrollUp() else scrollDown()
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || host.menuVisible) return false
                if (isUp) if (config.volumeKeysInverted) scrollDown() else scrollUp()
            }
            KeyEvent.KEYCODE_MENU -> if (isUp) host.toggleMenu()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> if (isUp) scrollUp()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) scrollDown()
            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean = false

    override fun destroy() {
        scope.cancel()
        adapter.destroyAll()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<Any> = emptyList()
            private set

        fun setChapters(window: ViewerChapters) {
            val newItems = mutableListOf<Any>()
            window.prevChapter?.pages?.let {
                newItems.addAll(it)
                newItems.add(ChapterTransition.Prev(window.currChapter, window.prevChapter))
            }
            newItems.addAll(window.currChapter.pages.orEmpty())
            window.nextChapter?.let {
                newItems.add(ChapterTransition.Next(window.currChapter, it))
                newItems.addAll(it).pages.orEmpty()
            }
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int = if (items[position] is ViewerPage) PAGE_VIEW else BOUNDARY_VIEW

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == PAGE_VIEW) PageHolder() else BoundaryHolder()

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is PageHolder -> holder.bind(items[position] as ViewerPage)
                is BoundaryHolder -> holder.bind(items[position] as ChapterTransition)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is PageHolder) holder.recyclePage()
        }

        fun destroyAll() {
            for (i in 0 until recycler.childCount) {
                recycler.getChildViewHolder(recycler.getChildAt(i)).let {
                    if (it is PageHolder) it.recyclePage()
                }
            }
        }
    }

    private inner class PageHolder : RecyclerView.ViewHolder(
        ReaderPageImageView(host.context, isWebtoon = true),
    ) {
        private val image = itemView as ReaderPageImageView
        private var loadJob: Job? = null

        init {
            image.setBackgroundColor(config.backgroundColor)
            image.onScaleChanged = { host.hideMenu() }
            image.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        fun bind(page: ViewerPage) {
            recyclePage()
            val sideMargin = (host.context.resources.displayMetrics.widthPixels * (config.webtoonSidePaddingPercent / 100f)).toInt()
            (image.layoutParams as RecyclerView.LayoutParams).apply {
                marginStart = sideMargin
                marginEnd = sideMargin
                bottomMargin = if (isContinuous) 0 else (15 * host.context.resources.displayMetrics.density).toInt()
            }
            loadJob = scope.launch {
                val stream = withContext(Dispatchers.IO) { page.open() }
                image.setImage(
                    stream,
                    ReaderPageImageView.Config(
                        zoomDurationMillis = config.zoomDurationMillis,
                        minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                        cropBorders = config.cropBorders,
                    ),
                )
            }
        }

        fun recyclePage() {
            loadJob?.cancel()
            loadJob = null
            image.recycle()
        }
    }

    private inner class BoundaryHolder : RecyclerView.ViewHolder(
        FrameLayout(host.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setBackgroundColor(config.backgroundColor)
        },
    ) {
        fun bind(transition: ChapterTransition) {
            val root = itemView as FrameLayout
            root.removeAllViews()
            root.addView(
                TextView(host.context).apply {
                    text = transition.to?.chapter?.title ?: "End"
                    textSize = 13f
                    setTextColor(0xFF9EA8B7.toInt())
                    val v = (12 * resources.displayMetrics.density).toInt()
                    val h = (20 * resources.displayMetrics.density).toInt()
                    setPadding(h, v, h, v)
                },
            )
        }
    }
}

private const val PAGE_VIEW = 0
private const val BOUNDARY_VIEW = 1
private const val PRELOAD_DISTANCE = 5
private const val RECYCLER_VIEW_CACHE_SIZE = 4
