package app.yomi.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yomi.reader.local.ArchiveScanResult
import app.yomi.reader.local.LibraryAvailability
import app.yomi.reader.local.LibraryBook
import app.yomi.reader.local.LibraryLocationType
import app.yomi.reader.local.LocalLibraryStore
import app.yomi.reader.local.ZipArchiveScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val libraryStore by lazy { LocalLibraryStore(this) }
    private val libraryRevision = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(STARTUP_TAG, "activity-created")
        enableEdgeToEdge()
        setContent {
            YomiTheme {
                YomiHome()
            }
        }
        Log.i(STARTUP_TAG, "compose-content-attached")
    }

    override fun onResume() {
        super.onResume()
        libraryRevision.intValue += 1
    }

    private fun persistSelection(uri: Uri, kind: String, pageCount: Int? = null): LibraryBook {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // A few document providers grant durable access without accepting this explicit call.
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
            Log.i(STARTUP_TAG, "home-content-composed sections=Library,Recent,Folders")
        }
        var showAddSheet by remember { mutableStateOf(false) }
        var section by remember { mutableStateOf(HomeSection.LIBRARY) }
        var importError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val openBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    when (val scan = withContext(Dispatchers.IO) { inspectArchive(uri) }) {
                        is ArchiveScanResult.Success -> {
                            persistSelection(uri, "archive", scan.catalog.pages.size)
                            library = libraryStore.list()
                            importError = null
                        }
                        is ArchiveScanResult.Rejected -> importError = scan.reason
                    }
                }
            }
        }

        val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching { persistSelection(uri, "folder") }
                    .onSuccess {
                        library = libraryStore.list()
                        importError = null
                    }
                    .onFailure { error ->
                        importError = error.message ?: "folder-permission"
                    }
            }
        }

        val recent = library
            .filter { it.lastOpenedEpochMillis != null }
            .sortedByDescending { it.lastOpenedEpochMillis ?: 0L }
        val folders = library.filter { it.locationType == LibraryLocationType.TREE }
        val continueReading = recent.firstOrNull()

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(26.dp))
                Header(onAdd = { showAddSheet = true })
                HomeTabs(section = section, onChange = { section = it })

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (section) {
                        HomeSection.LIBRARY -> {
                            if (library.isEmpty()) {
                                item {
                                    EmptyLibrary(
                                        onOpenBook = {
                                            openBook.launch(arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "*/*"))
                                        },
                                        onAddFolder = { addFolder.launch(null) },
                                    )
                                }
                            } else {
                                continueReading?.let { book ->
                                    item { ContinueReading(book, onOpen = { openReader(book) }) }
                                }
                                item { SectionTitle("Library") }
                                items(items = library, key = { it.id.value }) { book ->
                                    LibraryItem(book, onOpen = { openReader(book) })
                                }
                            }
                        }

                        HomeSection.RECENT -> {
                            item { SectionTitle("Recent") }
                            if (recent.isEmpty()) {
                                item { EmptyMessage("Nothing read yet", "Books appear here after you open them.") }
                            } else {
                                items(items = recent, key = { it.id.value }) { book ->
                                    LibraryItem(book, onOpen = { openReader(book) })
                                }
                            }
                        }

                        HomeSection.FOLDERS -> {
                            item { SectionTitle("Folders") }
                            if (folders.isEmpty()) {
                                item {
                                    EmptyFolders(onAddFolder = { addFolder.launch(null) })
                                }
                            } else {
                                items(items = folders, key = { it.id.value }) { book ->
                                    LibraryItem(book, onOpen = { openReader(book) })
                                }
                            }
                        }

                        HomeSection.SETTINGS -> {
                            item { PlaceholderSection(section) }
                        }
                    }

                    importError?.let { error ->
                        item {
                            Text(
                                text = "Could not import: $error",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Add to Yomi", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    SheetAction("Open a book", "CBZ or ZIP") {
                        showAddSheet = false
                        openBook.launch(arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "*/*"))
                    }
                    SheetAction("Add a folder", "Images and chapter folders") {
                        showAddSheet = false
                        addFolder.launch(null)
                    }
                    Spacer(Modifier.height(24.dp))
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
                        if (view.viewTreeObserver.isAlive) {
                            view.viewTreeObserver.removeOnDrawListener(this)
                        }
                    }
                }
            }
            view.viewTreeObserver.addOnDrawListener(listener)
            onDispose {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnDrawListener(listener)
                }
            }
        }
    }

    @Composable
    private fun Header(onAdd: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Yomi",
                modifier = Modifier.weight(1f),
                fontSize = 31.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp,
            )
            TextButton(onClick = {}) { Text("Search") }
            TextButton(onClick = onAdd) { Text("Add") }
        }
    }

    @Composable
    private fun HomeTabs(section: HomeSection, onChange: (HomeSection) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeSection.entries.forEach { item ->
                val active = item == section
                Text(
                    text = item.label,
                    modifier = Modifier
                        .clickable { onChange(item) }
                        .background(
                            if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun EmptyLibrary(onOpenBook: () -> Unit, onAddFolder: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(86.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Y", fontSize = 42.sp, fontWeight = FontWeight.Black)
            }
            Text("Your library is empty.", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Open a local book or add a folder. Yomi keeps reading local.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenBook) { Text("Open a book") }
            Button(
                onClick = onAddFolder,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) { Text("Add a folder") }
        }
    }

    @Composable
    private fun EmptyFolders(onAddFolder: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("No folders yet", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Add an image folder and Yomi will keep its SAF permission for later reading.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAddFolder) { Text("Add a folder") }
        }
    }

    @Composable
    private fun EmptyMessage(title: String, subtitle: String) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Continue Reading", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverPlaceholder()
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        if (item.locationType == LibraryLocationType.TREE) "Local folder" else "Local archive",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        progressText(item),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    @Composable
    private fun LibraryItem(item: LibraryBook, onOpen: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = item.availability == LibraryAvailability.AVAILABLE, onClick = onOpen)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverPlaceholder()
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(
                    when (item.availability) {
                        LibraryAvailability.AVAILABLE -> progressText(item)
                        LibraryAvailability.UNAVAILABLE -> "File or folder is unavailable"
                        LibraryAvailability.PERMISSION_LOST -> "Storage permission needs to be restored"
                    },
                    color = if (item.availability == LibraryAvailability.AVAILABLE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }

    private fun progressText(item: LibraryBook): String {
        val percent = (item.progress * 100).toInt().coerceIn(0, 100)
        val pageCount = item.pageCount?.let { "$it pages · " } ?: ""
        return pageCount + "$percent% read"
    }

    @Composable
    private fun CoverPlaceholder() {
        Box(
            modifier = Modifier.width(72.dp).height(98.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Y", fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
    }

    @Composable
    private fun PlaceholderSection(section: HomeSection) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(section.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Reader settings stay local to Yomi.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun SheetAction(title: String, subtitle: String, onClick: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    private companion object {
        const val STARTUP_TAG = "YomiStartup"
    }

    private enum class HomeSection(val label: String) {
        LIBRARY("Library"),
        RECENT("Recent"),
        FOLDERS("Folders"),
        SETTINGS("Settings"),
    }
}

@Composable
private fun YomiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
