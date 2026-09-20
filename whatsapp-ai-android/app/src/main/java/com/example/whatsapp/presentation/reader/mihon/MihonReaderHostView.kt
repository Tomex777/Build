package com.example.whatsapp.presentation.reader.mihon

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.NightMihonWebtoonLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

/*
 * Viewer switching/navigation is adapted from Mihon's PagerViewer,
 * PagerViewers and WebtoonViewer at revision
 * 424bbc53b85c19acd3c3b7c03ec6f73f516f25bc (Apache-2.0).
 */
internal class MihonReaderHostView(
    context: Context,
) : FrameLayout(context) {

    private var pages: List<MihonPageSpec> = emptyList()
    private var mode = MihonReadingMode.RIGHT_TO_LEFT
    private var cropBorders = false
    private var sidePadding = 0
    private var webtoonDoubleTapZoom = true
    private var webtoonZoomOutDisabled = false
    private var scaleType = MihonImageScaleType.FIT_SCREEN
    private var zoomStart = MihonZoomStart.AUTOMATIC
    private var landscapeZoom = true
    private var navigateToPan = true
    private var tapZone = MihonTapZone.RIGHT_AND_LEFT
    private var tapInvertMode = MihonTapInvertMode.NONE
    private var currentPage = 0

    var onPageChanged: ((Int) -> Unit)? = null
    var onToggleMenu: (() -> Unit)? = null
    var onLongTap: ((Int) -> Unit)? = null

    private var pager: MihonPager? = null
    private var webtoon: MihonWebtoonRecyclerView? = null

    fun configure(
        pages: List<MihonPageSpec>,
        mode: MihonReadingMode,
        currentPage: Int,
        cropBorders: Boolean,
        sidePadding: Int,
        webtoonDoubleTapZoom: Boolean,
        webtoonZoomOutDisabled: Boolean,
        scaleType: MihonImageScaleType,
        zoomStart: MihonZoomStart,
        landscapeZoom: Boolean,
        navigateToPan: Boolean,
        tapZone: MihonTapZone,
        tapInvertMode: MihonTapInvertMode,
    ) {
        val safePage =
            currentPage.coerceIn(
                0,
                (pages.size - 1).coerceAtLeast(0),
            )

        val structuralChange =
            this.pages != pages ||
                this.mode != mode ||
                this.cropBorders != cropBorders ||
                this.sidePadding != sidePadding ||
                this.webtoonDoubleTapZoom !=
                webtoonDoubleTapZoom ||
                this.webtoonZoomOutDisabled !=
                webtoonZoomOutDisabled ||
                this.scaleType != scaleType ||
                this.zoomStart != zoomStart ||
                this.landscapeZoom != landscapeZoom ||
                this.navigateToPan != navigateToPan ||
                this.tapZone != tapZone ||
                this.tapInvertMode != tapInvertMode

        this.pages = pages
        this.mode = mode
        this.cropBorders = cropBorders
        this.sidePadding = sidePadding
        this.webtoonDoubleTapZoom =
            webtoonDoubleTapZoom
        this.webtoonZoomOutDisabled =
            webtoonZoomOutDisabled
        this.scaleType = scaleType
        this.zoomStart = zoomStart
        this.landscapeZoom = landscapeZoom
        this.navigateToPan = navigateToPan
        this.tapZone = tapZone
        this.tapInvertMode = tapInvertMode
        this.currentPage = safePage

        if (structuralChange) {
            rebuild()
        } else {
            goToPage(safePage, smooth = false)
        }
    }

    fun goToPage(
        index: Int,
        smooth: Boolean = true,
    ) {
        if (pages.isEmpty()) return
        val safe = index.coerceIn(0, pages.lastIndex)
        currentPage = safe

        pager?.let { activePager ->
            val raw = rawPagerPosition(safe)
            if (activePager.currentItem != raw) {
                activePager.setCurrentItem(raw, smooth)
            }
        }

        webtoon?.let { activeRecycler ->
            (
                activeRecycler.layoutManager
                    as? NightMihonWebtoonLayoutManager
                )
                ?.scrollToPositionWithOffset(safe, 0)
        }
    }

    private fun rebuild() {
        pager?.adapter = null
        webtoon?.stopScroll()
        webtoon?.adapter = null
        removeAllViews()
        pager = null
        webtoon = null

        if (pages.isEmpty()) return

        if (
            mode == MihonReadingMode.WEBTOON ||
            mode ==
            MihonReadingMode.CONTINUOUS_VERTICAL
        ) {
            buildWebtoon()
        } else {
            buildPager()
        }
    }

    private fun buildPager() {
        val vertical =
            mode == MihonReadingMode.VERTICAL

        val newPager =
            MihonPager(
                context = context,
                isHorizontal = !vertical,
            ).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    )
                offscreenPageLimit = 1
                isFocusable = false
            }

        newPager.adapter =
            object : PagerAdapter() {
                override fun getCount(): Int =
                    pages.size

                override fun isViewFromObject(
                    view: View,
                    obj: Any,
                ): Boolean = view === obj

                override fun instantiateItem(
                    container: ViewGroup,
                    position: Int,
                ): Any {
                    val actual =
                        actualPagerIndex(position)
                    val page = pages[actual]
                    val holder =
                        MihonReaderPageView(
                            context,
                            isWebtoon = false,
                        ).apply {
                            tag = actual
                            onTap = onToggleMenu
                            bind(
                                page = page,
                                cropBorders = cropBorders,
                                sidePaddingPercent = 0,
                                scaleType = scaleType,
                                zoomStart = zoomStart,
                                readingMode = mode,
                                landscapeZoom = landscapeZoom,
                            )
                        }

                    container.addView(holder)
                    return holder
                }

                override fun destroyItem(
                    container: ViewGroup,
                    position: Int,
                    obj: Any,
                ) {
                    (obj as? MihonReaderPageView)
                        ?.destroy()
                    container.removeView(obj as View)
                }
            }

        newPager.addOnPageChangeListener(
            object :
                ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(
                    position: Int,
                ) {
                    val actual =
                        actualPagerIndex(position)
                    currentPage = actual
                    onPageChanged?.invoke(actual)
                }
            },
        )

        newPager.tapListener = { event ->
            handlePagerTap(
                newPager,
                event,
                vertical,
            )
        }
        newPager.longTapListener = {
            onLongTap?.invoke(currentPage)
            onLongTap != null
        }

        addView(newPager)
        pager = newPager
        newPager.setCurrentItem(
            rawPagerPosition(currentPage),
            false,
        )
    }

    private fun handlePagerTap(
        activePager: MihonPager,
        event: MotionEvent,
        vertical: Boolean,
    ) {
        val point =
            normalizedTapPoint(
                x = event.x /
                    activePager.width.coerceAtLeast(1),
                y = event.y /
                    activePager.height.coerceAtLeast(1),
            )

        when (
            tapAction(
                x = point.first,
                y = point.second,
                zone = tapZone,
            )
        ) {
            TapAction.PREVIOUS ->
                movePrevious()

            TapAction.NEXT ->
                moveNext()

            TapAction.LEFT ->
                if (vertical) {
                    movePrevious()
                } else {
                    moveLeft()
                }

            TapAction.RIGHT ->
                if (vertical) {
                    moveNext()
                } else {
                    moveRight()
                }

            TapAction.MENU ->
                onToggleMenu?.invoke()
        }
    }

    private fun currentPagerHolder():
        MihonReaderPageView? {
        val activePager = pager ?: return null
        for (index in 0 until activePager.childCount) {
            val child = activePager.getChildAt(index)
            if (
                child is MihonReaderPageView &&
                child.tag == currentPage
            ) {
                return child
            }
        }
        return null
    }

    private fun moveLeft() {
        val activePager = pager ?: return
        val holder = currentPagerHolder()

        if (
            navigateToPan &&
            holder != null &&
            holder.canPanLeft()
        ) {
            holder.panLeft()
        } else if (activePager.currentItem > 0) {
            activePager.setCurrentItem(
                activePager.currentItem - 1,
                true,
            )
        }
    }

    private fun moveRight() {
        val activePager = pager ?: return
        val holder = currentPagerHolder()

        if (
            navigateToPan &&
            holder != null &&
            holder.canPanRight()
        ) {
            holder.panRight()
        } else if (
            activePager.currentItem <
            pages.lastIndex
        ) {
            activePager.setCurrentItem(
                activePager.currentItem + 1,
                true,
            )
        }
    }

    fun moveNextByInput() {
        webtoon?.let { recycler ->
            recycler.smoothScrollBy(
                0,
                recycler.originalHeight.coerceAtLeast(height) * 3 / 4,
            )
            return
        }
        moveNext()
    }

    fun movePreviousByInput() {
        webtoon?.let { recycler ->
            recycler.smoothScrollBy(
                0,
                -(recycler.originalHeight.coerceAtLeast(height) * 3 / 4),
            )
            return
        }
        movePrevious()
    }

    private fun moveNext() {
        if (
            mode ==
            MihonReadingMode.RIGHT_TO_LEFT
        ) {
            moveLeft()
        } else {
            moveRight()
        }
    }

    private fun movePrevious() {
        if (
            mode ==
            MihonReadingMode.RIGHT_TO_LEFT
        ) {
            moveRight()
        } else {
            moveLeft()
        }
    }

    private fun actualPagerIndex(
        raw: Int,
    ): Int =
        if (
            mode ==
            MihonReadingMode.RIGHT_TO_LEFT
        ) {
            pages.lastIndex - raw
        } else {
            raw
        }

    private fun rawPagerPosition(
        actual: Int,
    ): Int =
        if (
            mode ==
            MihonReadingMode.RIGHT_TO_LEFT
        ) {
            pages.lastIndex - actual
        } else {
            actual
        }

    private fun buildWebtoon() {
        val initialPage = currentPage
        var acceptPageChanges = false

        val recycler =
            MihonWebtoonRecyclerView(context).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    )
                // Match Mihon: do not let RecyclerView run a layout pass
                // until its layout manager, adapter, start position and
                // frame parent are all ready.
                visibility = View.GONE
                isFocusable = false
                itemAnimator = null
                setItemViewCacheSize(4)
                doubleTapZoom =
                    webtoonDoubleTapZoom
                zoomOutDisabled =
                    webtoonZoomOutDisabled
            }

        val scrollDistance =
            resources.displayMetrics.heightPixels *
                3 / 4

        val manager =
            NightMihonWebtoonLayoutManager(
                context,
                scrollDistance,
            )

        recycler.layoutManager = manager

        val gap =
            if (
                mode ==
                MihonReadingMode
                    .CONTINUOUS_VERTICAL
            ) {
                (
                    15f *
                        resources.displayMetrics.density
                    ).toInt()
            } else {
                0
            }

        recycler.adapter =
            object :
                RecyclerView.Adapter<WebtoonHolder>() {
                override fun getItemCount(): Int =
                    pages.size

                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int,
                ): WebtoonHolder =
                    WebtoonHolder(
                        MihonReaderPageView(
                            parent.context,
                            isWebtoon = true,
                        ),
                    )

                override fun onBindViewHolder(
                    holder: WebtoonHolder,
                    position: Int,
                ) {
                    holder.bind(
                        page = pages[position],
                        cropBorders = cropBorders,
                        sidePadding = sidePadding,
                        gap = gap,
                    )
                }

                override fun onViewRecycled(
                    holder: WebtoonHolder,
                ) {
                    holder.recycle()
                }
            }

        recycler.longTapListener = { event ->
            val child =
                recycler.findChildViewUnder(
                    event.x,
                    event.y,
                )
            val position =
                child
                    ?.let {
                        recycler.getChildAdapterPosition(it)
                    }
                    ?.takeIf {
                        it != RecyclerView.NO_POSITION
                    }
                    ?: currentPage
            onLongTap?.invoke(position)
            onLongTap != null
        }

        recycler.tapListener = { event ->
            val point =
                normalizedTapPoint(
                    x = event.x /
                        recycler.width.coerceAtLeast(1),
                    y = event.y /
                        recycler.originalHeight
                            .coerceAtLeast(1),
                )

            when (
                tapAction(
                    x = point.first,
                    y = point.second,
                    zone = tapZone,
                )
            ) {
                TapAction.PREVIOUS,
                TapAction.LEFT,
                ->
                    recycler.smoothScrollBy(
                        0,
                        -scrollDistance,
                    )

                TapAction.NEXT,
                TapAction.RIGHT,
                ->
                    recycler.smoothScrollBy(
                        0,
                        scrollDistance,
                    )

                TapAction.MENU ->
                    onToggleMenu?.invoke()
            }
        }

        recycler.addOnScrollListener(
            object :
                RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    if (!acceptPageChanges) return

                    val first =
                        manager
                            .findFirstVisibleItemPosition()
                    if (
                        first !=
                        RecyclerView.NO_POSITION &&
                        first != currentPage
                    ) {
                        currentPage = first
                        onPageChanged?.invoke(first)
                    }
                }
            },
        )

        val frame =
            MihonWebtoonFrame(context).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    )
                doubleTapZoom =
                    webtoonDoubleTapZoom
                zoomOutDisabled =
                    webtoonZoomOutDisabled
                addView(
                    recycler,
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    ),
                )
            }

        addView(frame)
        webtoon = recycler
        manager.scrollToPositionWithOffset(
            initialPage,
            0,
        )

        // Keep initial RecyclerView callbacks from overwriting restored reading
        // progress with position 0 while the hidden webtoon is attaching.
        // Reveal first, let the requested start position win the first layout,
        // then begin publishing genuine user-driven page changes.
        recycler.postOnAnimation {
            if (webtoon === recycler && recycler.isAttachedToWindow) {
                recycler.visibility = View.VISIBLE
                recycler.postOnAnimation {
                    if (webtoon === recycler && recycler.isAttachedToWindow) {
                        acceptPageChanges = true
                        val first =
                            manager.findFirstVisibleItemPosition()
                        if (
                            first != RecyclerView.NO_POSITION &&
                            first != currentPage
                        ) {
                            currentPage = first
                            onPageChanged?.invoke(first)
                        }
                    }
                }
            }
        }
    }

    private fun normalizedTapPoint(
        x: Float,
        y: Float,
    ): Pair<Float, Float> =
        Pair(
            if (tapInvertMode.horizontal) 1f - x else x,
            if (tapInvertMode.vertical) 1f - y else y,
        )

    private fun tapAction(
        x: Float,
        y: Float,
        zone: MihonTapZone,
    ): TapAction =
        when (zone) {
            MihonTapZone.RIGHT_AND_LEFT ->
                when {
                    x < 0.33f -> TapAction.LEFT
                    x > 0.66f -> TapAction.RIGHT
                    else -> TapAction.MENU
                }

            MihonTapZone.L ->
                when {
                    y < 0.33f -> TapAction.PREVIOUS
                    y > 0.66f -> TapAction.NEXT
                    x < 0.33f -> TapAction.PREVIOUS
                    x > 0.66f -> TapAction.NEXT
                    else -> TapAction.MENU
                }

            MihonTapZone.KINDLISH ->
                when {
                    y < 0.33f -> TapAction.MENU
                    x < 0.33f -> TapAction.PREVIOUS
                    else -> TapAction.NEXT
                }

            MihonTapZone.EDGE ->
                when {
                    x < 0.33f || x > 0.66f ->
                        TapAction.NEXT
                    x in 0.33f..0.66f && y > 0.66f ->
                        TapAction.PREVIOUS
                    else ->
                        TapAction.MENU
                }

            MihonTapZone.DISABLED ->
                TapAction.MENU
        }

    private enum class TapAction {
        PREVIOUS,
        NEXT,
        LEFT,
        RIGHT,
        MENU,
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pager?.adapter = null
        webtoon?.adapter = null
    }

    private class WebtoonHolder(
        private val pageView: MihonReaderPageView,
    ) : RecyclerView.ViewHolder(pageView) {

        fun bind(
            page: MihonPageSpec,
            cropBorders: Boolean,
            sidePadding: Int,
            gap: Int,
        ) {
            pageView.bind(
                page = page,
                cropBorders = cropBorders,
                sidePaddingPercent = sidePadding,
                gapPx = gap,
                scaleType = MihonImageScaleType.FIT_WIDTH,
                zoomStart = MihonZoomStart.CENTER,
                readingMode = MihonReadingMode.WEBTOON,
                landscapeZoom = false,
            )
        }

        fun recycle() {
            pageView.recycle()
        }
    }
}
