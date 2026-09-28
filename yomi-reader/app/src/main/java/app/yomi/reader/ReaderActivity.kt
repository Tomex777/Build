package app.yomi.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.yomi.reader.core.ReaderBook
import app.yomi.reader.core.ReaderChapter
import app.yomi.reader.core.ReaderChapterId
import app.yomi.reader.core.ReaderLocation
import app.yomi.reader.core.ReaderPageSource
import app.yomi.reader.core.ReadingMode
import app.yomi.reader.local.LibraryAvailability
import app.yomi.reader.local.LocalBookIdentityStore
import app.yomi.reader.local.LocalChapterBinding
import app.yomi.reader.local.LocalLibraryStore
import app.yomi.reader.local.SharedPreferencesProgressSink
import app.yomi.reader.local.TreeBookCatalog
import app.yomi.reader.local.ZipDocumentPageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import reader.shared.android.ReaderRenderConfig
import reader.shared.android.ReaderViewerHost
import reader.shared.android.Viewer
import reader.shared.android.model.ViewerChapter
import reader.shared.android.model.ViewerChapters
import reader.shared.android.model.ViewerPage
import reader.shared.android.pager.PagerDirection
import reader.shared.android.pager.SharedPagerViewer
import reader.shared.android.webtoon.SharedWebtoonViewer

class ReaderActivity : ComponentActivity(), ReaderViewerHost {
    override val context: Context get() = this
    override var menuVisible: Boolean = true
        private set

