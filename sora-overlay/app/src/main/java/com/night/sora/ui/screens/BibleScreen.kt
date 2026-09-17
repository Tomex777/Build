@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.data.*
import com.night.sora.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private sealed interface BibleRoute {
    data object Hub : BibleRoute
    data object Search : BibleRoute
    data object Bookmarks : BibleRoute
    data class Chapters(val book: BibleBook) : BibleRoute
    data class Reader(val book: BibleBook, val chapter: Int, val initialVerse: Int = 1) : BibleRoute
}

@Composable
fun BibleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { BibleRepository(context.applicationContext) }
    var route by remember { mutableStateOf<BibleRoute>(BibleRoute.Hub) }
    var translations by remember { mutableStateOf(repository.cachedTranslations()) }
    var selectedTranslation by remember { mutableStateOf(repository.selectedTranslation()) }
    var bookmarks by remember { mutableStateOf(repository.bookmarks()) }
    var annotations by remember { mutableStateOf(repository.annotations()) }
    val lastReading = remember(route) { repository.lastReading() }

    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { repository.refreshTranslations().getOrNull() }
        if (!fresh.isNullOrEmpty()) {
            translations = fresh
            selectedTranslation = fresh.firstOrNull { it.id == selectedTranslation.id } ?: selectedTranslation
        }
    }

    fun changeTranslation(next: BibleTranslation) {
        selectedTranslation = next
        repository.setSelectedTranslation(next)
    }

    BackHandler(enabled = route != BibleRoute.Hub) {
        route = when (val current = route) {
            BibleRoute.Search, BibleRoute.Bookmarks -> BibleRoute.Hub
            is BibleRoute.Chapters -> BibleRoute.Hub
            is BibleRoute.Reader -> BibleRoute.Chapters(current.book)
            BibleRoute.Hub -> BibleRoute.Hub
        }
    }

    when (val current = route) {
        BibleRoute.Hub -> BibleHub(
            translations = translations,
            selectedTranslation = selectedTranslation,
            lastReading = lastReading,
            bookmarkCount = bookmarks.size,
            onTranslation = ::changeTranslation,
            onBack = onBack,
            onSearch = { route = BibleRoute.Search },
            onBookmarks = { route = BibleRoute.Bookmarks },
            onOpenBook = { route = BibleRoute.Chapters(it) },
            onContinue = { position ->
                val book = bibleBookByName(position.book) ?: return@BibleHub
                route = BibleRoute.Reader(book, position.chapter, position.verse)
            },
        )
        BibleRoute.Search -> BibleSearch(
            repository = repository,
            translations = translations,
            selectedTranslation = selectedTranslation,
            bookmarks = bookmarks,
            onTranslation = ::changeTranslation,
            onBookmarksChanged = { bookmarks = it },
            onBack = { route = BibleRoute.Hub },
        )
        BibleRoute.Bookmarks -> BibleBookmarks(
            entries = bookmarks,
            onBack = { route = BibleRoute.Hub },
            onOpen = { bookmark ->
                val book = bibleBookByName(bookmark.book) ?: return@BibleBookmarks
                translations.firstOrNull { it.id == bookmark.translationId }?.let(::changeTranslation)
                route = BibleRoute.Reader(book, bookmark.chapter, bookmark.verse)
            },
        )
        is BibleRoute.Chapters -> ChapterPicker(
            book = current.book,
            selectedTranslation = selectedTranslation,
            translations = translations,
            onTranslation = ::changeTranslation,
            onBack = { route = BibleRoute.Hub },
            onChapter = { chapter -> route = BibleRoute.Reader(current.book, chapter) },
        )
        is BibleRoute.Reader -> BibleReader(
            repository = repository,
            book = current.book,
            chapter = current.chapter,
            initialVerse = current.initialVerse,
            selectedTranslation = selectedTranslation,
            translations = translations,
            bookmarks = bookmarks,
            annotations = annotations,
            onTranslation = ::changeTranslation,
            onBookmarksChanged = { bookmarks = it },
            onAnnotationsChanged = { annotations = it },
            onBack = { route = BibleRoute.Chapters(current.book) },
            onChapter = { chapter -> route = BibleRoute.Reader(current.book, chapter) },
        )
    }
}

