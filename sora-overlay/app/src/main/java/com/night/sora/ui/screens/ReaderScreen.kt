@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.night.sora.model.ReaderSession
import com.night.sora.ui.reader.AniyomiReaderView
import com.night.sora.ui.reader.AniyomiReadingMode
import com.night.sora.ui.theme.SoraAccent
import com.night.sora.ui.theme.SoraMuted
import kotlin.math.roundToInt

private enum class SoraReaderOrientation(val label: String) {
    AUTO("Auto rotate"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

/**
 * Sora Manga reader backed by the Aniyomi/Mihon viewer stack.
 *
 * Sora owns resolved page URLs and progress. Aniyomi-derived views own the
 * actual pager/webtoon rendering, zooming and navigation mechanics.
 */
@Composable
fun ReaderScreen(
    session: ReaderSession,
    onBack: () -> Unit,
    onProgress: (ReaderSession, Int, Int) -> Unit = { _, _, _ -> },
) {
    val pages = session.pages
    val context = LocalContext.current
    val activity = remember(context) { context.findReaderActivity() }

    if (pages.isEmpty()) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(session.chapterTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This source returned no readable pages.", color = SoraMuted)
            }
        }
        return
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(AniyomiReadingMode.WEBTOON) }
    var orientation by remember { mutableStateOf(SoraReaderOrientation.AUTO) }
    var cropBorders by remember { mutableStateOf(false) }
    var currentPage by remember(session) {
        mutableIntStateOf(session.initialPage.coerceIn(0, pages.lastIndex))
    }
    var readerView by remember { mutableStateOf<AniyomiReaderView?>(null) }

    BackHandler(onBack = onBack)

    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        onDispose {
            if (previous != null) activity.requestedOrientation = previous
        }
    }

    LaunchedEffect(currentPage, pages.size, session) {
        onProgress(session, currentPage + 1, pages.size)
    }

    LaunchedEffect(orientation, activity) {
        activity?.requestedOrientation = when (orientation) {
            SoraReaderOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            SoraReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            SoraReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { androidContext ->
                AniyomiReaderView(androidContext).also { view ->
                    readerView = view
                    view.setCropBorders(cropBorders)
                    view.setContent(
                        pages = pages,
                        initialPage = currentPage,
                        mode = mode,
                        onPageChanged = { index -> currentPage = index },
                        onTap = { controlsVisible = !controlsVisible },
                    )
                }
            },
            update = { view ->
                view.setCropBorders(cropBorders)
                view.setContent(
                    pages = pages,
                    initialPage = currentPage,
                    mode = mode,
                    onPageChanged = { index -> currentPage = index },
                    onTap = { controlsVisible = !controlsVisible },
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(tween(200)) { -it },
            exit = slideOutVertically(tween(200)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = Color.Black.copy(alpha = .90f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                    }

                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            session.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            session.chapterTitle,
                            color = SoraMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        session.sourceName,
                        color = SoraMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(tween(200)) { it },
            exit = slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = Color.Black.copy(alpha = .92f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { readerView?.previousPage() },
                                enabled = currentPage > 0,
                            ) {
                                Icon(
                                    Icons.Rounded.ChevronLeft,
                                    "Previous page",
                                    tint = if (currentPage > 0) Color.White else SoraMuted,
                                )
                            }

                            Slider(
                                value = (currentPage + 1).toFloat(),
                                onValueChange = { value ->
                                    readerView?.jumpTo(value.roundToInt() - 1)
                                },
                                valueRange = 1f..pages.size.toFloat(),
                                steps = (pages.size - 2).coerceAtLeast(0),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = SoraAccent,
                                    activeTrackColor = SoraAccent,
                                    inactiveTrackColor = SoraMuted.copy(alpha = .45f),
                                ),
                            )

                            IconButton(
                                onClick = { readerView?.nextPage() },
                                enabled = currentPage < pages.lastIndex,
                            ) {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    "Next page",
                                    tint = if (currentPage < pages.lastIndex) Color.White else SoraMuted,
                                )
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${currentPage + 1} / ${pages.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                mode.label,
                                color = SoraMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = .08f))

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            IconButton(onClick = { modeMenuOpen = true }) {
                                Icon(readerModeIcon(mode), "Reading mode", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = modeMenuOpen,
                                onDismissRequest = { modeMenuOpen = false },
                            ) {
                                AniyomiReadingMode.entries.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.label) },
                                        leadingIcon = { Icon(readerModeIcon(item), null) },
                                        trailingIcon = {
                                            if (mode == item) Icon(Icons.Rounded.Check, null, tint = SoraAccent)
                                        },
                                        onClick = {
                                            mode = item
                                            readerView?.setReadingMode(item)
                                            readerView?.jumpTo(currentPage, notify = false)
                                            modeMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                orientation = when (orientation) {
                                    SoraReaderOrientation.AUTO -> SoraReaderOrientation.PORTRAIT
                                    SoraReaderOrientation.PORTRAIT -> SoraReaderOrientation.LANDSCAPE
                                    SoraReaderOrientation.LANDSCAPE -> SoraReaderOrientation.AUTO
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.ScreenRotation, orientation.label, tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                cropBorders = !cropBorders
                                readerView?.setCropBorders(cropBorders)
                                readerView?.jumpTo(currentPage, notify = false)
                            },
                        ) {
                            Icon(
                                if (cropBorders) Icons.Rounded.Crop else Icons.Rounded.CropFree,
                                if (cropBorders) "Disable crop borders" else "Crop borders",
                                tint = if (cropBorders) SoraAccent else Color.White,
                            )
                        }

                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Rounded.Settings, "Reader settings", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (!controlsVisible) {
            Surface(
                color = Color.Black.copy(alpha = .56f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    "${currentPage + 1} / ${pages.size}",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            containerColor = Color(0xFF161614),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp),
            ) {
                Text("Reader settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                ReaderSettingRow(
                    title = "Reading mode",
                    subtitle = mode.label,
                    onClick = {
                        settingsOpen = false
                        modeMenuOpen = true
                    },
                )

                ReaderSettingRow(
                    title = "Orientation",
                    subtitle = orientation.label,
                    onClick = {
                        orientation = when (orientation) {
                            SoraReaderOrientation.AUTO -> SoraReaderOrientation.PORTRAIT
                            SoraReaderOrientation.PORTRAIT -> SoraReaderOrientation.LANDSCAPE
                            SoraReaderOrientation.LANDSCAPE -> SoraReaderOrientation.AUTO
                        }
                    },
                )

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Crop borders", fontWeight = FontWeight.Medium)
                        Text(
                            "Trim empty page margins while reading.",
                            color = SoraMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = cropBorders,
                        onCheckedChange = { enabled ->
                            cropBorders = enabled
                            readerView?.setCropBorders(enabled)
                            readerView?.jumpTo(currentPage, notify = false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SoraMuted, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = SoraMuted)
    }
}

private fun readerModeIcon(mode: AniyomiReadingMode) = when (mode) {
    AniyomiReadingMode.LEFT_TO_RIGHT -> Icons.Rounded.ArrowForward
    AniyomiReadingMode.RIGHT_TO_LEFT -> Icons.Rounded.ArrowBack
    AniyomiReadingMode.VERTICAL -> Icons.Rounded.ViewDay
    AniyomiReadingMode.WEBTOON -> Icons.Rounded.ViewStream
    AniyomiReadingMode.CONTINUOUS_VERTICAL -> Icons.Rounded.ViewAgenda
}

private tailrec fun Context.findReaderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findReaderActivity()
    else -> null
}
