/*
 * Reader viewer adapter for Sora, derived from Aniyomi/Mihon's reader architecture.
 *
 * Aniyomi: https://github.com/aniyomiorg/aniyomi
 * Licensed under the Apache License, Version 2.0.
 */
package com.night.sora.ui.reader

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.DirectionalViewPager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.night.sora.model.ReaderPage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * The same reading modes exposed by Aniyomi/Mihon's reader.
 */
enum class AniyomiReadingMode(val label: String) {
    LEFT_TO_RIGHT("Left to right"),
    RIGHT_TO_LEFT("Right to left"),
    VERTICAL("Vertical"),
    WEBTOON("Webtoon"),
    CONTINUOUS_VERTICAL("Continuous vertical"),
}

/**
 * Sora adapter around Aniyomi/Mihon's pager + webtoon viewer mechanics.
 *
 * Sora supplies already-resolved page URLs and owns progress persistence.
 * This view owns reader navigation and page rendering only.
 */
class AniyomiReaderView(context: Context) : FrameLayout(context) {
    private var pages: List<ReaderPage> = emptyList()
    private var mode: AniyomiReadingMode = AniyomiReadingMode.WEBTOON
    private var currentPageIndex = 0
    private var cropBorders = false
    private var onPageChanged: (Int) -> Unit = {}
    private var onTap: () -> Unit = {}

    private var pager: DirectionalViewPager? = null
    private var recycler: RecyclerView? = null

    fun setContent(
        pages: List<ReaderPage>,
        initialPage: Int,
        mode: AniyomiReadingMode,
        onPageChanged: (Int) -> Unit,
        onTap: () -> Unit,
    ) {
        val nextIndex = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val mustRebuild = this.pages != pages || this.mode != mode || childCount == 0

        this.pages = pages
        this.mode = mode
        this.onPageChanged = onPageChanged
        this.onTap = onTap

        if (mustRebuild) {
            currentPageIndex = nextIndex
            rebuild()
        } else if (currentPageIndex != nextIndex) {
            jumpTo(nextIndex, notify = false)
        }
    }

    fun setReadingMode(mode: AniyomiReadingMode) {
        if (this.mode == mode) return
        this.mode = mode
        rebuild()
    }

    fun setCropBorders(enabled: Boolean) {
        if (cropBorders == enabled) return
        cropBorders = enabled
        rebuild()
    }

    fun currentPage(): Int = currentPageIndex

    fun nextPage() = jumpTo(currentPageIndex + 1)

    fun previousPage() = jumpTo(currentPageIndex - 1)

    fun jumpTo(index: Int, notify: Boolean = true) {
        if (pages.isEmpty()) return
        val target = index.coerceIn(0, pages.lastIndex)
        currentPageIndex = target

        pager?.let { value ->
            val displayPosition = if (mode == AniyomiReadingMode.RIGHT_TO_LEFT) {
                pages.lastIndex - target
            } else {
                target
            }
            value.setCurrentItem(displayPosition, false)
        }
        recycler?.scrollToPosition(target)

        if (notify) onPageChanged(target)
    }

    private fun rebuild() {
        removeAllViews()
        pager = null
        recycler = null

        if (pages.isEmpty()) return

        when (mode) {
            AniyomiReadingMode.LEFT_TO_RIGHT,
            AniyomiReadingMode.RIGHT_TO_LEFT,
            AniyomiReadingMode.VERTICAL,
            -> buildPager()

            AniyomiReadingMode.WEBTOON,
            AniyomiReadingMode.CONTINUOUS_VERTICAL,
            -> buildWebtoon()
        }
    }