@Composable
private fun BibleHub(
    translations: List<BibleTranslation>,
    selectedTranslation: BibleTranslation,
    lastReading: BibleReadingPosition?,
    bookmarkCount: Int,
    onTranslation: (BibleTranslation) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onOpenBook: (BibleBook) -> Unit,
    onContinue: (BibleReadingPosition) -> Unit,
) {
    var testament by remember { mutableStateOf<Testament?>(null) }
    val visibleBooks = remember(testament) {
        if (testament == null) SoraBibleBooks else SoraBibleBooks.filter { it.testament == testament }
    }

    Scaffold(
        containerColor = SoraBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Bible", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TranslationMenu(selectedTranslation, translations, onTranslation)
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Go to reference") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lastReading != null) {
                item {
                    Surface(
                        color = SoraSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
                        modifier = Modifier.fillMaxWidth().clickable { onContinue(lastReading) },
                    ) {
                        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).background(SoraSurfaceHigh, RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.MenuBook, null, tint = SoraAccent) }
                            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                                Text("Continue reading", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "${lastReading.book} ${lastReading.chapter}:${lastReading.verse} · ${selectedTranslation.shortLabel}",
                                    color = SoraMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = SoraMuted)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Books", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onBookmarks) {
                        Icon(Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (bookmarkCount == 0) "Bookmarks" else "Bookmarks · $bookmarkCount")
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BibleFilterChip("All", testament == null) { testament = null }
                    BibleFilterChip("Old Testament", testament == Testament.OLD) { testament = Testament.OLD }
                    BibleFilterChip("New Testament", testament == Testament.NEW) { testament = Testament.NEW }
                }
            }

            items(visibleBooks, key = { it.name }) { book ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenBook(book) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(book.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${book.chapters} chapter${if (book.chapters == 1) "" else "s"}", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = SoraMuted)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .28f))
            }
        }
    }
}

