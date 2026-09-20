package com.example.whatsapp.presentation.reader.mihon

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.whatsapp.NightMihonReaderActivity
import kotlinx.coroutines.launch

/*
 * Reader chrome is adapted from Mihon's ReaderAppBars, ReaderTopBar,
 * ReaderBottomBar, ChapterNavigator, ReaderPageIndicator and
 * ReadingModeSelectDialog.
 *
 * Upstream revision: 424bbc53b85c19acd3c3b7c03ec6f73f516f25bc
 * License: Apache-2.0. See third_party/mihon/LICENSE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightMihonReaderScreen(
    mangaTitle: String,
    chapterTitle: String,
    pages: List<MihonPageSpec>,
    initialPage: Int = 0,
    readerKey: String = mangaTitle,
    progressKey: String? = null,
    onBack: () -> Unit,
    onPreviousChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findReaderActivity() }
    val preferences = remember(context) {
        context.getSharedPreferences(
            "night_mihon_reader",
            Context.MODE_PRIVATE,
        )
    }

    val modePreferenceKey = remember(readerKey) {
        "mode:" + readerKey.trim().ifBlank { mangaTitle }
    }

    var modeName by rememberSaveable(readerKey) {
        mutableStateOf(
            preferences.getString(
                modePreferenceKey,
                preferences.getString(
                    "mode",
                    MihonReadingMode.RIGHT_TO_LEFT.name,
                ) ?: MihonReadingMode.RIGHT_TO_LEFT.name,
            ) ?: MihonReadingMode.RIGHT_TO_LEFT.name,
        )
    }
    var orientationName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "orientation",
                MihonReaderOrientation.FREE.name,
            ) ?: MihonReaderOrientation.FREE.name,
        )
    }
    var cropBorders by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "cropBorders",
                false,
            ),
        )
    }
    var sidePadding by rememberSaveable {
        mutableIntStateOf(
            preferences
                .getInt("webtoonSidePadding", 0)
                .coerceIn(0, 25),
        )
    }
    var webtoonDoubleTapZoom by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "webtoonDoubleTapZoom",
                true,
            ),
        )
    }
    var keepScreenOn by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "keepScreenOn",
                false,
            ),
        )
    }
    var fullscreen by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "fullscreen",
                true,
            ),
        )
    }
    var showPageNumber by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "showPageNumber",
                true,
            ),
        )
    }
    var volumeKeys by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "volumeKeys",
                false,
            ),
        )
    }
    var invertVolumeKeys by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "invertVolumeKeys",
                false,
            ),
        )
    }
    var webtoonZoomOutDisabled by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "webtoonZoomOutDisabled",
                false,
            ),
        )
    }
    var backgroundName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "background",
                MihonReaderBackground.BLACK.name,
            ) ?: MihonReaderBackground.BLACK.name,
        )
    }
    var scaleTypeName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "imageScaleType",
                MihonImageScaleType.FIT_SCREEN.name,
            ) ?: MihonImageScaleType.FIT_SCREEN.name,
        )
    }
    var zoomStartName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "zoomStart",
                MihonZoomStart.AUTOMATIC.name,
            ) ?: MihonZoomStart.AUTOMATIC.name,
        )
    }
    var landscapeZoom by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "landscapeZoom",
                true,
            ),
        )
    }
    var navigateToPan by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                "navigateToPan",
                true,
            ),
        )
    }
    var pagerTapZoneName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "pagerTapZone",
                MihonTapZone.RIGHT_AND_LEFT.name,
            ) ?: MihonTapZone.RIGHT_AND_LEFT.name,
        )
    }
    var webtoonTapZoneName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "webtoonTapZone",
                MihonTapZone.L.name,
            ) ?: MihonTapZone.L.name,
        )
    }
    var pagerTapInvertName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "pagerTapInvert",
                MihonTapInvertMode.NONE.name,
            ) ?: MihonTapInvertMode.NONE.name,
        )
    }
    var webtoonTapInvertName by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                "webtoonTapInvert",
                MihonTapInvertMode.NONE.name,
            ) ?: MihonTapInvertMode.NONE.name,
        )
    }

    val mode =
        runCatching {
            MihonReadingMode.valueOf(modeName)
        }.getOrDefault(
            MihonReadingMode.RIGHT_TO_LEFT,
        )
    val orientation =
        runCatching {
            MihonReaderOrientation
                .valueOf(orientationName)
        }.getOrDefault(
            MihonReaderOrientation.FREE,
        )
    val readerBackground =
        runCatching {
            MihonReaderBackground.valueOf(backgroundName)
        }.getOrDefault(MihonReaderBackground.BLACK)
    val imageScaleType =
        runCatching {
            MihonImageScaleType.valueOf(scaleTypeName)
        }.getOrDefault(MihonImageScaleType.FIT_SCREEN)
    val zoomStart =
        runCatching {
            MihonZoomStart.valueOf(zoomStartName)
        }.getOrDefault(MihonZoomStart.AUTOMATIC)
    val isWebtoonMode =
        mode == MihonReadingMode.WEBTOON ||
            mode == MihonReadingMode.CONTINUOUS_VERTICAL
    val tapZone =
        runCatching {
            MihonTapZone.valueOf(
                if (isWebtoonMode) {
                    webtoonTapZoneName
                } else {
                    pagerTapZoneName
                },
            )
        }.getOrDefault(
            if (isWebtoonMode) {
                MihonTapZone.L
            } else {
                MihonTapZone.RIGHT_AND_LEFT
            },
        )
    val tapInvertMode =
        runCatching {
            MihonTapInvertMode.valueOf(
                if (isWebtoonMode) {
                    webtoonTapInvertName
                } else {
                    pagerTapInvertName
                },
            )
        }.getOrDefault(MihonTapInvertMode.NONE)
    val readerBackgroundColor =
        when (readerBackground) {
            MihonReaderBackground.BLACK -> Color.Black
            MihonReaderBackground.GRAY -> Color(0xFF303030)
            MihonReaderBackground.WHITE -> Color.White
            MihonReaderBackground.AUTOMATIC ->
                if (isSystemInDarkTheme()) Color.Black else Color.White
        }

    val savedProgress = remember(progressKey, pages.size) {
        progressKey
            ?.let { preferences.getInt("progress:" + it, -1) }
            ?.takeIf { it >= 0 }
    }

    var currentPage by rememberSaveable(progressKey, pages.size) {
        mutableIntStateOf(
            (savedProgress ?: initialPage).coerceIn(
                0,
                (pages.size - 1).coerceAtLeast(0),
            ),
        )
    }

    LaunchedEffect(currentPage, progressKey) {
        progressKey?.let {
            preferences
                .edit()
                .putInt("progress:" + it, currentPage)
                .apply()
        }
    }
    var controlsVisible by rememberSaveable {
        mutableStateOf(true)
    }
    var host by remember {
        mutableStateOf<MihonReaderHostView?>(null)
    }
    var readingModeSheet by remember {
        mutableStateOf(false)
    }
    var orientationSheet by remember {
        mutableStateOf(false)
    }
    var settingsSheet by remember {
        mutableStateOf(false)
    }
    var overflowOpen by remember {
        mutableStateOf(false)
    }
    var pageActionIndex by remember {
        mutableStateOf<Int?>(null)
    }
    val bookmarkKey =
        remember(mangaTitle, chapterTitle) {
            "bookmark:" +
                mangaTitle +
                ":" +
                chapterTitle
        }
    var bookmarked by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                bookmarkKey,
                false,
            ),
        )
    }

    DisposableEffect(activity) {
        val previousOrientation =
            activity?.requestedOrientation

        activity?.window?.let { window ->
            val controller =
                WindowCompat.getInsetsController(
                    window,
                    window.decorView,
                )
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(
                WindowInsetsCompat.Type.systemBars(),
            )
        }

        onDispose {
            activity?.window?.let { window ->
                WindowCompat
                    .getInsetsController(
                        window,
                        window.decorView,
                    )
                    .show(
                        WindowInsetsCompat
                            .Type.systemBars(),
                    )
            }
            if (previousOrientation != null) {
                activity.requestedOrientation =
                    previousOrientation
            }
            activity?.window?.clearFlags(
                WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    LaunchedEffect(orientation, activity) {
        activity?.requestedOrientation =
            orientation.activityInfo
    }

    LaunchedEffect(keepScreenOn, activity) {
        if (keepScreenOn) {
            activity?.window?.addFlags(
                WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON,
            )
        } else {
            activity?.window?.clearFlags(
                WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    LaunchedEffect(fullscreen, activity) {
        activity?.window?.let { window ->
            val controller =
                WindowCompat.getInsetsController(
                    window,
                    window.decorView,
                )
            if (fullscreen) {
                controller.hide(
                    WindowInsetsCompat.Type.systemBars(),
                )
            } else {
                controller.show(
                    WindowInsetsCompat.Type.systemBars(),
                )
            }
        }
    }

    DisposableEffect(
        activity,
        host,
        volumeKeys,
        invertVolumeKeys,
    ) {
        val readerActivity =
            activity as? NightMihonReaderActivity
        val activeHost = host

        if (volumeKeys && activeHost != null) {
            readerActivity?.setVolumeKeyHandler { volumeDown ->
                val moveNext =
                    if (invertVolumeKeys) {
                        !volumeDown
                    } else {
                        volumeDown
                    }
                if (moveNext) {
                    activeHost.moveNextByInput()
                } else {
                    activeHost.movePreviousByInput()
                }
                true
            }
        } else {
            readerActivity?.setVolumeKeyHandler(null)
        }

        onDispose {
            readerActivity?.setVolumeKeyHandler(null)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(readerBackgroundColor),
    ) {
        AndroidView(
            factory = { viewContext ->
                MihonReaderHostView(viewContext).also {
                    reader ->
                    host = reader
                    reader.onPageChanged = {
                        currentPage = it
                    }
                    reader.onToggleMenu = {
                        controlsVisible =
                            !controlsVisible
                    }
                    reader.onLongTap = { pageIndex ->
                        pageActionIndex =
                            pageIndex.coerceIn(
                                0,
                                (pages.size - 1)
                                    .coerceAtLeast(0),
                            )
                    }
                }
            },
            update = { reader ->
                reader.configure(
                    pages = pages,
                    mode = mode,
                    currentPage = currentPage,
                    cropBorders = cropBorders,
                    sidePadding = sidePadding,
                    webtoonDoubleTapZoom =
                        webtoonDoubleTapZoom,
                    webtoonZoomOutDisabled =
                        webtoonZoomOutDisabled,
                    scaleType = imageScaleType,
                    zoomStart = zoomStart,
                    landscapeZoom = landscapeZoom,
                    navigateToPan = navigateToPan,
                    tapZone = tapZone,
                    tapInvertMode = tapInvertMode,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (
            !controlsVisible &&
            showPageNumber &&
            pages.isNotEmpty()
        ) {
            MihonPageIndicator(
                currentPage = currentPage + 1,
                totalPages = pages.size,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .padding(bottom = 18.dp),
            )
        }

        if (controlsVisible) {
            val barColor = Color(0xEB18191B)

            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(barColor)
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled
                                .ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = mangaTitle,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            text = chapterTitle,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.68f),
                        )
                    }

                    IconButton(
                        onClick = {
                            bookmarked = !bookmarked
                            preferences
                                .edit()
                                .putBoolean(
                                    bookmarkKey,
                                    bookmarked,
                                )
                                .apply()
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (bookmarked) {
                                    Icons.Default.Bookmark
                                } else {
                                    Icons.Default
                                        .BookmarkBorder
                                },
                            contentDescription =
                                if (bookmarked) {
                                    "Remove bookmark"
                                } else {
                                    "Bookmark"
                                },
                            tint = Color.White,
                        )
                    }

                    Box {
                        IconButton(
                            onClick = {
                                overflowOpen = true
                            },
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White,
                            )
                        }

                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = {
                                overflowOpen = false
                            },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text("Reader settings")
                                },
                                onClick = {
                                    overflowOpen = false
                                    settingsSheet = true
                                },
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.weight(1f),
                )

                if (pages.isNotEmpty()) {
                    MihonChapterNavigator(
                        currentPage = currentPage,
                        totalPages = pages.size,
                        rightToLeft =
                            mode ==
                            MihonReadingMode
                                .RIGHT_TO_LEFT,
                        onPageChanged = {
                            currentPage = it
                            host?.goToPage(it)
                        },
                        onPreviousChapter =
                            onPreviousChapter,
                        onNextChapter =
                            onNextChapter,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(barColor)
                            .windowInsetsPadding(
                                WindowInsets.navigationBars,
                            )
                            .padding(horizontal = 8.dp),
                    horizontalArrangement =
                        Arrangement.SpaceEvenly,
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            readingModeSheet = true
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    mode.iconRes,
                                ),
                            contentDescription =
                                "Reading mode",
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            orientationSheet = true
                        },
                    ) {
                        Icon(
                            imageVector =
                                orientationIcon(
                                    orientation,
                                ),
                            contentDescription =
                                "Orientation",
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            cropBorders =
                                !cropBorders
                            preferences
                                .edit()
                                .putBoolean(
                                    "cropBorders",
                                    cropBorders,
                                )
                                .apply()
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (cropBorders) {
                                    Icons.Default.Crop
                                } else {
                                    Icons.Default.CropFree
                                },
                            contentDescription =
                                "Crop borders",
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            settingsSheet = true
                        },
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription =
                                "Reader settings",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (readingModeSheet) {
        MihonReadingModeSheet(
            selected = mode,
            onDismiss = {
                readingModeSheet = false
            },
            onApply = { selected ->
                modeName = selected.name
                preferences
                    .edit()
                    .putString(
                        modePreferenceKey,
                        selected.name,
                    )
                    .apply()
                readingModeSheet = false
            },
        )
    }

    if (orientationSheet) {
        MihonOrientationSheet(
            selected = orientation,
            onDismiss = {
                orientationSheet = false
            },
            onApply = { selected ->
                orientationName = selected.name
                preferences
                    .edit()
                    .putString(
                        "orientation",
                        selected.name,
                    )
                    .apply()
                orientationSheet = false
            },
        )
    }

    if (settingsSheet) {
        MihonReaderSettingsSheet(
            isWebtoonMode = isWebtoonMode,
            sidePadding = sidePadding,
            onSidePaddingChanged = {
                sidePadding = it
                preferences
                    .edit()
                    .putInt(
                        "webtoonSidePadding",
                        it,
                    )
                    .apply()
            },
            doubleTapZoom =
                webtoonDoubleTapZoom,
            onDoubleTapZoomChanged = {
                webtoonDoubleTapZoom = it
                preferences
                    .edit()
                    .putBoolean(
                        "webtoonDoubleTapZoom",
                        it,
                    )
                    .apply()
            },
            scaleType = imageScaleType,
            onScaleTypeChanged = {
                scaleTypeName = it.name
                preferences.edit()
                    .putString("imageScaleType", it.name)
                    .apply()
            },
            zoomStart = zoomStart,
            onZoomStartChanged = {
                zoomStartName = it.name
                preferences.edit()
                    .putString("zoomStart", it.name)
                    .apply()
            },
            landscapeZoom = landscapeZoom,
            onLandscapeZoomChanged = {
                landscapeZoom = it
                preferences.edit()
                    .putBoolean("landscapeZoom", it)
                    .apply()
            },
            navigateToPan = navigateToPan,
            onNavigateToPanChanged = {
                navigateToPan = it
                preferences.edit()
                    .putBoolean("navigateToPan", it)
                    .apply()
            },
            tapZone = tapZone,
            onTapZoneChanged = {
                if (isWebtoonMode) {
                    webtoonTapZoneName = it.name
                    preferences.edit()
                        .putString("webtoonTapZone", it.name)
                        .apply()
                } else {
                    pagerTapZoneName = it.name
                    preferences.edit()
                        .putString("pagerTapZone", it.name)
                        .apply()
                }
            },
            tapInvertMode = tapInvertMode,
            onTapInvertModeChanged = {
                if (isWebtoonMode) {
                    webtoonTapInvertName = it.name
                    preferences.edit()
                        .putString("webtoonTapInvert", it.name)
                        .apply()
                } else {
                    pagerTapInvertName = it.name
                    preferences.edit()
                        .putString("pagerTapInvert", it.name)
                        .apply()
                }
            },
            keepScreenOn = keepScreenOn,
            onKeepScreenOnChanged = {
                keepScreenOn = it
                preferences
                    .edit()
                    .putBoolean(
                        "keepScreenOn",
                        it,
                    )
                    .apply()
            },
            fullscreen = fullscreen,
            onFullscreenChanged = {
                fullscreen = it
                preferences.edit()
                    .putBoolean("fullscreen", it)
                    .apply()
            },
            showPageNumber = showPageNumber,
            onShowPageNumberChanged = {
                showPageNumber = it
                preferences.edit()
                    .putBoolean("showPageNumber", it)
                    .apply()
            },
            volumeKeys = volumeKeys,
            onVolumeKeysChanged = {
                volumeKeys = it
                preferences.edit()
                    .putBoolean("volumeKeys", it)
                    .apply()
            },
            invertVolumeKeys = invertVolumeKeys,
            onInvertVolumeKeysChanged = {
                invertVolumeKeys = it
                preferences.edit()
                    .putBoolean("invertVolumeKeys", it)
                    .apply()
            },
            zoomOutDisabled = webtoonZoomOutDisabled,
            onZoomOutDisabledChanged = {
                webtoonZoomOutDisabled = it
                preferences.edit()
                    .putBoolean("webtoonZoomOutDisabled", it)
                    .apply()
            },
            background = readerBackground,
            onBackgroundChanged = {
                backgroundName = it.name
                preferences.edit()
                    .putString("background", it.name)
                    .apply()
            },
            onDismiss = {
                settingsSheet = false
            },
        )
    }

    pageActionIndex?.let { index ->
        val page = pages.getOrNull(index)
        if (page != null) {
            MihonPageActionsSheet(
                pageNumber = index + 1,
                onDismiss = { pageActionIndex = null },
                onSetCover = {
                    NightMihonPageActions.setAsCover(
                        context = context,
                        readerKey = readerKey,
                        page = page,
                    )
                    pageActionIndex = null
                    Toast.makeText(
                        context,
                        "Cover updated.",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onCopy = {
                    pageActionIndex = null
                    scope.launch {
                        NightMihonPageActions
                            .resolvePageUri(context, page)
                            .onSuccess {
                                NightMihonPageActions.copyPage(
                                    context,
                                    it,
                                )
                                Toast.makeText(
                                    context,
                                    "Page copied.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Could not copy this page.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    }
                },
                onShare = {
                    pageActionIndex = null
                    scope.launch {
                        NightMihonPageActions
                            .resolvePageUri(context, page)
                            .onSuccess {
                                NightMihonPageActions.sharePage(
                                    context,
                                    it,
                                )
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Could not share this page.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    }
                },
                onSave = {
                    pageActionIndex = null
                    scope.launch {
                        NightMihonPageActions
                            .savePage(
                                context = context,
                                page = page,
                                pageNumber = index + 1,
                            )
                            .onSuccess {
                                Toast.makeText(
                                    context,
                                    "Page saved to Pictures/Night/Manga.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Could not save this page.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    }
                },
            )
        } else {
            pageActionIndex = null
        }
    }
}

@Composable
private fun MihonChapterNavigator(
    currentPage: Int,
    totalPages: Int,
    rightToLeft: Boolean,
    onPageChanged: (Int) -> Unit,
    onPreviousChapter: (() -> Unit)?,
    onNextChapter: (() -> Unit)?,
) {
    val barColor = Color(0xEB18191B)

    CompositionLocalProvider(
        LocalLayoutDirection provides
            LayoutDirection.Ltr,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            val leadingCallback =
                if (rightToLeft) {
                    onNextChapter
                } else {
                    onPreviousChapter
                }
            val trailingCallback =
                if (rightToLeft) {
                    onPreviousChapter
                } else {
                    onNextChapter
                }

            FilledIconButton(
                enabled = leadingCallback != null,
                onClick = {
                    leadingCallback?.invoke()
                },
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription =
                        "Previous chapter",
                    tint = Color.White,
                )
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides
                    if (rightToLeft) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .background(
                                barColor,
                                RoundedCornerShape(24.dp),
                            )
                            .padding(horizontal = 16.dp),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Box(
                        contentAlignment =
                            Alignment.CenterEnd,
                    ) {
                        Text(
                            (currentPage + 1)
                                .toString(),
                            color = Color.White,
                        )
                        Text(
                            totalPages.toString(),
                            color = Color.Transparent,
                        )
                    }

                    Slider(
                        value =
                            (currentPage + 1)
                                .toFloat(),
                        onValueChange = {
                            onPageChanged(
                                it.toInt()
                                    .coerceIn(
                                        1,
                                        totalPages,
                                    ) - 1,
                            )
                        },
                        valueRange =
                            1f..
                                totalPages
                                    .toFloat()
                                    .coerceAtLeast(1f),
                        steps =
                            (totalPages - 2)
                                .coerceAtLeast(0),
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(
                                    horizontal = 8.dp,
                                ),
                    )

                    Text(
                        totalPages.toString(),
                        color = Color.White,
                    )
                }
            }

            FilledIconButton(
                enabled =
                    trailingCallback != null,
                onClick = {
                    trailingCallback?.invoke()
                },
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next chapter",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun MihonPageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    if (currentPage <= 0 || totalPages <= 0) return

    val label =
        currentPage.toString() +
            " / " +
            totalPages.toString()

    val style =
        TextStyle(
            color = Color(235, 235, 235),
            fontSize =
                MaterialTheme
                    .typography
                    .bodySmall
                    .fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

    val strokeStyle =
        style.copy(
            color = Color(45, 45, 45),
            drawStyle = Stroke(width = 4f),
        )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = strokeStyle,
        )
        Text(
            text = label,
            style = style,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MihonReadingModeSheet(
    selected: MihonReadingMode,
    onDismiss: () -> Unit,
    onApply: (MihonReadingMode) -> Unit,
) {
    var pending by remember(selected) {
        mutableStateOf(selected)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier.padding(bottom = 18.dp),
        ) {
            Text(
                text = "Reading mode",
                style =
                    MaterialTheme.typography
                        .titleLarge,
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 12.dp,
                    ),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                    ),
            ) {
                items(MihonReadingMode.entries) {
                    item ->
                    Surface(
                        tonalElevation =
                            if (pending == item) {
                                5.dp
                            } else {
                                0.dp
                            },
                        shape =
                            RoundedCornerShape(18.dp),
                        modifier =
                            Modifier
                                .padding(5.dp)
                                .clickable {
                                    pending = item
                                },
                    ) {
                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            modifier =
                                Modifier.padding(16.dp),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        item.iconRes,
                                    ),
                                contentDescription = null,
                            )
                            Text(
                                text = item.label,
                                modifier =
                                    Modifier.padding(
                                        top = 8.dp,
                                    ),
                            )
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 8.dp,
                        ),
            ) {
                Spacer(
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onApply(pending)
                    },
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                    )
                    Text(
                        text = "Apply",
                        modifier =
                            Modifier.padding(
                                start = 7.dp,
                            ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MihonOrientationSheet(
    selected: MihonReaderOrientation,
    onDismiss: () -> Unit,
    onApply: (MihonReaderOrientation) -> Unit,
) {
    var pending by remember(selected) {
        mutableStateOf(selected)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier.padding(bottom = 18.dp),
        ) {
            Text(
                text = "Orientation",
                style =
                    MaterialTheme.typography
                        .titleLarge,
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 12.dp,
                    ),
            )

            MihonReaderOrientation.entries.forEach {
                item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                pending = item
                            }
                            .padding(
                                horizontal = 20.dp,
                                vertical = 12.dp,
                            ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label,
                        modifier =
                            Modifier.weight(1f),
                    )
                    if (pending == item) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 8.dp,
                        ),
            ) {
                Spacer(
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onApply(pending)
                    },
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MihonPageActionsSheet(
    pageNumber: Int,
    onDismiss: () -> Unit,
    onSetCover: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 20.dp,
                vertical = 10.dp,
            ),
        ) {
            Text(
                text = "Page " + pageNumber,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            listOf(
                "Set as cover" to onSetCover,
                "Copy to clipboard" to onCopy,
                "Share" to onShare,
                "Save" to onSave,
            ).forEach { (label, action) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = action)
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MihonReaderSettingsSheet(
    isWebtoonMode: Boolean,
    sidePadding: Int,
    onSidePaddingChanged: (Int) -> Unit,
    doubleTapZoom: Boolean,
    onDoubleTapZoomChanged: (Boolean) -> Unit,
    scaleType: MihonImageScaleType,
    onScaleTypeChanged: (MihonImageScaleType) -> Unit,
    zoomStart: MihonZoomStart,
    onZoomStartChanged: (MihonZoomStart) -> Unit,
    landscapeZoom: Boolean,
    onLandscapeZoomChanged: (Boolean) -> Unit,
    navigateToPan: Boolean,
    onNavigateToPanChanged: (Boolean) -> Unit,
    tapZone: MihonTapZone,
    onTapZoneChanged: (MihonTapZone) -> Unit,
    tapInvertMode: MihonTapInvertMode,
    onTapInvertModeChanged: (MihonTapInvertMode) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    fullscreen: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    showPageNumber: Boolean,
    onShowPageNumberChanged: (Boolean) -> Unit,
    volumeKeys: Boolean,
    onVolumeKeysChanged: (Boolean) -> Unit,
    invertVolumeKeys: Boolean,
    onInvertVolumeKeysChanged: (Boolean) -> Unit,
    zoomOutDisabled: Boolean,
    onZoomOutDisabledChanged: (Boolean) -> Unit,
    background: MihonReaderBackground,
    onBackgroundChanged: (MihonReaderBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderValue by remember(sidePadding) {
        mutableFloatStateOf(
            sidePadding.toFloat(),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 20.dp,
                        vertical = 12.dp,
                    ),
        ) {
            Text(
                text = "Reader settings",
                style =
                    MaterialTheme.typography
                        .titleLarge,
            )

            if (isWebtoonMode) {
                Text(
                    text =
                        "Long strip side padding · " +
                            sliderValue
                                .toInt()
                                .toString() +
                            "%",
                    modifier =
                        Modifier.padding(top = 18.dp),
                )

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onSidePaddingChanged(
                            it.toInt(),
                        )
                    },
                    valueRange = 0f..25f,
                    steps = 24,
                )

                ReaderSettingSwitch(
                    label =
                        "Double tap zoom",
                    checked = doubleTapZoom,
                    onCheckedChange =
                        onDoubleTapZoomChanged,
                )
            } else {
                ReaderSettingChoice(
                    label = "Scale type",
                    selected = scaleType,
                    entries =
                        MihonImageScaleType.entries,
                    itemLabel = { it.label },
                    onSelected =
                        onScaleTypeChanged,
                )

                ReaderSettingChoice(
                    label = "Zoom start position",
                    selected = zoomStart,
                    entries =
                        MihonZoomStart.entries,
                    itemLabel = { it.label },
                    onSelected =
                        onZoomStartChanged,
                )

                ReaderSettingSwitch(
                    label = "Zoom landscape images",
                    checked = landscapeZoom,
                    onCheckedChange =
                        onLandscapeZoomChanged,
                )

                ReaderSettingSwitch(
                    label = "Navigate wide image when tapping",
                    checked = navigateToPan,
                    onCheckedChange =
                        onNavigateToPanChanged,
                )
            }

            ReaderSettingChoice(
                label = "Tap zones",
                selected = tapZone,
                entries = MihonTapZone.entries,
                itemLabel = { it.label },
                onSelected = onTapZoneChanged,
            )

            ReaderSettingChoice(
                label = "Invert tapping",
                selected = tapInvertMode,
                entries =
                    MihonTapInvertMode.entries,
                itemLabel = { it.label },
                onSelected =
                    onTapInvertModeChanged,
            )

            ReaderSettingSwitch(
                label = "Keep screen on",
                checked = keepScreenOn,
                onCheckedChange =
                    onKeepScreenOnChanged,
            )

            ReaderSettingSwitch(
                label = "Fullscreen",
                checked = fullscreen,
                onCheckedChange = onFullscreenChanged,
            )

            ReaderSettingSwitch(
                label = "Show page number",
                checked = showPageNumber,
                onCheckedChange = onShowPageNumberChanged,
            )

            ReaderSettingSwitch(
                label = "Volume keys",
                checked = volumeKeys,
                onCheckedChange = onVolumeKeysChanged,
            )

            ReaderSettingSwitch(
                label = "Invert volume keys",
                checked = invertVolumeKeys,
                onCheckedChange = onInvertVolumeKeysChanged,
            )

            if (isWebtoonMode) {
                ReaderSettingSwitch(
                    label = "Disable zoom out",
                    checked = zoomOutDisabled,
                    onCheckedChange =
                        onZoomOutDisabledChanged,
                )
            }

            Text(
                text = "Background color",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )

            MihonReaderBackground.entries.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBackgroundChanged(item) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label,
                        modifier = Modifier.weight(1f),
                    )
                    if (item == background) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.padding(bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun <T> ReaderSettingChoice(
    label: String,
    selected: T,
    entries: List<T>,
    itemLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        modifier =
            Modifier.padding(
                top = 14.dp,
                bottom = 4.dp,
            ),
    )

    entries.forEach { item ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelected(item)
                    }
                    .padding(vertical = 9.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text = itemLabel(item),
                modifier = Modifier.weight(1f),
            )
            if (item == selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onCheckedChange(!checked)
                }
                .padding(vertical = 10.dp),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun orientationIcon(
    orientation: MihonReaderOrientation,
) =
    when (orientation) {
        MihonReaderOrientation.FREE ->
            Icons.Default.ScreenRotation

        MihonReaderOrientation.PORTRAIT,
        MihonReaderOrientation.LOCKED_PORTRAIT,
        MihonReaderOrientation.REVERSE_PORTRAIT,
        -> Icons.Default.StayCurrentPortrait

        MihonReaderOrientation.LANDSCAPE,
        MihonReaderOrientation.LOCKED_LANDSCAPE,
        -> Icons.Default.StayCurrentLandscape
    }

private tailrec fun Context.findReaderActivity():
    Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper ->
            baseContext.findReaderActivity()
        else -> null
    }
