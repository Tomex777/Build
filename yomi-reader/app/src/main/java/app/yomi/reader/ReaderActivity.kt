package app.yomi.reader

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.Modifier
import eu.kanade.presentation.reader.ReaderPageIndicator
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import eu.kanade.presentation.reader.ReadingModeSelectDialog
import eu.kanade.presentation.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.ReaderContentOverlay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation as MihonReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode as MihonReadingMode
import androidx.core.content.FileProvider
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
import app.yomi.reader.core.ReaderScaleMode
import app.yomi.reader.core.calculateOverallProgress
import app.yomi.reader.local.LibraryAvailability
import app.yomi.reader.local.LocalBookIdentityStore
import app.yomi.reader.local.LocalChapterBinding
import app.yomi.reader.local.LocalLibraryStore
import app.yomi.reader.local.ReaderBookmark
import app.yomi.reader.local.ReaderBookmarkStore
import app.yomi.reader.local.SharedPreferencesProgressSink
import app.yomi.reader.local.TreeBookCatalog
import app.yomi.reader.local.ZipDocumentPageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import reader.shared.android.ReaderPageImageView
import reader.shared.android.ReaderRenderConfig
import reader.shared.android.ReaderViewerHost
import reader.shared.android.Viewer
import reader.shared.android.model.ViewerChapter
import reader.shared.android.model.ViewerChapters
import reader.shared.android.model.ViewerPage
import reader.shared.android.pager.PagerDirection
import reader.shared.android.pager.SharedPagerViewer
import reader.shared.android.navigation.DisabledNavigation
import reader.shared.android.navigation.EdgeNavigation
import reader.shared.android.navigation.KindlishNavigation
import reader.shared.android.navigation.LNavigation
import reader.shared.android.navigation.RightAndLeftNavigation
import reader.shared.android.navigation.ViewerNavigation
import reader.shared.android.webtoon.SharedWebtoonViewer
import java.io.BufferedInputStream
import java.io.File

class ReaderActivity : ComponentActivity(), ReaderViewerHost {
    override val context: Context get() = this
    override var menuVisible: Boolean = true
        private set

