@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var menuOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(AniyomiReadingMode.WEBTOON) }
    var currentPage by remember(session) {
        mutableIntStateOf(session.initialPage.coerceIn(0, pages.lastIndex))
    }
    var readerView by remember { mutableStateOf<AniyomiReaderView?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(currentPage, pages.size, session) {
        onProgress(session, currentPage + 1, pages.size)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                AniyomiReaderView(context).also { view ->
                    readerView = view
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

        if (controlsVisible) {
            Surface(
                color = Color.Black.copy(alpha = .86f),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
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

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, "Reader settings", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            AniyomiReadingMode.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    leadingIcon = {
                                        Icon(
                                            when (item) {
                                                AniyomiReadingMode.LEFT_TO_RIGHT -> Icons.Rounded.ArrowForward
                                                AniyomiReadingMode.RIGHT_TO_LEFT -> Icons.Rounded.ArrowBack
                                                AniyomiReadingMode.VERTICAL -> Icons.Rounded.ViewDay
                                                AniyomiReadingMode.WEBTOON -> Icons.Rounded.ViewStream
                                                AniyomiReadingMode.CONTINUOUS_VERTICAL -> Icons.Rounded.ViewAgenda
                                            },
                                            null,
                                        )
                                    },
                                    trailingIcon = {
                                        if (mode == item) {
                                            Icon(Icons.Rounded.Check, null, tint = SoraAccent)
                                        }
                                    },
                                    onClick = {
                                        mode = item
                                        readerView?.setReadingMode(item)
                                        readerView?.jumpTo(currentPage, notify = false)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = .88f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
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
                        Modifier.fillMaxWidth(),
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
                            session.sourceName,
                            color = SoraMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
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
}