    private fun buildPager() {
        val isHorizontal = mode != AniyomiReadingMode.VERTICAL
        val viewPager = DirectionalViewPager(context, isHorizontal).apply {
            id = View.generateViewId()
            offscreenPageLimit = 1
            adapter = PageAdapter()
            addOnPageChangeListener(
                object : ViewPager.SimpleOnPageChangeListener() {
                    override fun onPageSelected(position: Int) {
                        val actual = if (mode == AniyomiReadingMode.RIGHT_TO_LEFT) {
                            pages.lastIndex - position
                        } else {
                            position
                        }.coerceIn(0, pages.lastIndex)

                        if (currentPageIndex != actual) {
                            currentPageIndex = actual
                            onPageChanged(actual)
                        }
                    }
                },
            )
        }

        pager = viewPager
        addView(viewPager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        jumpTo(currentPageIndex, notify = false)
    }

    private fun buildWebtoon() {
        val continuous = mode == AniyomiReadingMode.CONTINUOUS_VERTICAL
        val list = RecyclerView(context).apply {
            itemAnimator = null
            setItemViewCacheSize(4)
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = WebtoonAdapter(continuous)
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                        val index = lm.findFirstVisibleItemPosition()
                        if (index in pages.indices && index != currentPageIndex) {
                            currentPageIndex = index
                            onPageChanged(index)
                        }
                    }
                },
            )
        }

        recycler = list
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        list.scrollToPosition(currentPageIndex)
    }

    private inner class PageAdapter : PagerAdapter() {
        override fun getCount(): Int = pages.size

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val actual = if (mode == AniyomiReadingMode.RIGHT_TO_LEFT) {
                pages.lastIndex - position
            } else {
                position
            }
            val pageView = AniyomiReaderPageView(context, isWebtoon = false).apply {
                bind(pages[actual], onTap, cropBorders)
            }
            container.addView(
                pageView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            return pageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
            val view = item as AniyomiReaderPageView
            view.recycle()
            container.removeView(view)
        }

        override fun isViewFromObject(view: View, item: Any): Boolean = view === item
    }

    private inner class WebtoonAdapter(
        private val continuous: Boolean,
    ) : RecyclerView.Adapter<WebtoonHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebtoonHolder {
            val page = AniyomiReaderPageView(parent.context, isWebtoon = true)
            page.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = if (continuous) 0 else dp(15)
            }
            return WebtoonHolder(page)
        }

        override fun onBindViewHolder(holder: WebtoonHolder, position: Int) {
            holder.page.bind(pages[position], onTap, cropBorders)
        }

        override fun onViewRecycled(holder: WebtoonHolder) {
            holder.page.recycle()
        }

        override fun getItemCount(): Int = pages.size
    }

    private class WebtoonHolder(val page: AniyomiReaderPageView) : RecyclerView.ViewHolder(page)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/**
 * Sora-sized adaptation of Aniyomi's ReaderPageImageView.
 *
 * Large static manga pages are downloaded to Sora's cache and handed to
 * SubsamplingScaleImageView, preserving tiled decoding, pan limits and zoom.
 */
private class AniyomiReaderPageView(
    context: Context,
    private val isWebtoon: Boolean,
) : FrameLayout(context) {
    private val image = SubsamplingScaleImageView(context)
    private val progress = ProgressBar(context)
    private val error = TextView(context)
    private var boundKey: String? = null

    init {
        setBackgroundColor(Color.BLACK)

        image.apply {
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setMinimumScaleType(
                if (isWebtoon) {
                    SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
                } else {
                    SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                },
            )
        }

        addView(
            image,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                if (isWebtoon) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT,
            ),
        )

        addView(
            progress,
            LayoutParams(dp(42), dp(42), Gravity.CENTER),
        )

        error.apply {
            text = "Page failed to load"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        addView(
            error,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(180), Gravity.CENTER),
        )
    }

    fun bind(page: ReaderPage, onTap: () -> Unit, cropBorders: Boolean) {
        val key = buildKey(page)
        boundKey = key
        progress.visibility = View.VISIBLE
        error.visibility = View.GONE
        image.visibility = View.INVISIBLE
        image.recycle()
        image.setCropBorders(cropBorders)
        image.setOnClickListener { onTap() }

        image.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    if (boundKey != key) return
                    val base = image.scale.coerceAtLeast(0.01f)
                    image.maxScale = base * 5f
                    image.setDoubleTapZoomScale(base * 2f)
                    image.setDoubleTapZoomDuration(250)
                    image.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                }

                override fun onImageLoadError(e: Exception) {
                    if (boundKey != key) return
                    progress.visibility = View.GONE
                    error.visibility = View.VISIBLE
                }
            },
        )

        PAGE_EXECUTOR.execute {
            runCatching { cachedPage(page, key) }
                .onSuccess { file ->
                    MAIN.post {
                        if (boundKey != key) return@post
                        image.setImage(ImageSource.uri(context, Uri.fromFile(file)))
                    }
                }
                .onFailure {
                    MAIN.post {
                        if (boundKey != key) return@post
                        progress.visibility = View.GONE
                        error.visibility = View.VISIBLE
                    }
                }
        }
    }

    fun recycle() {
        boundKey = null
        image.recycle()
    }

    private fun cachedPage(page: ReaderPage, key: String): File {
        val dir = File(context.cacheDir, "aniyomi-reader-pages").apply { mkdirs() }
        val file = File(dir, key)
        if (file.exists() && file.length() > 0L) return file

        val temp = File(dir, "$key.part")
        val connection = URL(page.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        page.headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                connection.setRequestProperty(name, value)
            }
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Reader page HTTP $code")
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            return file
        } finally {
            connection.disconnect()
        }
    }

    private fun buildKey(page: ReaderPage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val headerKey = page.headers.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        val bytes = digest.digest("${page.url}|$headerKey".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val PAGE_EXECUTOR = Executors.newFixedThreadPool(3)
        private val MAIN = Handler(Looper.getMainLooper())
    }
}