    private lateinit var root: FrameLayout
    private lateinit var controls: LinearLayout
    private lateinit var positionLabel: TextView
    private var viewer: Viewer? = null
    private val pageSources = mutableListOf<ReaderPageSource>()
    private var viewerChapters: List<ViewerChapter> = emptyList()
    private var activeChapterIndex = 0
    private var chapterPromotionJob: Job? = null
    private var title: String = "Book"
    private var mode: ReadingMode = ReadingMode.LTR_PAGED
    private val progressSink by lazy { SharedPreferencesProgressSink(this) }
    private val identityStore by lazy { LocalBookIdentityStore(this) }
    private val libraryStore by lazy { LocalLibraryStore(this) }
    private var book: ReaderBook? = null
    private var lastLocation: ReaderLocation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        readIntentReaderState()
        controls = buildControls()
        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        applyImmersive()
        openIntentBook()
    }

    private fun readIntentReaderState() {
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Book"
        mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
            ?: loadMode()
    }

    private fun openIntentBook() {
        val uriString = intent.getStringExtra(EXTRA_URI)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: "archive"
        if (uriString.isNullOrBlank()) {
            showFatal("Missing local book URI")
            return
        }

        val uri = Uri.parse(uriString)
        val bookId = identityStore.getOrCreate(uri)
        book = ReaderBook(bookId, title)
        libraryStore.markOpened(bookId)

        lifecycleScope.launch {
            try {
                val bindings = withContext(Dispatchers.IO) {
                    if (kind == "folder") {
                        TreeBookCatalog(contentResolver, uri).chapters(bookId, title)
                    } else {
                        val chapter = ReaderChapter(
                            id = ReaderChapterId(bookId.value + "#root"),
                            bookId = bookId,
                            title = title,
                            order = 0,
                        )
                        listOf(LocalChapterBinding(chapter, ZipDocumentPageSource(contentResolver, uri)))
                    }
                }
                require(bindings.isNotEmpty()) { "No supported images or chapters found" }

                pageSources.clear()
                pageSources += bindings.map { it.source }
                viewerChapters = bindings.map { ViewerChapter(it.chapter, it.source) }

                val restored = progressSink.restore(bookId)
                activeChapterIndex = restored
                    ?.let { location -> viewerChapters.indexOfFirst { it.chapter.id == location.chapterId } }
                    ?.takeIf { it >= 0 }
                    ?: 0

                val current = viewerChapters[activeChapterIndex]
                if (restored != null && restored.chapterId == current.chapter.id) {
                    current.requestedPage = restored.pageIndex
                    current.requestedOffsetFraction = restored.pageOffsetFraction
                    lastLocation = restored
                }

                prepareWindow(activeChapterIndex)
                require(current.pages?.isNotEmpty() == true) { "No supported images found" }
                libraryStore.setAvailability(bookId, LibraryAvailability.AVAILABLE)
                installViewer()
            } catch (t: Throwable) {
                libraryStore.setAvailability(
                    bookId,
                    if (t is SecurityException) LibraryAvailability.PERMISSION_LOST else LibraryAvailability.UNAVAILABLE,
                )
                showFatal(t.message ?: "Unable to open local book")
            }
        }
    }

    private suspend fun prepareWindow(index: Int) {
        withContext(Dispatchers.IO) {
            viewerChapters.getOrNull(index - 1)?.load()
            viewerChapters.getOrNull(index)?.load()
            viewerChapters.getOrNull(index + 1)?.load()
        }
    }

    private fun window(index: Int = activeChapterIndex): ViewerChapters {
        return ViewerChapters(
            currChapter = viewerChapters[index],
            prevChapter = viewerChapters.getOrNull(index - 1),
            nextChapter = viewerChapters.getOrNull(index + 1),
        )
    }

    private fun installViewer() {
        if (viewerChapters.isEmpty()) return
        viewer?.destroy()
        viewer?.getView()?.let(root::removeView)

        val renderConfig = ReaderRenderConfig(
            cropBorders = getPreferences(MODE_PRIVATE).getBoolean(PREF_CROP, false),
            backgroundColor = backgroundColor(),
            volumeKeysEnabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_VOLUME_KEYS, false),
            pageTransitions = true,
        )

        viewer = when (mode) {
            ReadingMode.LTR_PAGED -> SharedPagerViewer(this, PagerDirection.LTR, renderConfig)
            ReadingMode.RTL_PAGED -> SharedPagerViewer(this, PagerDirection.RTL, renderConfig)
            ReadingMode.VERTICAL_PAGED -> SharedPagerViewer(this, PagerDirection.VERTICAL, renderConfig)
            ReadingMode.WEBTOON -> SharedWebtoonViewer(this, renderConfig, isContinuous = true)
        }

        root.addView(
            viewer!!.getView(),
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        viewer!!.setChapters(window())
        updatePositionLabel(lastLocation)
        controls.visibility = if (menuVisible) View.VISIBLE else View.GONE
    }

    override fun hideMenu() {
        menuVisible = false
        controls.visibility = View.GONE
        applyImmersive()
    }

    override fun showMenu() {
        menuVisible = true
        controls.visibility = View.VISIBLE
        applyImmersive()
    }

    override fun toggleMenu() {
        if (menuVisible) hideMenu() else showMenu()
    }

    override fun onPageSelected(page: ViewerPage, pageOffsetFraction: Double) {
        val currentBook = book ?: return
        page.chapter.requestedPage = page.index
        page.chapter.requestedOffsetFraction = pageOffsetFraction

        val chapterIndex = viewerChapters.indexOf(page.chapter).takeIf { it >= 0 } ?: activeChapterIndex
        val pageCount = page.chapter.pages?.size?.coerceAtLeast(1) ?: 1
        val chapterFraction = ((page.index + pageOffsetFraction) / pageCount.toDouble()).coerceIn(0.0, 1.0)
        val overall = ((chapterIndex + chapterFraction) / viewerChapters.size.coerceAtLeast(1).toDouble())
            .coerceIn(0.0, 1.0)

        val location = ReaderLocation(
            bookId = currentBook.id,
            chapterId = page.chapter.chapter.id,
            pageIndex = page.index,
            pageOffsetFraction = pageOffsetFraction,
            overallProgress = overall,
        )
        lastLocation = location
        updatePositionLabel(location)

        lifecycleScope.launch(Dispatchers.IO) {
            progressSink.onLocationChanged(location)
        }

        if (chapterIndex != activeChapterIndex) {
            activeChapterIndex = chapterIndex
            chapterPromotionJob?.cancel()
            chapterPromotionJob = lifecycleScope.launch {
                try {
                    prepareWindow(chapterIndex)
                    if (activeChapterIndex == chapterIndex) {
                        viewer?.setChapters(window(chapterIndex))
                    }
                } catch (_: Throwable) {
                    // Keep the current loaded page visible; the next interaction can retry.
                }
            }
        }
    }

    override fun onPageLongTap(page: ViewerPage): Boolean {
        showMenu()
        return true
    }

    override fun requestPreloadChapter(chapter: ViewerChapter) {
        if (chapter.pages != null) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { chapter.load() }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return viewer?.handleKeyEvent(event) == true || super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return viewer?.handleGenericMotionEvent(ev) == true || super.dispatchGenericMotionEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        lastLocation?.let { location ->
            lifecycleScope.launch(Dispatchers.IO) {
                progressSink.onSessionClosed(location)
            }
        }
    }

    override fun onDestroy() {
        chapterPromotionJob?.cancel()
        viewer?.destroy()
        pageSources.distinct().forEach { source -> runCatching { source.close() } }
        pageSources.clear()
        super.onDestroy()
    }

    private fun buildControls(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(18))
            setBackgroundColor(0xEE111722.toInt())

            addView(
                TextView(this@ReaderActivity).apply {
                    text = title
                    textSize = 17f
                    setTextColor(Color.WHITE)
                },
            )

            positionLabel = TextView(this@ReaderActivity).apply {
                textSize = 13f
                setTextColor(0xFFB7C2D5.toInt())
                setPadding(0, dp(4), 0, dp(8))
            }
            addView(positionLabel)

            addView(
                LinearLayout(this@ReaderActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addModeButton("LTR", ReadingMode.LTR_PAGED)
                    addModeButton("RTL", ReadingMode.RTL_PAGED)
                    addModeButton("Vertical", ReadingMode.VERTICAL_PAGED)
                    addModeButton("Webtoon", ReadingMode.WEBTOON)
                },
            )
        }
    }

    private fun LinearLayout.addModeButton(label: String, target: ReadingMode) {
        addView(
            TextView(this@ReaderActivity).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(if (mode == target) 0xFFD4E4FF.toInt() else 0xFF98A4B7.toInt())
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener {
                    if (mode != target) {
                        mode = target
                        saveMode(target)
                        installViewer()
                    }
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun updatePositionLabel(location: ReaderLocation?) {
        if (!::positionLabel.isInitialized) return
        val chapter = location
            ?.let { saved -> viewerChapters.firstOrNull { it.chapter.id == saved.chapterId } }
            ?: viewerChapters.getOrNull(activeChapterIndex)
        val total = chapter?.pages?.size ?: 0
        positionLabel.text = if (location == null || total == 0) {
            title
        } else {
            val percent = (location.overallProgress * 100).toInt().coerceIn(0, 100)
            val chapterPart = if (viewerChapters.size > 1) chapter?.chapter?.title + " · " else ""
            chapterPart + "page " + (location.pageIndex + 1) + "/" + total + " · " + percent + "%"
        }
    }

    private fun applyImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (menuVisible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun backgroundColor(): Int {
        return when (getPreferences(MODE_PRIVATE).getString(PREF_BACKGROUND, "black")) {
            "light" -> 0xFFF5F3EF.toInt()
            "dark" -> 0xFF15171B.toInt()
            else -> Color.BLACK
        }
    }

    private fun loadMode(): ReadingMode {
        return getPreferences(MODE_PRIVATE)
            .getString(PREF_MODE, ReadingMode.LTR_PAGED.name)
            ?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
            ?: ReadingMode.LTR_PAGED
    }

    private fun saveMode(value: ReadingMode) {
        getPreferences(MODE_PRIVATE).edit().putString(PREF_MODE, value.name).apply()
    }

    private fun showFatal(message: String) {
        root.removeAllViews()
        root.addView(
            TextView(this).apply {
                text = "Yomi could not open this book.\n\n" + message
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(28), dp(28), dp(28))
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MODE = "mode"

        private const val PREF_MODE = "reader_mode"
        private const val PREF_CROP = "crop_borders"
        private const val PREF_VOLUME_KEYS = "volume_keys"
        private const val PREF_BACKGROUND = "background"

        fun newIntent(context: Context, uri: String, kind: String, title: String): Intent {
            return Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_TITLE, title)
        }
    }
}