@Composable
private fun BibleFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) SoraText else SoraSurface,
        contentColor = if (selected) SoraBg else SoraText,
        shape = RoundedCornerShape(99.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun ChapterPicker(
    book: BibleBook,
    selectedTranslation: BibleTranslation,
    translations: List<BibleTranslation>,
    onTranslation: (BibleTranslation) -> Unit,
    onBack: () -> Unit,
    onChapter: (Int) -> Unit,
) {
    Scaffold(
        containerColor = SoraBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(book.name, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Choose a chapter", color = SoraMuted, fontSize = 10.sp)
                }
                TranslationMenu(selectedTranslation, translations, onTranslation)
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(68.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 100.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items((1..book.chapters).toList(), key = { it }) { chapter ->
                Surface(
                    color = SoraSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .06f)),
                    modifier = Modifier.aspectRatio(1f).clickable { onChapter(chapter) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(chapter.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BibleReader(
    repository: BibleRepository,
    book: BibleBook,
    chapter: Int,
    initialVerse: Int,
    selectedTranslation: BibleTranslation,
    translations: List<BibleTranslation>,
    bookmarks: List<BibleBookmark>,
    annotations: List<BibleAnnotation>,
    onTranslation: (BibleTranslation) -> Unit,
    onBookmarksChanged: (List<BibleBookmark>) -> Unit,
    onAnnotationsChanged: (List<BibleAnnotation>) -> Unit,
    onBack: () -> Unit,
    onChapter: (Int) -> Unit,
) {
    var passage by remember(book, chapter, selectedTranslation.id) { mutableStateOf<BiblePassage?>(null) }
    var loading by remember(book, chapter, selectedTranslation.id) { mutableStateOf(true) }
    var error by remember(book, chapter, selectedTranslation.id) { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var selectedVerse by remember(book, chapter, selectedTranslation.id) { mutableStateOf<BibleVerse?>(null) }
    var noteVerse by remember(book, chapter, selectedTranslation.id) { mutableStateOf<BibleVerse?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(book, chapter, selectedTranslation.id, refreshNonce) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            repository.loadChapter(book, chapter, selectedTranslation, forceRefresh = refreshNonce > 0)
        }
        passage = result.getOrNull()
        error = result.exceptionOrNull()?.message
        loading = false
        passage?.let {
            val index = it.verses.indexOfFirst { verse -> verse.number >= initialVerse }.coerceAtLeast(0)
            if (index > 0) listState.scrollToItem(index)
        }
    }

    LaunchedEffect(passage, book, chapter) {
        if (passage == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val verse = passage?.verses?.getOrNull(index)?.number ?: 1
                repository.saveLastReading(BibleReadingPosition(book.name, chapter, verse))
            }
    }

    Scaffold(
        containerColor = SoraBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("${book.name} $chapter", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(selectedTranslation.name, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TranslationMenu(selectedTranslation, translations, onTranslation)
            }
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SoraAccent)
            }
            error != null -> BibleErrorState(error.orEmpty()) { refreshNonce++ }
            passage != null -> {
                val current = passage!!
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(current.translation, color = SoraMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            if (current.fromCache) {
                                Text("Cached", color = SoraMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(current.verses, key = { "${it.chapter}:${it.number}" }) { verse ->
                        val saved = repository.isBookmarked(verse, selectedTranslation, bookmarks)
                        val annotation = repository.annotationFor(verse, selectedTranslation, annotations)
                        Surface(
                            color = if (annotation?.highlighted == true) SoraAccent.copy(alpha = .10f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedVerse = verse }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    verse.number.toString(),
                                    color = SoraAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(28.dp).padding(top = 5.dp),
                                )
                                Text(
                                    verse.text,
                                    fontSize = 17.sp,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!annotation?.note.isNullOrBlank()) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        "Verse has a note",
                                        tint = SoraAccent,
                                        modifier = Modifier.padding(top = 10.dp, end = 3.dp).size(15.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onBookmarksChanged(repository.toggleBookmark(verse, selectedTranslation)) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        if (saved) "Remove bookmark" else "Bookmark verse",
                                        tint = if (saved) SoraAccent else SoraMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { if (chapter > 1) onChapter(chapter - 1) },
                                enabled = chapter > 1,
                                modifier = Modifier.weight(1f),
                            ) { Icon(Icons.Rounded.ChevronLeft, null); Text("Previous") }
                            Button(
                                onClick = { if (chapter < book.chapters) onChapter(chapter + 1) },
                                enabled = chapter < book.chapters,
                                modifier = Modifier.weight(1f),
                            ) { Text("Next"); Icon(Icons.Rounded.ChevronRight, null) }
                        }
                    }
                }
            }
        }
    }

    selectedVerse?.let { verse ->
        val annotation = repository.annotationFor(verse, selectedTranslation, annotations)
        val saved = repository.isBookmarked(verse, selectedTranslation, bookmarks)
        ModalBottomSheet(
            onDismissRequest = { selectedVerse = null },
            containerColor = SoraSurface,
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                Text("${verse.book} ${verse.chapter}:${verse.number}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(verse.text, color = SoraMuted, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAnnotationsChanged(repository.toggleHighlight(verse, selectedTranslation)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (annotation?.highlighted == true) "Unhighlight" else "Highlight") }
                    Button(
                        onClick = {
                            noteDraft = annotation?.note.orEmpty()
                            noteVerse = verse
                            selectedVerse = null
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (annotation?.note.isNullOrBlank()) "Add note" else "Edit note") }
                }
                TextButton(
                    onClick = { onBookmarksChanged(repository.toggleBookmark(verse, selectedTranslation)) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saved) "Remove bookmark" else "Bookmark verse")
                }
            }
        }
    }

    noteVerse?.let { verse ->
        val existing = repository.annotationFor(verse, selectedTranslation, annotations)
        AlertDialog(
            onDismissRequest = { noteVerse = null },
            title = { Text("${verse.book} ${verse.chapter}:${verse.number}") },
            text = {
                Column {
                    Text(verse.text, color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("Note") },
                        minLines = 3,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAnnotationsChanged(repository.saveNote(verse, selectedTranslation, noteDraft))
                    noteVerse = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (!existing?.note.isNullOrBlank()) {
                        TextButton(onClick = {
                            onAnnotationsChanged(repository.saveNote(verse, selectedTranslation, ""))
                            noteVerse = null
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { noteVerse = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun BibleSearch(
    repository: BibleRepository,
    translations: List<BibleTranslation>,
    selectedTranslation: BibleTranslation,
    bookmarks: List<BibleBookmark>,
    onTranslation: (BibleTranslation) -> Unit,
    onBookmarksChanged: (List<BibleBookmark>) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var passage by remember { mutableStateOf<BiblePassage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableIntStateOf(0) }

    LaunchedEffect(request) {
        if (request == 0 || query.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) { repository.lookup(query, selectedTranslation) }
        passage = result.getOrNull()
        error = result.exceptionOrNull()?.message
        loading = false
    }

    Scaffold(
        containerColor = SoraBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Go to reference", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TranslationMenu(selectedTranslation, translations, onTranslation)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = SoraSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = SoraText, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("John 3:16, Psalm 23…", color = SoraMuted, fontSize = 15.sp)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { if (query.isNotBlank()) request++ }) { Icon(Icons.Rounded.Search, "Open reference") }
            }
            Text(
                "Search uses Bible references, not keyword matching.",
                color = SoraMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
            )
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SoraAccent) }
                error != null -> BibleErrorState(error.orEmpty()) { request++ }
                passage != null -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    item {
                        Text(passage!!.reference, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(passage!!.translation, color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    }
                    items(passage!!.verses, key = { "${it.book}:${it.chapter}:${it.number}" }) { verse ->
                        val saved = repository.isBookmarked(verse, selectedTranslation, bookmarks)
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                            Text(verse.number.toString(), color = SoraAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp).padding(top = 5.dp))
                            Text(verse.text, fontSize = 17.sp, lineHeight = 28.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { onBookmarksChanged(repository.toggleBookmark(verse, selectedTranslation)) },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                    null,
                                    tint = if (saved) SoraAccent else SoraMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BibleBookmarks(
    entries: List<BibleBookmark>,
    onBack: () -> Unit,
    onOpen: (BibleBookmark) -> Unit,
) {
    Scaffold(
        containerColor = SoraBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Bookmarks", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.BookmarkBorder, null, tint = SoraMuted, modifier = Modifier.size(42.dp))
                Text("No bookmarked verses yet", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text("Tap the bookmark beside a verse while reading.", color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    Surface(
                        color = SoraSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) },
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${entry.book} ${entry.chapter}:${entry.verse}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                Text(entry.translationId.uppercase(), color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                            Text(entry.text, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationMenu(
    selected: BibleTranslation,
    translations: List<BibleTranslation>,
    onSelected: (BibleTranslation) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visibleTranslations = remember(query, translations) {
        val needle = query.trim()
        if (needle.isBlank()) translations else translations.filter { translation ->
            translation.shortLabel.contains(needle, ignoreCase = true) ||
                translation.name.contains(needle, ignoreCase = true) ||
                translation.language.contains(needle, ignoreCase = true)
        }
    }

    Surface(
        color = SoraSurfaceHigh,
        contentColor = SoraText,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable {
            query = ""
            sheetOpen = true
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.shortLabel, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, "Choose Bible version", modifier = Modifier.size(16.dp))
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            containerColor = SoraSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            ) {
                Text("Select version", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Choose the translation used across the Bible reader.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )

                Surface(
                    color = SoraSurfaceHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = SoraMuted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = SoraText, fontSize = 14.sp),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Search versions", color = SoraMuted, fontSize = 14.sp)
                                inner()
                            },
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Close, "Clear search", tint = SoraMuted, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }

                Text(
                    if (query.isBlank()) "All versions" else "Results · ${visibleTranslations.size}",
                    color = SoraMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 17.dp, bottom = 6.dp),
                )

                if (visibleTranslations.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.SearchOff, null, tint = SoraMuted, modifier = Modifier.size(32.dp))
                        Text("No versions found", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp))
                        Text("Try another name or abbreviation.", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(visibleTranslations, key = { it.id }) { translation ->
                            val isSelected = translation.id == selected.id
                            Surface(
                                color = if (isSelected) SoraSurfaceHigh else Color.Transparent,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSelected(translation)
                                    sheetOpen = false
                                },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(38.dp).background(
                                            if (isSelected) SoraAccent.copy(alpha = .14f) else Color.White.copy(alpha = .045f),
                                            RoundedCornerShape(11.dp),
                                        ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            translation.shortLabel.take(5),
                                            color = if (isSelected) SoraAccent else SoraText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(
                                            translation.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${translation.shortLabel} · ${translation.language}",
                                            color = SoraMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, "Selected", tint = SoraAccent, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BibleErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = SoraMuted, modifier = Modifier.size(42.dp))
        Text("Couldn’t load this passage", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(message, color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
    }
}

/** Small embedded entry point used by Media until the full Bible route is opened. */
@Composable
fun BibleHubContent(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.MenuBook, null, tint = SoraAccent, modifier = Modifier.size(44.dp))
        Text("Bible reader", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
        Text("Choose a book and translation, keep your place, and bookmark verses.", color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        Button(onClick = onOpen, modifier = Modifier.padding(top = 18.dp), shape = RoundedCornerShape(10.dp)) {
            Text("Open Bible")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
        }
    }
}