    private lateinit var root: FrameLayout
    private lateinit var chrome: ComposeView
    private var loadingView: View? = null
    // Page-change callbacks may enqueue several IO jobs during a fast swipe.
    // Serialize their commits and reject stale snapshots so the last page wins.
    private val progressWriteMutex = Mutex()
    @Volatile private var latestProgressRevision = 0L
    private val chromeRevision = mutableIntStateOf(0)
    private val settingsOpen = mutableStateOf(false)
    private val pageActions = mutableStateOf<ViewerPage?>(null)
    private val quickReadingModeOpen = mutableStateOf(false)
    private val quickOrientationOpen = mutableStateOf(false)
    private var pendingSavePage: ViewerPage? = null
    private val savePageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val page = pendingSavePage
        pendingSavePage = null
        val destination = result.data?.data
        if (page != null && destination != null) {
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        page.open().use { input ->
                            val output = contentResolver.openOutputStream(destination, "w")
                                ?: error("Unable to write selected document")
                            output.use { input.copyTo(it) }
                        }
                    }
                }.onSuccess {
                    Toast.makeText(this@ReaderActivity, "Page saved", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Log.e(READER_TAG, "page-save-failed", it)
                    Toast.makeText(this@ReaderActivity, "Unable to save this page", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // Kept for diagnostic log continuity while migrating reader UI to Mihon's bars.
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
    private val bookmarkStore by lazy { ReaderBookmarkStore(this) }
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
        showBookLoading()
        readIntentReaderState()
        applySavedOrientation()
        applyCustomBrightness()
        Log.i(READER_TAG, "activity-created title=$title mode=$mode")
        // Mihon's original Compose reader bars occupy a transparent overlay.
        // Empty space passes touch gestures through to the original page viewer.
        positionLabel = TextView(this)
        chrome = ComposeView(this)
        chrome.setContent {
            chromeRevision.intValue // refresh on page/mode/bookmark/chrome state
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ComposeColor(0xFFD4E4FF),
                    surface = ComposeColor(0xFF111722),
                    onSurface = ComposeColor.White,
                ),
            ) {
                val selectedChapter = viewerChapters.getOrNull(activeChapterIndex)
                val pages = selectedChapter?.pages.orEmpty()
                Box(modifier = Modifier.fillMaxSize()) {
                    val displayPrefs = getPreferences(MODE_PRIVATE)
                    ReaderContentOverlay(
                        brightness = if (displayPrefs.getBoolean(PREF_CUSTOM_BRIGHTNESS, false)) {
                            displayPrefs.getInt(PREF_BRIGHTNESS_VALUE, 0).coerceIn(-75, 100)
                        } else {
                            0
                        },
                        color = displayPrefs.getInt(PREF_TINT_COLOR, 0).takeIf { it != 0 },
                        colorBlendMode = when (displayPrefs.getInt(PREF_COLOR_BLEND_MODE, 0)) {
                            1 -> BlendMode.Modulate
                            2 -> BlendMode.Screen
                            3 -> BlendMode.Overlay
                            4 -> BlendMode.Lighten
                            5 -> BlendMode.Darken
                            else -> BlendMode.SrcOver
                        },
                    )
                    // Mihon displays its outlined page indicator only when the
                    // toolbars are hidden, above the viewer's image layer.
                    if (!menuVisible && getPreferences(MODE_PRIVATE).getBoolean(PREF_SHOW_PAGE_NUMBER, true)) {
                        ReaderPageIndicator(
                            currentPage = (lastLocation?.pageIndex ?: selectedChapter?.requestedPage ?: 0) + 1,
                            totalPages = pages.size,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                        )
                    }
                    ReaderAppBars(
                    visible = menuVisible,
                    mangaTitle = title,
                    chapterTitle = selectedChapter?.chapter?.title?.takeIf { viewerChapters.size > 1 },
                    navigateUp = ::finish,
                    onClickTopAppBar = {},
                    bookmarked = lastLocation?.let(bookmarkStore::contains) ?: false,
                    onToggleBookmarked = {
                        lastLocation?.let { bookmarkStore.toggle(it) }
                        refreshChrome()
                    },
                    onOpenInWebView = null,
                    onOpenInBrowser = null,
                    onShare = null,
                    isRtl = mode == ReadingMode.RTL_PAGED,
                    onNextChapter = { changeChapter(1) },
                    enabledNext = activeChapterIndex < viewerChapters.lastIndex,
                    onPreviousChapter = { changeChapter(-1) },
                    enabledPrevious = activeChapterIndex > 0,
                    currentPage = ((lastLocation?.takeIf { it.chapterId == selectedChapter?.chapter?.id }?.pageIndex
                        ?: selectedChapter?.requestedPage ?: 0) + 1).coerceAtLeast(1),
                    totalPages = pages.size,
                    onPageIndexChange = ::navigateToPage,
                    readingMode = when (mode) {
                        ReadingMode.LTR_PAGED -> MihonReadingMode.LEFT_TO_RIGHT
                        ReadingMode.RTL_PAGED -> MihonReadingMode.RIGHT_TO_LEFT
                        ReadingMode.VERTICAL_PAGED -> MihonReadingMode.VERTICAL
                        ReadingMode.WEBTOON -> MihonReadingMode.WEBTOON
                    },
                    onClickReadingMode = ::showReadingModePicker,
                    orientation = when (getPreferences(MODE_PRIVATE).getString(PREF_ORIENTATION, ORIENTATION_AUTO)) {
                        ORIENTATION_PORTRAIT -> MihonReaderOrientation.PORTRAIT
                        ORIENTATION_LANDSCAPE -> MihonReaderOrientation.LANDSCAPE
                        else -> MihonReaderOrientation.DEFAULT
                    },
                    onClickOrientation = ::showOrientationPicker,
                    cropEnabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_CROP, false),
                    onClickCropBorder = {
                        val prefs = getPreferences(MODE_PRIVATE)
                        prefs.edit().putBoolean(PREF_CROP, !prefs.getBoolean(PREF_CROP, false)).apply()
                        installViewer()
                        refreshChrome()
                    },
                    onClickSettings = ::showReaderSettings,
                    )
                }
                if (quickReadingModeOpen.value) {
                    ReadingModeSelectDialog(
                        current = mode,
                        onDismissRequest = {
                            quickReadingModeOpen.value = false
                            showMenu()
                        },
                        onApply = { selected ->
                            quickReadingModeOpen.value = false
                            if (mode != selected) {
                                mode = selected
                                saveMode(selected)
                                installViewer()
                            }
                            showMenu()
                        },
                    )
                }
                if (quickOrientationOpen.value) {
                    OrientationSelectDialog(
                        current = getPreferences(MODE_PRIVATE)
                            .getString(PREF_ORIENTATION, ORIENTATION_AUTO) ?: ORIENTATION_AUTO,
                        onDismissRequest = {
                            quickOrientationOpen.value = false
                            showMenu()
                        },
                        onApply = { selected ->
                            quickOrientationOpen.value = false
                            getPreferences(MODE_PRIVATE).edit()
                                .putString(PREF_ORIENTATION, selected).apply()
                            applySavedOrientation()
                            refreshChrome()
                            showMenu()
                        },
                    )
                }
                pageActions.value?.let { page ->
                    ReaderPageActionsDialog(
                        onDismissRequest = {
                            pageActions.value = null
                            if (menuVisible) scheduleChromeHide()
                        },
                        onSetAsCover = { saveAsBookCover(page) },
                        onCopy = { shareReaderPage(page, copyToClipboard = true) },
                        onShare = { shareReaderPage(page, copyToClipboard = false) },
                        onSave = { promptSavePage(page) },
                    )
                }
                if (settingsOpen.value) {
                    val prefs = getPreferences(MODE_PRIVATE)
                    ReaderSettingsDialog(
                        onDismissRequest = {
                            settingsOpen.value = false
                            showMenu()
                        },
                        onShowMenus = ::showMenu,
                        onHideMenus = ::hideMenu,
                        readingMode = mode,
                        onReadingModeChange = { selected ->
                            if (mode != selected) {
                                mode = selected
                                saveMode(selected)
                                installViewer()
                                refreshChrome()
                            }
                        },
                        orientation = prefs.getString(PREF_ORIENTATION, ORIENTATION_AUTO) ?: ORIENTATION_AUTO,
                        onOrientationChange = { value ->
                            prefs.edit().putString(PREF_ORIENTATION, value).apply()
                            applySavedOrientation()
                            refreshChrome()
                        },
                        scaleMode = loadScaleMode(),
                        onScaleModeChange = { value ->
                            prefs.edit().putString(PREF_SCALE_MODE, value.name).apply()
                            installViewer()
                            refreshChrome()
                        },
                        zoomStart = prefs.getString(PREF_ZOOM_START, "auto") ?: "auto",
                        onZoomStartChange = { value ->
                            prefs.edit().putString(PREF_ZOOM_START, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        landscapeZoom = prefs.getBoolean(PREF_LANDSCAPE_ZOOM, false),
                        onLandscapeZoomChange = { value ->
                            prefs.edit().putBoolean(PREF_LANDSCAPE_ZOOM, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        webtoonSidePadding = prefs.getInt(PREF_WEBTOON_PADDING, 0).coerceIn(0, 25),
                        onWebtoonSidePaddingChange = { value ->
                            prefs.edit().putInt(PREF_WEBTOON_PADDING, value.coerceIn(0, 25)).apply()
                            installViewer()
                            refreshChrome()
                        },
                        webtoonDoubleTapZoom = prefs.getBoolean(PREF_WEBTOON_DOUBLE_TAP, true),
                        onWebtoonDoubleTapZoomChange = { value ->
                            prefs.edit().putBoolean(PREF_WEBTOON_DOUBLE_TAP, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        webtoonZoomOutDisabled = prefs.getBoolean(PREF_WEBTOON_DISABLE_ZOOM_OUT, false),
                        onWebtoonZoomOutDisabledChange = { value ->
                            prefs.edit().putBoolean(PREF_WEBTOON_DISABLE_ZOOM_OUT, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        cropEnabled = prefs.getBoolean(PREF_CROP, false),
                        onCropChange = { value ->
                            prefs.edit().putBoolean(PREF_CROP, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        showPageNumber = prefs.getBoolean(PREF_SHOW_PAGE_NUMBER, true),
                        onShowPageNumberChange = { value ->
                            prefs.edit().putBoolean(PREF_SHOW_PAGE_NUMBER, value).apply()
                            updatePositionLabel(lastLocation)
                            refreshChrome()
                        },
                        volumeKeys = prefs.getBoolean(PREF_VOLUME_KEYS, false),
                        onVolumeKeysChange = { value ->
                            prefs.edit().putBoolean(PREF_VOLUME_KEYS, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        volumeKeysInverted = prefs.getBoolean(PREF_VOLUME_KEYS_INVERTED, false),
                        onVolumeKeysInvertedChange = { value ->
                            prefs.edit().putBoolean(PREF_VOLUME_KEYS_INVERTED, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        pageTransitions = prefs.getBoolean(PREF_PAGE_TRANSITIONS, true),
                        onPageTransitionsChange = { value ->
                            prefs.edit().putBoolean(PREF_PAGE_TRANSITIONS, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        tapZones = prefs.getString(PREF_TAP_ZONES, "default") ?: "default",
                        onTapZonesChange = { value ->
                            prefs.edit().putString(PREF_TAP_ZONES, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        tapZoneInvert = prefs.getString(PREF_TAP_ZONE_INVERT, "none") ?: "none",
                        onTapZoneInvertChange = { value ->
                            prefs.edit().putString(PREF_TAP_ZONE_INVERT, value).apply()
                            installViewer()
                            refreshChrome()
                        },
                        background = prefs.getString(PREF_BACKGROUND, "black") ?: "black",
                        onBackgroundChange = { value ->
                            prefs.edit().putString(PREF_BACKGROUND, value).apply()
                            root.setBackgroundColor(backgroundColor())
                            installViewer()
                            refreshChrome()
                        },
                        customBrightness = prefs.getBoolean(PREF_CUSTOM_BRIGHTNESS, false),
                        onCustomBrightnessChange = { value ->
                            prefs.edit().putBoolean(PREF_CUSTOM_BRIGHTNESS, value).apply()
                            applyCustomBrightness()
                            refreshChrome()
                        },
                        brightnessValue = prefs.getInt(PREF_BRIGHTNESS_VALUE, 0).coerceIn(-75, 100),
                        onBrightnessValueChange = { value ->
                            prefs.edit().putInt(PREF_BRIGHTNESS_VALUE, value.coerceIn(-75, 100)).apply()
                            applyCustomBrightness()
                            refreshChrome()
                        },
                        colorTint = prefs.getInt(PREF_TINT_COLOR, 0),
                        onColorTintChange = { value ->
                            prefs.edit().putInt(PREF_TINT_COLOR, value).apply()
                            refreshChrome()
                        },
                        filterBlendMode = prefs.getInt(PREF_COLOR_BLEND_MODE, 0),
                        onFilterBlendModeChange = { value ->
                            prefs.edit().putInt(PREF_COLOR_BLEND_MODE, value).apply()
                            refreshChrome()
                        },
                        grayscale = prefs.getBoolean(PREF_GRAYSCALE, false),
                        onGrayscaleChange = { value ->
                            prefs.edit().putBoolean(PREF_GRAYSCALE, value).apply()
                            applyPageColorMatrix()
                            refreshChrome()
                        },
                        invertedColors = prefs.getBoolean(PREF_INVERTED_COLORS, false),
                        onInvertedColorsChange = { value ->
                            prefs.edit().putBoolean(PREF_INVERTED_COLORS, value).apply()
                            applyPageColorMatrix()
                            refreshChrome()
                        },
                    )
                }
            }
        }
        // ComposeView is final. Instead of subclassing it, host Mihon's Compose
        // chrome in a transparent FrameLayout that only accepts gestures inside
        // the visible reader bars. The remaining center area must be handled by
        // the underlying Mihon pager/webtoon viewer, including page taps and zoom.
        val chromeTouchHost = object : FrameLayout(this) {
            private var routingToChrome = false

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val topBarBottom = dp(112).toFloat()
                    val bottomBarTop = height.toFloat() - dp(205).toFloat()
                    routingToChrome = menuVisible &&
                        (event.y < topBarBottom || event.y > bottomBarTop)
                }
                val handled = routingToChrome && super.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    routingToChrome = false
                }
                return handled
            }
        }.apply {
            isClickable = false
            addView(
                chrome,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        root.addView(
            chromeTouchHost,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        applyImmersive()
        openIntentBook()
    }

    private fun showBookLoading() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(ProgressBar(this@ReaderActivity).apply {
                isIndeterminate = true
                contentDescription = "Opening book"
            })
            addView(TextView(this@ReaderActivity).apply {
                text = "Opening your book…"
                setTextColor(0xFFCFD8E5.toInt())
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
            })
        }
        loadingView = content
        root.addView(
            content,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
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
        loadingView?.let(root::removeView)
        loadingView = null
        viewer?.destroy()
        viewer?.getView()?.let(root::removeView)

        val zoomStart = getPreferences(MODE_PRIVATE).getString(PREF_ZOOM_START, "auto")
        val zoomStartPosition = when (zoomStart) {
            "left" -> ReaderPageImageView.ZoomStartPosition.LEFT
            "right" -> ReaderPageImageView.ZoomStartPosition.RIGHT
            "center" -> ReaderPageImageView.ZoomStartPosition.CENTER
            else -> when (mode) {
                ReadingMode.LTR_PAGED -> ReaderPageImageView.ZoomStartPosition.LEFT
                ReadingMode.RTL_PAGED -> ReaderPageImageView.ZoomStartPosition.RIGHT
                else -> ReaderPageImageView.ZoomStartPosition.CENTER
            }
        }
        val renderConfig = ReaderRenderConfig(
            zoomStartPosition = zoomStartPosition,
            landscapeZoom = getPreferences(MODE_PRIVATE).getBoolean(PREF_LANDSCAPE_ZOOM, false),
            webtoonSidePaddingPercent = getPreferences(MODE_PRIVATE).getInt(PREF_WEBTOON_PADDING, 0).coerceIn(0, 25),
            doubleTapZoom = getPreferences(MODE_PRIVATE).getBoolean(PREF_WEBTOON_DOUBLE_TAP, true),
            webtoonZoomOutDisabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_WEBTOON_DISABLE_ZOOM_OUT, false),
            cropBorders = getPreferences(MODE_PRIVATE).getBoolean(PREF_CROP, false),
            backgroundColor = backgroundColor(),
            volumeKeysEnabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_VOLUME_KEYS, false),
            volumeKeysInverted = getPreferences(MODE_PRIVATE).getBoolean(PREF_VOLUME_KEYS_INVERTED, false),
            minimumScaleType = minimumScaleType(),
            pageTransitions = getPreferences(MODE_PRIVATE).getBoolean(PREF_PAGE_TRANSITIONS, true),
            pagerNavigation = readerTapZones(pager = true),
            webtoonNavigation = readerTapZones(pager = false),
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
        applyPageColorMatrix()
        updatePositionLabel(lastLocation)
        refreshChrome()
        if (menuVisible) scheduleChromeHide()
        Log.i(READER_TAG, "viewer-installed title=$title mode=$mode")
        reportReaderFirstDraw()
    }

    override fun hideMenu() {
        menuVisible = false
        if (::root.isInitialized) root.removeCallbacks(hideChromeRunnable)
        refreshChrome()
        applyImmersive()
    }

    override fun showMenu() {
        menuVisible = true
        refreshChrome()
        applyImmersive()
        scheduleChromeHide()
    }

    private fun scheduleChromeHide() {
        if (!::root.isInitialized || keepChromeVisibleForCi || settingsOpen.value) return
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
        val overall = calculateOverallProgress(
            mode = mode,
            chapterIndex = chapterIndex,
            chapterCount = viewerChapters.size.coerceAtLeast(1),
            pageIndex = page.index,
            pageCount = pageCount,
            pageOffsetFraction = pageOffsetFraction,
        )

        val location = ReaderLocation(
            bookId = currentBook.id,
            chapterId = page.chapter.chapter.id,
            pageIndex = page.index,
            pageOffsetFraction = pageOffsetFraction,
            overallProgress = overall,
        )
        lastLocation = location
        val progressRevision = ++latestProgressRevision
        updatePositionLabel(location)

        lifecycleScope.launch(Dispatchers.IO) {
            progressWriteMutex.withLock {
                // An older coroutine can resume after the user has already turned
                // another page; never commit an outdated chapter/page in that case.
                if (progressRevision != latestProgressRevision) return@withLock
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
        if (isFinishing) return false
        root.removeCallbacks(hideChromeRunnable)
        pageActions.value = page
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
            val revision = latestProgressRevision
            lifecycleScope.launch(Dispatchers.IO) {
                progressWriteMutex.withLock {
                    if (revision == latestProgressRevision) {
                        runCatching { progressSink.onSessionClosed(location) }
                            .onFailure { Log.e(READER_TAG, "progress-close-failed", it) }
                    }
                }
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

    private fun refreshChrome() {
        chromeRevision.intValue += 1
    }

    private fun navigateToPage(index: Int) {
        val current = viewerChapters.getOrNull(activeChapterIndex) ?: return
        val pages = current.pages.orEmpty()
        if (index !in pages.indices) return
        current.requestedPage = index
        current.requestedOffsetFraction = 0.0
        viewer?.moveToPage(pages[index], 0.0)
        refreshChrome()
    }

    private fun changeChapter(offset: Int) {
        val target = activeChapterIndex + offset
        if (target !in viewerChapters.indices) return
        lifecycleScope.launch {
            runCatching {
                prepareWindow(target)
                activeChapterIndex = target
                val chapter = viewerChapters[target]
                lastLocation = null
                viewer?.setChapters(window(target))
                refreshChrome()
            }.onFailure {
                Log.e(READER_TAG, "chapter-navigation-failed", it)
                Toast.makeText(this@ReaderActivity, "Could not open chapter", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showReadingModePicker() {
        if (isFinishing) return
        root.removeCallbacks(hideChromeRunnable)
        quickReadingModeOpen.value = true
    }

    private fun showOrientationPicker() {
        if (isFinishing) return
        root.removeCallbacks(hideChromeRunnable)
        quickOrientationOpen.value = true
    }

    private fun showReaderSettings() {
        if (isFinishing) return
        root.removeCallbacks(hideChromeRunnable)
        settingsOpen.value = true
        Log.i(READER_TAG, "reader-settings-opened")
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
            if (getPreferences(MODE_PRIVATE).getBoolean(PREF_SHOW_PAGE_NUMBER, true)) {
                chapterPart + "page " + (location.pageIndex + 1) + "/" + total + " · " + percent + "%"
            } else {
                chapterPart + percent + "%"
            }
        }
        Log.i(READER_TAG, "position title=$title mode=$mode label=${positionLabel.text}")
        refreshChrome()
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

    private fun openBookmark(bookmark: ReaderBookmark) {
        val currentBook = book ?: return
        val chapterIndex = viewerChapters.indexOfFirst { it.chapter.id == bookmark.chapterId }
        if (chapterIndex < 0) return

        lifecycleScope.launch {
            runCatching {
                prepareWindow(chapterIndex)
                val chapter = viewerChapters[chapterIndex]
                val pages = chapter.pages.orEmpty()
                if (pages.isEmpty()) return@runCatching
                val pageIndex = bookmark.pageIndex.coerceIn(0, pages.lastIndex)
                chapter.requestedPage = pageIndex
                chapter.requestedOffsetFraction = bookmark.pageOffsetFraction
                activeChapterIndex = chapterIndex
                val location = ReaderLocation(
                    bookId = currentBook.id,
                    chapterId = chapter.chapter.id,
                    pageIndex = pageIndex,
                    pageOffsetFraction = bookmark.pageOffsetFraction,
                    overallProgress = bookmark.overallProgress,
                )
                lastLocation = location
                viewer?.setChapters(window(chapterIndex))
                viewer?.moveToPage(pages[pageIndex], bookmark.pageOffsetFraction)
                updatePositionLabel(location)
            }.onFailure {
                Toast.makeText(this@ReaderActivity, "This bookmark is no longer available.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptSavePage(page: ViewerPage) {
        pendingSavePage = page
        val name = page.displayName
            .replace('/', '_')
            .replace('\\', '_')
            .take(120)
            .ifBlank { "page.png" }
        val mime = when (name.substringAfterLast('.', "").lowercase()) {
            "jpeg", "jpg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "png" -> "image/png"
            else -> "image/*"
        }
        savePageLauncher.launch(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mime
                putExtra(Intent.EXTRA_TITLE, name)
            },
        )
    }

    private fun shareReaderPage(page: ViewerPage, copyToClipboard: Boolean) {
        lifecycleScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) { stageReaderPage(page) }
                val sharedUri = FileProvider.getUriForFile(
                    this@ReaderActivity,
                    "${packageName}.fileprovider",
                    file,
                )
                if (copyToClipboard) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newUri(contentResolver, page.displayName, sharedUri),
                    )
                    Toast.makeText(this@ReaderActivity, "Page copied", Toast.LENGTH_SHORT).show()
                } else {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, sharedUri)
                        clipData = ClipData.newRawUri("Yomi reader page", sharedUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, "Share page"))
                }
            }.onFailure {
                Log.e(READER_TAG, "page-export-failed", it)
                Toast.makeText(this@ReaderActivity, "Unable to share this page", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun stageReaderPage(page: ViewerPage): File {
        val directory = File(cacheDir, "reader-pages").apply { mkdirs() }
        val expiry = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        directory.listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.delete() }
        val stream = BufferedInputStream(page.open())
        return stream.use { input ->
            input.mark(12)
            val magic = ByteArray(12)
            var readBytes = 0
            while (readBytes < magic.size) {
                val count = input.read(magic, readBytes, magic.size - readBytes)
                if (count <= 0) break
                readBytes += count
            }
            input.reset()
            val ext = when {
                readBytes >= 8 && magic[0] == 0x89.toByte() &&
                    magic[1] == 'P'.code.toByte() && magic[2] == 'N'.code.toByte() -> ".png"
                readBytes >= 4 && magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'I'.code.toByte() && magic[2] == 'F'.code.toByte() -> ".gif"
                readBytes >= 12 && String(magic, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(magic, 8, 4, Charsets.US_ASCII) == "WEBP" -> ".webp"
                else -> ".jpg"
            }
            val target = File.createTempFile("yomi-page-", ext, directory)
            try {
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        require(total <= 128L * 1024 * 1024) { "Page too large to share" }
                        output.write(buffer, 0, count)
                    }
                }
                target
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }

    private fun saveAsBookCover(page: ViewerPage) {
        val currentBook = book ?: return
        lifecycleScope.launch {
            runCatching {
                val coverUri = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    page.open().use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                        bounds.outWidth.toLong() * bounds.outHeight <= 250_000_000L) {
                        "Invalid cover image dimensions"
                    }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 640) sample *= 2
                    val bitmap = page.open().use {
                        BitmapFactory.decodeStream(
                            it, null, BitmapFactory.Options().apply { inSampleSize = sample },
                        )
                    } ?: error("Could not decode this page as a cover")
                    try {
                        val directory = File(filesDir, "covers").apply { mkdirs() }
                        val safeId = currentBook.id.value.filter {
                            it.isLetterOrDigit() || it == '-' || it == '_'
                        }.ifBlank { "book" }
                        val destination = File(directory, "$safeId.jpg")
                        val staging = File.createTempFile("cover-", ".jpg", directory)
                        try {
                            staging.outputStream().buffered().use {
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it))
                            }
                            if (!staging.renameTo(destination)) {
                                staging.copyTo(destination, overwrite = true)
                            }
                        } finally {
                            staging.delete()
                        }
                        Uri.fromFile(destination).toString()
                    } finally {
                        bitmap.recycle()
                    }
                }
                libraryStore.setCover(currentBook.id, coverUri)
            }.onSuccess {
                Toast.makeText(this@ReaderActivity, "Book cover updated", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Log.e(READER_TAG, "page-set-cover-failed", it)
                Toast.makeText(this@ReaderActivity, "Unable to set this cover", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readerTapZones(pager: Boolean): ViewerNavigation {
        val preferences = getPreferences(MODE_PRIVATE)
        val layout = preferences.getString(PREF_TAP_ZONES, "default")
        val navigation = when (layout) {
            "disabled" -> DisabledNavigation()
            "edge" -> EdgeNavigation()
            "kindle" -> KindlishNavigation()
            "l" -> LNavigation()
            "right_left" -> RightAndLeftNavigation()
            else -> if (pager) RightAndLeftNavigation() else LNavigation()
        }
        navigation.invertMode = when (preferences.getString(PREF_TAP_ZONE_INVERT, "none")) {
            "horizontal" -> ViewerNavigation.InvertMode.HORIZONTAL
            "vertical" -> ViewerNavigation.InvertMode.VERTICAL
            "both" -> ViewerNavigation.InvertMode.BOTH
            else -> ViewerNavigation.InvertMode.NONE
        }
        return navigation
    }

    private fun loadScaleMode(): ReaderScaleMode {
        return getPreferences(MODE_PRIVATE)
            .getString(PREF_SCALE_MODE, ReaderScaleMode.FIT_SCREEN.name)
            ?.let { runCatching { ReaderScaleMode.valueOf(it) }.getOrNull() }
            ?: ReaderScaleMode.FIT_SCREEN
    }

    private fun minimumScaleType(): Int {
        return when (loadScaleMode()) {
            ReaderScaleMode.FIT_SCREEN -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
            ReaderScaleMode.STRETCH -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP
            ReaderScaleMode.FIT_WIDTH -> SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
            ReaderScaleMode.FIT_HEIGHT -> SubsamplingScaleImageView.SCALE_TYPE_FIT_HEIGHT
            ReaderScaleMode.ORIGINAL -> SubsamplingScaleImageView.SCALE_TYPE_ORIGINAL_SIZE
            ReaderScaleMode.SMART_FIT -> SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT
        }
    }

    /**
     * Mihon brightness semantics: a positive value overrides Android window
     * brightness, a negative value uses minimal brightness plus a Compose
     * black overlay, and zero returns control to system brightness.
     */
    private fun applyCustomBrightness() {
        val prefs = getPreferences(MODE_PRIVATE)
        val value = if (prefs.getBoolean(PREF_CUSTOM_BRIGHTNESS, false)) {
            prefs.getInt(PREF_BRIGHTNESS_VALUE, 0).coerceIn(-75, 100)
        } else {
            0
        }
        val screenBrightness = when {
            value > 0 -> value / 100f
            value < 0 -> 0.01f
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = window.attributes.apply { this.screenBrightness = screenBrightness }
    }

    private fun applySavedOrientation() {
        requestedOrientation = when (
            getPreferences(MODE_PRIVATE).getString(PREF_ORIENTATION, ORIENTATION_AUTO)
        ) {
            ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /**
     * Faithful port of Mihon's ReaderActivity.ReaderConfig.getCombinedPaint().
     * The paint is applied to the page viewer, never to Compose controls, and
     * leaves image bytes unchanged in the imported archive.
     */
    private fun applyPageColorMatrix() {
        val pageView = viewer?.getView() ?: return
        val prefs = getPreferences(MODE_PRIVATE)
        val grayscale = prefs.getBoolean(PREF_GRAYSCALE, false)
        val inverted = prefs.getBoolean(PREF_INVERTED_COLORS, false)
        if (!grayscale && !inverted) {
            pageView.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        val matrix = ColorMatrix().apply {
            if (grayscale) setSaturation(0f)
            if (inverted) {
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            }
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        pageView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        Log.i(READER_TAG, "reader-page-filter grayscale=$grayscale inverted=$inverted")
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
        loadingView = null
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
        private const val PREF_VOLUME_KEYS_INVERTED = "volume_keys_inverted"
        private const val PREF_PAGE_TRANSITIONS = "page_transitions"
        private const val PREF_TAP_ZONES = "reader_tap_zones"
        private const val PREF_TAP_ZONE_INVERT = "reader_tap_zone_invert"
        private const val PREF_SHOW_PAGE_NUMBER = "show_page_number"
        private const val PREF_SCALE_MODE = "scale_mode"
        private const val PREF_ZOOM_START = "reader_zoom_start"
        private const val PREF_LANDSCAPE_ZOOM = "reader_landscape_zoom"
        private const val PREF_WEBTOON_PADDING = "reader_webtoon_padding"
        private const val PREF_WEBTOON_DOUBLE_TAP = "reader_webtoon_double_tap_zoom"
        private const val PREF_WEBTOON_DISABLE_ZOOM_OUT = "reader_webtoon_disable_zoom_out"
        private const val PREF_ORIENTATION = "orientation"
        private const val PREF_BACKGROUND = "background"
        private const val PREF_CUSTOM_BRIGHTNESS = "reader_custom_brightness"
        private const val PREF_BRIGHTNESS_VALUE = "reader_brightness_value"
        private const val PREF_TINT_COLOR = "reader_color_tint"
        private const val PREF_COLOR_BLEND_MODE = "reader_color_filter_blend_mode"
        private const val PREF_GRAYSCALE = "reader_grayscale"
        private const val PREF_INVERTED_COLORS = "reader_inverted_colors"
        private const val ORIENTATION_AUTO = "auto"
        private const val ORIENTATION_PORTRAIT = "portrait"
        private const val ORIENTATION_LANDSCAPE = "landscape"

        fun newIntent(context: Context, uri: String, kind: String, title: String): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_KIND, kind)
                putExtra(EXTRA_TITLE, title)
                // An ACTION_VIEW grant can be temporary. Forward the actual content
                // URI so ReaderActivity keeps permission after leaving MainActivity.
                val bookUri = Uri.parse(uri)
                if (bookUri.scheme == ContentResolver.SCHEME_CONTENT) {
                    clipData = ClipData.newRawUri("Yomi book", bookUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }
}
