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
    private var currentPage = 0

    var onPageChanged: ((Int) -> Unit)? = null
    var onToggleMenu: (() -> Unit)? = null

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
                webtoonZoomOutDisabled

        this.pages = pages
        this.mode = mode
        this.cropBorders = cropBorders
        this.sidePadding = sidePadding
        this.webtoonDoubleTapZoom =
            webtoonDoubleTapZoom
        this.webtoonZoomOutDisabled =
            webtoonZoomOutDisabled
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
        val x =
            event.x /
                activePager.width.coerceAtLeast(1)
        val y =
            event.y /
                activePager.height.coerceAtLeast(1)

        if (vertical) {
            // Mihon's default LNavigation zones.
            when {
                y < 0.33f -> movePrevious()
                y > 0.66f -> moveNext()
                x < 0.33f -> movePrevious()
                x > 0.66f -> moveNext()
                else -> onToggleMenu?.invoke()
            }
        } else {
            // Mihon's default RightAndLeftNavigation zones.
            when {
                x < 0.33f -> moveLeft()
                x > 0.66f -> moveRight()
                else -> onToggleMenu?.invoke()
            }
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
        val recycler =
            MihonWebtoonRecyclerView(context).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    )
                isFocusable = false
                itemAnimator = null
                setItemViewCacheSize(3)
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

        recycler.tapListener = { event ->
            val x =
                event.x /
                    recycler.width.coerceAtLeast(1)
            val y =
                event.y /
                    recycler.originalHeight
                        .coerceAtLeast(1)

            // Mihon's default LNavigation zones.
            when {
                y < 0.33f ->
                    recycler.smoothScrollBy(
                        0,
                        -scrollDistance,
                    )
                y > 0.66f ->
                    recycler.smoothScrollBy(
                        0,
                        scrollDistance,
                    )
                x < 0.33f ->
                    recycler.smoothScrollBy(
                        0,
                        -scrollDistance,
                    )
                x > 0.66f ->
                    recycler.smoothScrollBy(
                        0,
                        scrollDistance,
                    )
                else -> onToggleMenu?.invoke()
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
            currentPage,
            0,
        )
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
            )
        }

        fun recycle() {
            pageView.recycle()
        }
    }
}
