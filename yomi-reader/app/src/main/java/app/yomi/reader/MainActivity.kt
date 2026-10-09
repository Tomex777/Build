package app.yomi.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yomi.reader.local.ArchiveScanResult
import app.yomi.reader.local.LibraryAvailability
import app.yomi.reader.local.LibraryBook
import app.yomi.reader.local.LibraryLocationType
import app.yomi.reader.core.ReaderBookId
import app.yomi.reader.core.ReaderPage
import app.yomi.reader.core.ReaderPageSource
import app.yomi.reader.local.LocalBookIdentityStore
import app.yomi.reader.local.LocalLibraryStore
import app.yomi.reader.local.ReaderBookmarkStore
import app.yomi.reader.local.SharedPreferencesProgressSink
import app.yomi.reader.local.TreeBookCatalog
import app.yomi.reader.local.ZipArchiveScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val libraryStore by lazy { LocalLibraryStore(this) }
    private val identityStore by lazy { LocalBookIdentityStore(this) }
    private val bookmarkStore by lazy { ReaderBookmarkStore(this) }
    private val progressStore by lazy { SharedPreferencesProgressSink(this) }
    private val libraryRevision = mutableIntStateOf(0)
    private val incomingArchiveUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(STARTUP_TAG, "activity-created")
        incomingArchiveUri.value = archiveUriFromIntent(intent)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            YomiTheme {
                YomiHome()
            }
        }
        Log.i(STARTUP_TAG, "compose-content-attached")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingArchiveUri.value = archiveUriFromIntent(intent)
    }

    private fun archiveUriFromIntent(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
            ?.takeIf { it.scheme == "content" || it.scheme == "file" }

    override fun onResume() {
        super.onResume()
        libraryRevision.intValue += 1
    }

    private fun persistSelection(uri: Uri, kind: String, pageCount: Int? = null): LibraryBook {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers grant durable access without accepting this explicit call.
        }

        val title = if (kind == "folder") {
            uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Folder"
        } else {
            queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Book"
        }

        return libraryStore.upsert(
            uri = uri,
            title = title,
            locationType = if (kind == "folder") LibraryLocationType.TREE else LibraryLocationType.DOCUMENT,
            pageCount = pageCount,
        )
    }

    private fun inspectArchive(uri: Uri): ArchiveScanResult {
        val stream = contentResolver.openInputStream(uri)
            ?: return ArchiveScanResult.Rejected("cannot-open")
        return stream.use(ZipArchiveScanner::scan)
    }

    private suspend fun importArchive(uri: Uri): LibraryBook {
        val catalog = when (val scan = withContext(Dispatchers.IO) { inspectArchive(uri) }) {
            is ArchiveScanResult.Success -> scan.catalog
            is ArchiveScanResult.Rejected -> throw IllegalArgumentException("cbz:" + scan.reason)
        }
        val book = persistSelection(uri, "archive", catalog.pages.size)
        val coverUri = withContext(Dispatchers.IO) {
            createCoverThumbnail(uri, catalog.pages.first().name, book.id.value)
        }
        if (coverUri != null) {
            libraryStore.upsert(
                uri = uri,
                title = book.title,
                locationType = LibraryLocationType.DOCUMENT,
                pageCount = catalog.pages.size,
                coverUri = coverUri,
            )
        }
        return book
    }

    private fun createCoverThumbnail(uri: Uri, pageName: String, bookId: String): String? {
        return runCatching {
            val input = contentResolver.openInputStream(uri) ?: return@runCatching null
            val pageBytes = input.use { ZipArchiveScanner.readPage(it, pageName) }
            val bytes = (pageBytes as? app.yomi.reader.local.ArchivePageRead.Success)?.bytes ?: return@runCatching null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 250_000_000L) return@runCatching null
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 640) sampleSize *= 2
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return@runCatching null

            val directory = java.io.File(filesDir, "covers").apply { mkdirs() }
            val filename = bookId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "book" } + ".jpg"
            val file = java.io.File(directory, filename)
            file.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it) }
            bitmap.recycle()
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private suspend fun inspectFolder(
        uri: Uri,
        bookId: ReaderBookId,
        title: String,
    ): FolderInspection {
        val bindings = TreeBookCatalog(contentResolver, uri).chapters(bookId, title)
        require(bindings.isNotEmpty()) { "No supported images or chapters found" }

        var pageCount = 0
        var coverUri: String? = null
        try {
            bindings.forEach { binding ->
                val pages = binding.source.pages(binding.chapter)
                pageCount += pages.size
                if (coverUri == null) {
                    val firstPage = pages.firstOrNull()
                    if (firstPage != null) {
                        coverUri = createCoverThumbnail(
                            source = binding.source,
                            page = firstPage,
                            bookId = bookId.value,
                        )
                    }
                }
            }
        } finally {
            bindings.forEach { runCatching { it.source.close() } }
        }
        require(pageCount > 0) { "No supported images found" }
        return FolderInspection(pageCount = pageCount, coverUri = coverUri)
    }

    private suspend fun createCoverThumbnail(
        source: ReaderPageSource,
        page: ReaderPage,
        bookId: String,
    ): String? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            source.open(page).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (
                bounds.outWidth <= 0 ||
                bounds.outHeight <= 0 ||
                bounds.outWidth.toLong() * bounds.outHeight > 250_000_000L
            ) {
                return@runCatching null
            }

            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 640) sampleSize *= 2
            val bitmap = source.open(page).use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                )
            } ?: return@runCatching null

            saveCoverBitmap(bitmap, bookId)
        }.getOrNull()
    }

    private fun saveCoverBitmap(bitmap: Bitmap, bookId: String): String? {
        return runCatching {
            val directory = java.io.File(filesDir, "covers").apply { mkdirs() }
            val filename = bookId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                .ifBlank { "book" } + ".jpg"
            val file = java.io.File(directory, filename)
            file.outputStream().buffered().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it)
            }
            bitmap.recycle()
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun folderTitle(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() } ?: "Folder"
    }

    private fun openReader(item: LibraryBook) {
        libraryStore.markOpened(item.id)
        startActivity(
            ReaderActivity.newIntent(
                context = this,
                uri = item.locationUri,
                kind = if (item.locationType == LibraryLocationType.TREE) "folder" else "archive",
                title = item.title,
            ),
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment?.substringAfterLast('/')
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun YomiHome() {
        val revision = libraryRevision.intValue
        var library by remember(revision) { mutableStateOf(libraryStore.list()) }
        StartupDrawProbe()

        LaunchedEffect(Unit) {
            Log.i(STARTUP_TAG, "home-content-composed sections=Home,Folders")
        }

        var showAddSheet by remember { mutableStateOf(false) }
        var showSortSheet by remember { mutableStateOf(false) }
        var actionBook by remember { mutableStateOf<LibraryBook?>(null) }
        var pendingRelink by remember { mutableStateOf<LibraryBook?>(null) }
        var sortMode by remember { mutableStateOf(LibrarySort.RECENT) }
        var destination by remember { mutableStateOf(HomeDestination.HOME) }
        var searchQuery by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        var importingArchive by remember { mutableStateOf(false) }

        suspend fun importAndOpenArchive(uri: Uri) {
            if (importingArchive) return
            importingArchive = true
            importError = null
            try {
                val book = importArchive(uri)
                library = libraryStore.list()
                Log.i(STARTUP_TAG, "archive-imported-and-opening title=${book.title}")
                openReader(book)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e(STARTUP_TAG, "archive-import-or-open-failed", error)
                importError = when {
                    error is SecurityException ->
                        "Yomi can't access this file. Choose it again from your device's files."
                    error.message == "cbz:no-image-pages" ->
                        "This archive has no JPG, PNG, or WebP pages to read."
                    error is IllegalArgumentException && error.message?.startsWith("cbz:") == true ->
                        "This CBZ/ZIP couldn't be read. It may be damaged, encrypted, or too large."
                    else -> "Couldn't open this CBZ/ZIP. Try another file or check that it isn't damaged."
                }
            } finally {
                importingArchive = false
            }
        }

        val openBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch { importAndOpenArchive(uri) }
            }
        }

        // ACTION_VIEW files from Android Files must use the same validation and reader
        // launch as CBZ documents chosen through Yomi's own "Open a book" button.
        val incoming = incomingArchiveUri.value
        LaunchedEffect(incoming) {
            if (incoming != null) {
                importAndOpenArchive(incoming)
                incomingArchiveUri.value = null
            }
        }

        val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                scope.launch {
                    val title = folderTitle(uri)
                    val bookId = identityStore.getOrCreate(uri)
                    runCatching {
                        withContext(Dispatchers.IO) { inspectFolder(uri, bookId, title) }
                    }.onSuccess { inspection ->
                        val book = persistSelection(uri, "folder", inspection.pageCount)
                        if (inspection.coverUri != null) {
                            libraryStore.upsert(
                                uri = uri,
                                title = book.title,
                                locationType = LibraryLocationType.TREE,
                                pageCount = inspection.pageCount,
                                coverUri = inspection.coverUri,
                            )
                        }
                        library = libraryStore.list()
                        importError = null
                    }.onFailure {
                        importError = "Choose a folder that contains supported images or chapter archives."
                    }
                }
            }
        }

        val relinkBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val item = pendingRelink
            pendingRelink = null
            if (uri != null && item != null) {
                scope.launch {
                    when (val scan = withContext(Dispatchers.IO) { inspectArchive(uri) }) {
                        is ArchiveScanResult.Success -> {
                            runCatching {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val title = queryDisplayName(uri) ?: item.title
                            val coverUri = withContext(Dispatchers.IO) {
                                createCoverThumbnail(uri, scan.catalog.pages.first().name, item.id.value)
                            }
                            runCatching {
                                libraryStore.relink(
                                    id = item.id,
                                    uri = uri,
                                    title = title,
                                    pageCount = scan.catalog.pages.size,
                                    coverUri = coverUri,
                                )
                            }.onSuccess {
                                library = libraryStore.list()
                                importError = null
                            }.onFailure {
                                importError = "Yomi couldn’t reconnect this book. Try choosing it again."
                            }
                        }
                        is ArchiveScanResult.Rejected -> {
                            importError = "Choose the CBZ or ZIP file for this book."
                        }
                    }
                }
            }
        }

        val relinkFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val item = pendingRelink
            pendingRelink = null
            if (uri != null && item != null) {
                scope.launch {
                    val title = folderTitle(uri)
                    runCatching {
                        withContext(Dispatchers.IO) { inspectFolder(uri, item.id, title) }
                    }.onSuccess { inspection ->
                        runCatching {
                            try {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: SecurityException) {
                                // Some document providers grant durable access without accepting this call.
                            }
                            libraryStore.relink(
                                id = item.id,
                                uri = uri,
                                title = title,
                                pageCount = inspection.pageCount,
                                coverUri = inspection.coverUri,
                            )
                        }.onSuccess {
                            library = libraryStore.list()
                            importError = null
                        }.onFailure {
                            importError = "Yomi couldn’t reconnect this folder. Choose it again and allow access."
                        }
                    }.onFailure {
                        importError = "Choose the folder that contains this book’s images or chapter archives."
                    }
                }
            }
        }

        fun startRelink(item: LibraryBook) {
            pendingRelink = item
            actionBook = null
            if (item.locationType == LibraryLocationType.TREE) {
                relinkFolder.launch(null)
            } else {
                relinkBook.launch(SUPPORTED_BOOK_TYPES)
            }
        }

        val recent = library.filter { it.lastOpenedEpochMillis != null }
            .sortedByDescending { it.lastOpenedEpochMillis ?: 0L }
        val sortedLibrary = when (sortMode) {
            LibrarySort.RECENT -> library.sortedWith(
                compareByDescending<LibraryBook> { it.lastOpenedEpochMillis ?: Long.MIN_VALUE }
                    .thenByDescending { it.dateAddedEpochMillis },
            )
            LibrarySort.TITLE -> library.sortedBy { it.title.lowercase() }
            LibrarySort.ADDED -> library.sortedByDescending { it.dateAddedEpochMillis }
        }
        val folders = sortedLibrary.filter { it.locationType == LibraryLocationType.TREE }
        val currentBook = recent.firstOrNull { it.progress < 1.0 }
        val visibleLibrary = sortedLibrary.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }

        val openOrRecover: (LibraryBook) -> Unit = { item ->
            if (item.availability == LibraryAvailability.AVAILABLE) {
                openReader(item)
            } else {
                actionBook = item
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Header(
                    searchMode = destination == HomeDestination.SEARCH,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearch = { destination = HomeDestination.SEARCH },
                    onCloseSearch = {
                        destination = HomeDestination.HOME
                        searchQuery = ""
                    },
                    onAdd = { showAddSheet = true },
                )
                if (importingArchive) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Opening CBZ/ZIP…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                when (destination) {
                    HomeDestination.HOME -> HomeContent(
                        modifier = Modifier.weight(1f),
                        library = sortedLibrary,
                        recent = recent,
                        currentBook = currentBook,
                        sortLabel = sortMode.label,
                        importError = importError,
                        onOpen = openOrRecover,
                        onManage = { actionBook = it },
                        onSort = { showSortSheet = true },
                        onOpenBook = { openBook.launch(SUPPORTED_BOOK_TYPES) },
                    )
                    HomeDestination.FOLDERS -> FoldersContent(
                        modifier = Modifier.weight(1f),
                        folders = folders,
                        importError = importError,
                        onOpen = openOrRecover,
                        onManage = { actionBook = it },
                        onAddFolder = { addFolder.launch(null) },
                    )
                    HomeDestination.SEARCH -> SearchContent(
                        modifier = Modifier.weight(1f),
                        query = searchQuery,
                        books = visibleLibrary,
                        onOpen = openOrRecover,
                        onManage = { actionBook = it },
                    )
                }

                if (destination == HomeDestination.HOME || destination == HomeDestination.FOLDERS) {
                    BottomNavigation(destination = destination, onChange = { destination = it })
                }
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Add to Yomi", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    SheetAction("Open a book", "CBZ or ZIP archive") {
                        showAddSheet = false
                        openBook.launch(SUPPORTED_BOOK_TYPES)
                    }
                    SheetAction("Add a folder", "Images and chapter folders") {
                        showAddSheet = false
                        addFolder.launch(null)
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }
        }

        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Sort library", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    LibrarySort.entries.forEach { option ->
                        SheetAction(
                            option.label,
                            if (option == sortMode) "Selected" else option.description,
                        ) {
                            sortMode = option
                            showSortSheet = false
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }

        actionBook?.let { item ->
            ModalBottomSheet(
                onDismissRequest = { actionBook = null },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.availability != LibraryAvailability.AVAILABLE) {
                        SheetAction(
                            "Find again",
                            if (item.locationType == LibraryLocationType.TREE) {
                                "Choose the folder again and keep your reading progress"
                            } else {
                                "Choose the book file again and keep your reading progress"
                            },
                        ) {
                            startRelink(item)
                        }
                    }
                    SheetAction("Remove from library", "The original file or folder will not be deleted") {
                        val result = runCatching {
                            libraryStore.remove(item.id)
                            bookmarkStore.clear(item.id)
                            progressStore.clear(item.id)
                            identityStore.forget(item.id)
                            item.coverUri?.let { Uri.parse(it).path }?.let { java.io.File(it).delete() }
                        }
                        library = libraryStore.list()
                        actionBook = null
                        if (result.isFailure) {
                            importError = "The book was removed, but Yomi couldn’t clear all of its saved reading data."
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    @Composable
    private fun HomeContent(
        modifier: Modifier,
        library: List<LibraryBook>,
        recent: List<LibraryBook>,
        currentBook: LibraryBook?,
        sortLabel: String,
        importError: String?,
        onOpen: (LibraryBook) -> Unit,
        onManage: (LibraryBook) -> Unit,
        onSort: () -> Unit,
        onOpenBook: () -> Unit,
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (library.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        EmptyHome(onOpenBook = onOpenBook)
                    }
                }
            } else {
                currentBook?.let { book -> item { ContinueReading(book, onOpen = { onOpen(book) }) } }

                if (recent.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Recently opened")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recent.take(5), key = { "recent-${it.id.value}" }) { book ->
                                    RecentBookCard(book, onOpen = { onOpen(book) })
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) { SectionTitle("Your library") }
                        Text(
                            sortLabel,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onSort)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                items(library.chunked(2), key = { row -> row.joinToString { it.id.value } }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { book ->
                            LibraryGridItem(
                                item = book,
                                modifier = Modifier.weight(1f),
                                onOpen = { onOpen(book) },
                                onManage = { onManage(book) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            importError?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    @Composable
    private fun FoldersContent(
        modifier: Modifier,
        folders: List<LibraryBook>,
        importError: String?,
        onOpen: (LibraryBook) -> Unit,
        onManage: (LibraryBook) -> Unit,
        onAddFolder: () -> Unit,
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionTitle("Folders") }
            if (folders.isEmpty()) {
                item { EmptyFolders(onAddFolder = onAddFolder) }
            } else {
                items(folders, key = { it.id.value }) { book ->
                    LibraryListItem(book, onOpen = { onOpen(book) }, onManage = { onManage(book) })
                }
            }
            importError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        }
    }

    @Composable
    private fun SearchContent(
        modifier: Modifier,
        query: String,
        books: List<LibraryBook>,
        onOpen: (LibraryBook) -> Unit,
        onManage: (LibraryBook) -> Unit,
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (query.isBlank()) {
                item { Text("Search books and folders in your library.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (books.isEmpty()) {
                item { EmptyMessage("No matches", "Try another title or folder name.") }
            } else {
                item { SectionTitle("${books.size} result${if (books.size == 1) "" else "s"}") }
                items(books, key = { "search-${it.id.value}" }) { book ->
                    LibraryListItem(book, onOpen = { onOpen(book) }, onManage = { onManage(book) })
                }
            }
        }
    }

    @Composable
    private fun StartupDrawProbe() {
        val view = LocalView.current
        DisposableEffect(view) {
            var scheduled = false
            val listener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (scheduled) return
                    scheduled = true
                    view.post {
                        Log.i(STARTUP_TAG, "home-first-draw")
                        reportFullyDrawn()
                        if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this)
                    }
                }
            }
            view.viewTreeObserver.addOnDrawListener(listener)
            onDispose {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
            }
        }
    }

    @Composable
    private fun Header(
        searchMode: Boolean,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        onSearch: () -> Unit,
        onCloseSearch: () -> Unit,
        onAdd: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchMode) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search books and folders") },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                IconAction(R.drawable.ic_yomi_close, "Close search", onCloseSearch)
            } else {
                Text(
                    text = "Yomi",
                    modifier = Modifier.weight(1f),
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp,
                )
                IconAction(R.drawable.ic_yomi_search, "Search library", onSearch)
                IconAction(R.drawable.ic_yomi_add, "Add to library", onAdd)
            }
        }
    }

    @Composable
    private fun IconAction(icon: Int, description: String, onClick: () -> Unit) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(painter = painterResource(icon), contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }

    @Composable
    private fun BottomNavigation(destination: HomeDestination, onChange: (HomeDestination) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 28.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavigationItem("Home", R.drawable.ic_yomi_home, destination == HomeDestination.HOME) { onChange(HomeDestination.HOME) }
            NavigationItem("Folders", R.drawable.ic_yomi_folder, destination == HomeDestination.FOLDERS) { onChange(HomeDestination.FOLDERS) }
        }
    }

    @Composable
    private fun NavigationItem(label: String, icon: Int, active: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
                .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun EmptyHome(onOpenBook: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Y", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(2.dp))
            Text("Your library is empty", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Open a CBZ, ZIP, or image folder to start reading.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenBook, modifier = Modifier.padding(top = 4.dp)) { Text("Open a book") }
        }
    }

    @Composable
    private fun EmptyFolders(onAddFolder: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            Text("No folders yet", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Add an image folder to read it in Yomi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAddFolder) { Text("Add a folder") }
        }
    }

    @Composable
    private fun EmptyMessage(title: String, subtitle: String) {
        Column(modifier = Modifier.padding(vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }

    @Composable
    private fun ContinueReading(item: LibraryBook, onOpen: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Continue reading")
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen)
                    .background(MaterialTheme.colorScheme.surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverPlaceholder(84.dp, 112.dp, item.title, item.coverUri)
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(resumePosition(item), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(progressText(item), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }

    @Composable
    private fun RecentBookCard(item: LibraryBook, onOpen: () -> Unit) {
        Column(
            modifier = Modifier.width(134.dp).clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CoverPlaceholder(134.dp, 154.dp, item.title, item.coverUri)
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun LibraryGridItem(
        item: LibraryBook,
        modifier: Modifier,
        onOpen: () -> Unit,
        onManage: () -> Unit,
    ) {
        Column(
            modifier = modifier.clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onManage,
                ),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CoverPlaceholder(Modifier.fillMaxWidth().height(204.dp), item.title, item.coverUri)
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (item.availability == LibraryAvailability.AVAILABLE) progressText(item) else "Unavailable",
                color = if (item.availability == LibraryAvailability.AVAILABLE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun LibraryListItem(
        item: LibraryBook,
        onOpen: () -> Unit,
        onManage: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onManage,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverPlaceholder(64.dp, 84.dp, item.title, item.coverUri)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (item.availability) {
                        LibraryAvailability.AVAILABLE -> progressText(item)
                        LibraryAvailability.UNAVAILABLE -> "Unavailable · tap to find again"
                        LibraryAvailability.PERMISSION_LOST -> "Access needed · tap to find again"
                    },
                    color = if (item.availability == LibraryAvailability.AVAILABLE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }
    }

    @Composable
    private fun CoverPlaceholder(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, title: String, coverUri: String?) {
        CoverPlaceholder(Modifier.width(width).height(height), title, coverUri)
    }

    @Composable
    private fun CoverPlaceholder(modifier: Modifier, title: String, coverUri: String?) {
        val bitmap by produceState<Bitmap?>(null, coverUri) {
            value = coverUri?.let { uri ->
                withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(Uri.parse(uri).path) }.getOrNull()
                }
            }
        }
        Box(
            modifier = modifier.clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap!!.asImageBitmap(), contentDescription = "$title cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(title.take(1).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    private fun progressText(item: LibraryBook): String {
        val percent = (item.progress * 100).toInt().coerceIn(0, 100)
        val pageCount = item.pageCount?.let { "$it pages · " } ?: ""
        return pageCount + "$percent% read"
    }

    private fun resumePosition(item: LibraryBook): String {
        val pageCount = item.pageCount ?: return "Resume where you left off"
        if (pageCount <= 0) return "Resume where you left off"
        val page = (item.progress * (pageCount - 1)).toInt().coerceIn(0, pageCount - 1) + 1
        return "Resume at page $page of $pageCount"
    }

    @Composable
    private fun SheetAction(title: String, subtitle: String, onClick: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }

    private data class FolderInspection(
        val pageCount: Int,
        val coverUri: String?,
    )

    private companion object {
        const val STARTUP_TAG = "YomiStartup"
        val SUPPORTED_BOOK_TYPES = arrayOf(
            "application/zip",
            "application/x-cbz",
            "application/vnd.comicbook+zip",
            "*/*",
        )
    }

    private enum class HomeDestination { HOME, FOLDERS, SEARCH }

    private enum class LibrarySort(
        val label: String,
        val description: String,
    ) {
        RECENT("Recent", "Most recently opened first"),
        TITLE("Title", "A to Z"),
        ADDED("Added", "Newest additions first"),
    }
}

@Composable
private fun YomiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFD4E4FF),
            onPrimary = Color(0xFF15223A),
            background = Color(0xFF0A0D12),
            onBackground = Color(0xFFE7ECF5),
            surface = Color(0xFF111722),
            onSurface = Color(0xFFE7ECF5),
            surfaceVariant = Color(0xFF1A2230),
            onSurfaceVariant = Color(0xFFAAB6C9),
        ),
        content = content,
    )
}
