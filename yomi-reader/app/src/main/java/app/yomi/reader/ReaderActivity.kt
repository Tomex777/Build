package app.yomi.reader

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
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
    private lateinit var topControls: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var positionLabel: TextView
    private val hideChromeRunnable = Runnable {
        if (!isFinishing && menuVisible) hideMenu()
    }
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
    private val keepChromeVisibleForCi by lazy {
        (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "acceptance") && intent.getBooleanExtra(EXTRA_CI_KEEP_CHROME, false)
    }
    private val openSettingsForCi by lazy {
        (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "acceptance") && intent.getBooleanExtra(EXTRA_CI_OPEN_SETTINGS, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        readIntentReaderState()
        Log.i(READER_TAG, "activity-created title=$title mode=$mode")
        topControls = buildTopControls()
        controls = buildControls()
        root.addView(
            topControls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                topMargin = dp(12)
            },
        )
        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(12)
            },
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            (topControls.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = bars.top + dp(8)
                topControls.layoutParams = params
            }
            (controls.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.bottomMargin = bars.bottom + dp(10)
                controls.layoutParams = params
            }
            insets
        }
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
            showFatal("The book link is missing.")
            return
        }

        val uri = Uri.parse(uriString)
        if (kind == "folder" && (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { error ->
                // A library launch normally already has this grant from the picker. A
                // direct share can still be readable for this session without persistence.
                Log.w(READER_TAG, "tree-grant-not-persisted uri=$uri", error)
            }
        }
        val bookId = identityStore.getOrCreate(uri)
        book = ReaderBook(bookId, title)
        libraryStore.markOpened(bookId)

        lifecycleScope.launch {
            try {
                val bindings = withContext(Dispatchers.IO) {
                    if (kind == "folder") {
                        Log.i(READER_TAG, "tree-discovery-start uri=$uri")
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
                Log.i(READER_TAG, "book-structure-loaded title=$title chapters=${bindings.size}")
                require(bindings.isNotEmpty()) { "No supported images or chapters found" }

                pageSources.clear()
                pageSources.addAll(bindings.map { it.source })
                viewerChapters = bindings.map { ViewerChapter(it.chapter, it.source) }

                val restored = progressSink.restore(bookId)
                Log.i(
                    READER_TAG,
                    "progress-restored title=$title chapter=${restored?.chapterId?.value ?: "none"} page=${restored?.pageIndex?.plus(1) ?: 0}",
                )
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
                Log.i(
                    READER_TAG,
                    "chapter-window-ready title=$title chapters=${viewerChapters.size} currentPages=${current.pages?.size ?: 0}",
                )
                require(current.pages?.isNotEmpty() == true) { "No supported images found" }
                libraryStore.setAvailability(bookId, LibraryAvailability.AVAILABLE)
                installViewer()
            } catch (t: Throwable) {
                Log.e(READER_TAG, "open-book-failed title=$title kind=$kind uri=$uriString", t)
                libraryStore.setAvailability(
                    bookId,
                    if (t is SecurityException) LibraryAvailability.PERMISSION_LOST else LibraryAvailability.UNAVAILABLE,
                )
                showFatal(
                    if (t is SecurityException) {
                        "Yomi no longer has access to this book. Add it again from Home."
                    } else {
                        "Check that the book is still available, then try opening it again."
                    },
                )
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
        val chromeVisibility = if (menuVisible) View.VISIBLE else View.GONE
        topControls.visibility = chromeVisibility
        controls.visibility = chromeVisibility
        if (menuVisible) scheduleChromeHide()
        Log.i(READER_TAG, "viewer-installed title=$title mode=$mode")
        reportReaderFirstDraw()
    }

    override fun hideMenu() {
        menuVisible = false
        if (::root.isInitialized) root.removeCallbacks(hideChromeRunnable)
        if (::topControls.isInitialized) topControls.visibility = View.GONE
        if (::controls.isInitialized) controls.visibility = View.GONE
        applyImmersive()
    }

    override fun showMenu() {
        menuVisible = true
        if (::topControls.isInitialized) topControls.visibility = View.VISIBLE
        if (::controls.isInitialized) controls.visibility = View.VISIBLE
        applyImmersive()
        scheduleChromeHide()
    }

    private fun scheduleChromeHide() {
        if (!::root.isInitialized || keepChromeVisibleForCi) return
        root.removeCallbacks(hideChromeRunnable)
        root.postDelayed(hideChromeRunnable, 2400L)
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
            Log.i(
                READER_TAG,
                "progress-save-start title=$title chapter=${location.chapterId.value} page=${page.index + 1}/$pageCount",
            )
            try {
                progressSink.onLocationChanged(location)
                Log.i(
                    READER_TAG,
                    "progress-saved title=$title chapter=${location.chapterId.value} page=${page.index + 1}/$pageCount",
                )
            } catch (error: Throwable) {
                Log.e(
                    READER_TAG,
                    "progress-save-failed title=$title chapter=${location.chapterId.value} page=${page.index + 1}/$pageCount",
                    error,
                )
            }
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
        if (::root.isInitialized) root.removeCallbacks(hideChromeRunnable)
        chapterPromotionJob?.cancel()
        viewer?.destroy()
        pageSources.distinct().forEach { source -> runCatching { source.close() } }
        pageSources.clear()
        super.onDestroy()
    }

    private fun buildTopControls(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(8), dp(4))
            background = roundedPanel(0xD9111722.toInt(), dp(20).toFloat())

            addView(
                ImageButton(this@ReaderActivity).apply {
                    setImageResource(R.drawable.ic_yomi_back)
                    setBackgroundColor(Color.TRANSPARENT)
                    setColorFilter(Color.WHITE)
                    contentDescription = "Back"
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setOnClickListener { finish() }
                },
                LinearLayout.LayoutParams(dp(44), dp(44)),
            )
            addView(
                TextView(this@ReaderActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(8), 0, dp(8), 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun buildControls(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(6), dp(6))
            background = roundedPanel(0xE6111722.toInt(), dp(20).toFloat())

            positionLabel = TextView(this@ReaderActivity).apply {
                textSize = 13f
                setTextColor(0xFFD7DEEA.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(
                positionLabel,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(
                ImageButton(this@ReaderActivity).apply {
                    setImageResource(R.drawable.ic_yomi_settings)
                    setBackgroundColor(Color.TRANSPARENT)
                    setColorFilter(0xFFD7DEEA.toInt())
                    contentDescription = "Reader settings"
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setOnClickListener { showReaderSettings() }
                },
                LinearLayout.LayoutParams(dp(44), dp(44)),
            )
        }
    }

    private fun showReaderSettings() {
        if (isFinishing) return
        root.removeCallbacks(hideChromeRunnable)
        val prefs = getPreferences(MODE_PRIVATE)
        val dialog = Dialog(this)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(22))
            background = roundedPanel(0xFF171C25.toInt(), dp(26).toFloat())
        }

        sheet.addView(
            TextView(this).apply {
                text = "Reader settings"
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(14))
            },
        )
        sheet.addView(sectionLabel("Reading mode"))

        listOf(
            ReadingMode.LTR_PAGED to "Left to right",
            ReadingMode.RTL_PAGED to "Right to left",
            ReadingMode.VERTICAL_PAGED to "Vertical pages",
            ReadingMode.WEBTOON to "Webtoon",
        ).forEach { (target, label) ->
            sheet.addView(
                settingsOption(label, target == mode) {
                    if (mode != target) {
                        mode = target
                        saveMode(target)
                        installViewer()
                    }
                    dialog.dismiss()
                },
            )
        }

        sheet.addView(sectionLabel("Page"))
        val cropEnabled = prefs.getBoolean(PREF_CROP, false)
        sheet.addView(
            settingsOption("Crop borders · " + if (cropEnabled) "On" else "Off") {
                prefs.edit().putBoolean(PREF_CROP, !cropEnabled).apply()
                installViewer()
                dialog.dismiss()
            },
        )
        val volumeEnabled = prefs.getBoolean(PREF_VOLUME_KEYS, false)
        sheet.addView(
            settingsOption("Volume keys · " + if (volumeEnabled) "On" else "Off") {
                prefs.edit().putBoolean(PREF_VOLUME_KEYS, !volumeEnabled).apply()
                installViewer()
                dialog.dismiss()
            },
        )

        sheet.addView(sectionLabel("Background"))
        listOf(
            "black" to "Black",
            "dark" to "Dark gray",
            "light" to "Light",
        ).forEach { (value, label) ->
            val selected = prefs.getString(PREF_BACKGROUND, "black") == value
            sheet.addView(
                settingsOption(label, selected) {
                    prefs.edit().putString(PREF_BACKGROUND, value).apply()
                    root.setBackgroundColor(backgroundColor())
                    installViewer()
                    dialog.dismiss()
                },
            )
        }

        dialog.setContentView(sheet)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener {
            if (menuVisible) scheduleChromeHide()
        }
        dialog.show()
        Log.i(READER_TAG, "reader-settings-opened")
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            decorView.setPadding(dp(10), 0, dp(10), dp(10))
        }
    }

    private fun sectionLabel(label: String): TextView {
        return TextView(this).apply {
            text = label.uppercase()
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(0xFF8E9AAF.toInt())
            setPadding(dp(4), dp(10), dp(4), dp(6))
        }
    }

    private fun settingsOption(
        label: String,
        selected: Boolean = false,
        onClick: () -> Unit,
    ): TextView {
        return TextView(this).apply {
            text = if (selected) "$label   ✓" else label
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (selected) 0xFFD4E4FF.toInt() else 0xFFE7ECF5.toInt())
            setPadding(dp(14), dp(13), dp(14), dp(13))
            setOnClickListener { onClick() }
        }
    }

    private fun roundedPanel(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
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
        Log.i(READER_TAG, "position title=$title mode=$mode label=${positionLabel.text}")
    }

    private fun reportReaderFirstDraw() {
        var scheduled = false
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (scheduled) return
                scheduled = true
                root.post {
                    Log.i(READER_TAG, "reader-first-draw title=$title mode=$mode")
                    reportFullyDrawn()
                    if (openSettingsForCi) root.post { if (!isFinishing) showReaderSettings() }
                    if (root.viewTreeObserver.isAlive) {
                        root.viewTreeObserver.removeOnDrawListener(this)
                    }
                }
            }
        }
        root.viewTreeObserver.addOnDrawListener(listener)
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
        root.removeCallbacks(hideChromeRunnable)
        root.removeAllViews()
        root.setBackgroundColor(0xFF0A0D12.toInt())
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(28), dp(28), dp(28))

                addView(
                    TextView(this@ReaderActivity).apply {
                        text = "Couldn’t open this book"
                        setTextColor(Color.WHITE)
                        textSize = 22f
                        gravity = Gravity.CENTER
                    },
                )
                addView(
                    TextView(this@ReaderActivity).apply {
                        text = message
                        setTextColor(0xFFAAB6C9.toInt())
                        textSize = 15f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(12), 0, dp(20))
                    },
                )
                addView(
                    Button(this@ReaderActivity).apply {
                        text = "Back to library"
                        setOnClickListener { finish() }
                    },
                )
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
        const val EXTRA_CI_KEEP_CHROME = "ci_keep_chrome"
        const val EXTRA_CI_OPEN_SETTINGS = "ci_open_settings"

        private const val READER_TAG = "YomiReader"
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
