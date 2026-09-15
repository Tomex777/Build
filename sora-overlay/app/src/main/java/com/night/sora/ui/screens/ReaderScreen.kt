@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.night.sora.model.ReaderPage
import com.night.sora.model.ReaderSession
import com.night.sora.ui.theme.SoraAccent
import com.night.sora.ui.theme.SoraAccentInk
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface
import com.night.sora.ui.theme.SoraText
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ReaderMode(val label: String) {
    WEBTOON("Webtoon"),
    PAGED("Paged"),
}

@Composable
fun ReaderScreen(
    session: ReaderSession,
    onBack: () -> Unit,
) {
    val pages = session.pages
    if (pages.isEmpty()) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(session.chapterTitle) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
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
    var mode by remember { mutableStateOf(ReaderMode.WEBTOON) }
    var menuOpen by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(session.initialPage.coerceIn(0, pages.lastIndex)) }
    val webtoonState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val pagerState = rememberPagerState(initialPage = currentPage, pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    LaunchedEffect(webtoonState.firstVisibleItemIndex, mode) {
        if (mode == ReaderMode.WEBTOON) currentPage = webtoonState.firstVisibleItemIndex.coerceIn(0, pages.lastIndex)
    }
    LaunchedEffect(pagerState.currentPage, mode) {
        if (mode == ReaderMode.PAGED) currentPage = pagerState.currentPage.coerceIn(0, pages.lastIndex)
    }

    fun jumpTo(index: Int) {
        val target = index.coerceIn(0, pages.lastIndex)
        currentPage = target
        scope.launch {
            if (mode == ReaderMode.WEBTOON) webtoonState.animateScrollToItem(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (mode) {
            ReaderMode.WEBTOON -> {
                LazyColumn(
                    state = webtoonState,
                    modifier = Modifier.fillMaxSize().clickable { controlsVisible = !controlsVisible },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    itemsIndexed(pages, key = { index, page -> "${index}-${page.url}" }) { index, page ->
                        ReaderImage(
                            page = page,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                            contentDescription = "Page ${index + 1}",
                        )
                    }
                }
            }
            ReaderMode.PAGED -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { index ->
                    ZoomableReaderPage(
                        page = pages[index],
                        contentDescription = "Page ${index + 1}",
                        onTap = { controlsVisible = !controlsVisible },
                    )
                }
            }
        }

        if (controlsVisible) {
            Surface(
                color = Color.Black.copy(alpha = .86f),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier.statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(session.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(session.chapterTitle, color = SoraMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Reader settings", tint = Color.White) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ReaderMode.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    leadingIcon = {
                                        Icon(
                                            if (item == ReaderMode.WEBTOON) Icons.Rounded.ViewDay else Icons.Rounded.ViewCarousel,
                                            null,
                                        )
                                    },
                                    trailingIcon = { if (mode == item) Icon(Icons.Rounded.Check, null, tint = SoraAccent) },
                                    onClick = {
                                        mode = item
                                        menuOpen = false
                                        jumpTo(currentPage)
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
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { jumpTo(currentPage - 1) }, enabled = currentPage > 0) {
                            Icon(Icons.Rounded.ChevronLeft, "Previous page", tint = if (currentPage > 0) Color.White else SoraMuted)
                        }
                        Slider(
                            value = (currentPage + 1).toFloat(),
                            onValueChange = { jumpTo(it.roundToInt() - 1) },
                            valueRange = 1f..pages.size.toFloat(),
                            steps = (pages.size - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = SoraAccent,
                                activeTrackColor = SoraAccent,
                                inactiveTrackColor = SoraMuted.copy(alpha = .45f),
                            ),
                        )
                        IconButton(onClick = { jumpTo(currentPage + 1) }, enabled = currentPage < pages.lastIndex) {
                            Icon(Icons.Rounded.ChevronRight, "Next page", tint = if (currentPage < pages.lastIndex) Color.White else SoraMuted)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${currentPage + 1} / ${pages.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text(session.sourceName, color = SoraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Surface(
                color = Color.Black.copy(alpha = .56f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            ) {
                Text("${currentPage + 1} / ${pages.size}", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun ReaderImage(
    page: ReaderPage,
    contentScale: ContentScale,
    modifier: Modifier,
    contentDescription: String,
) {
    val context = LocalContext.current
    val request = remember(page.url, page.headers) {
        ImageRequest.Builder(context)
            .data(page.url)
            .apply {
                if (page.headers.isNotEmpty()) {
                    val networkHeaders = NetworkHeaders.Builder().apply {
                        page.headers.forEach { (name, value) -> set(name, value) }
                    }.build()
                    httpHeaders(networkHeaders)
                }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.background(Color.Black),
        contentScale = contentScale,
    )
}

@Composable
private fun ZoomableReaderPage(
    page: ReaderPage,
    contentDescription: String,
    onTap: () -> Unit,
) {
    var scale by remember(page.url) { mutableFloatStateOf(1f) }
    var offsetX by remember(page.url) { mutableFloatStateOf(0f) }
    var offsetY by remember(page.url) { mutableFloatStateOf(0f) }

    Box(
        Modifier.fillMaxSize().clipToBounds().pointerInput(page.url) {
            detectTransformGestures { _, pan, zoom, _ ->
                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                scale = nextScale
                if (nextScale == 1f) {
                    offsetX = 0f
                    offsetY = 0f
                } else {
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
        }.clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        ReaderImage(
            page = page,
            contentScale = ContentScale.Fit,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
        )
    }
}
