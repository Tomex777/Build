package app.yomi.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import app.yomi.reader.local.ArchiveScanResult
import app.yomi.reader.local.ZipArchiveScanner
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YomiTheme {
                YomiHome()
            }
        }
    }

    private fun persistSelection(uri: Uri, kind: String, pageCount: Int? = null): ImportedItem {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers grant read access without a persistable grant.
        }

        val title = if (kind == "folder") {
            uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Folder"
        } else {
            queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Book"
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_KIND, kind)
            .putString(KEY_TITLE, title)
            .putInt(KEY_PAGE_COUNT, pageCount ?: -1)
            .apply()

        return ImportedItem(title, uri.toString(), kind, pageCount)
    }

    private fun loadSelection(): ImportedItem? {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val uri = prefs.getString(KEY_URI, null) ?: return null
        return ImportedItem(
            title = prefs.getString(KEY_TITLE, "Book") ?: "Book",
            uri = uri,
            kind = prefs.getString(KEY_KIND, "file") ?: "file",
            pageCount = prefs.getInt(KEY_PAGE_COUNT, -1).takeIf { it >= 0 },
        )
    }

    private fun inspectArchive(uri: Uri): ArchiveScanResult {
        val stream = contentResolver.openInputStream(uri)
            ?: return ArchiveScanResult.Rejected("cannot-open")
        return stream.use(ZipArchiveScanner::scan)
    }

    private fun openReader(item: ImportedItem) {
        startActivity(ReaderActivity.newIntent(this, item.uri, item.kind, item.title))
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
        var imported by remember { mutableStateOf(loadSelection()) }
        var showAddSheet by remember { mutableStateOf(false) }
        var section by remember { mutableStateOf(HomeSection.LIBRARY) }
        var importError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val openBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    when (val scan = withContext(Dispatchers.IO) { inspectArchive(uri) }) {
                        is ArchiveScanResult.Success -> {
                            imported = persistSelection(uri, "archive", scan.catalog.pages.size)
                            importError = null
                        }
                        is ArchiveScanResult.Rejected -> importError = scan.reason
                    }
                }
            }
        }
        val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) imported = persistSelection(uri, "folder")
        }

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
                    if (section == HomeSection.LIBRARY) {
                        item {
                            if (imported == null) {
                                EmptyLibrary(
                                    onOpenBook = { openBook.launch(arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "*/*")) },
                                    onAddFolder = { addFolder.launch(null) },
                                )
                            } else {
                                ContinueReading(imported!!, onOpen = { openReader(imported!!) })
                            }
                        }
                        importError?.let { error ->
                            item {
                                Text(
                                    text = "Could not import archive: $error",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        imported?.let { selected ->
                            item { LibraryItem(selected, onOpen = { openReader(selected) }) }
                        }
                    } else {
                        item { PlaceholderSection(section) }
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
                    SheetAction("Add a folder", "Images, books, or chapter folders") {
                        showAddSheet = false
                        addFolder.launch(null)
                    }
                    Spacer(Modifier.height(24.dp))
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
    private fun ContinueReading(item: ImportedItem, onOpen: () -> Unit) {
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
                        if (item.kind == "folder") "Local folder" else "Local archive",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.pageCount?.let { "$it pages indexed" } ?: "Ready to read",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    @Composable
    private fun LibraryItem(item: ImportedItem, onOpen: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Library", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverPlaceholder()
                Column(Modifier.padding(start = 12.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text(
                        item.pageCount?.let { "$it pages · 0% read" } ?: "0% read",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
                "Local-first " + section.label.lowercase() + " view is next.",
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

    private data class ImportedItem(val title: String, val uri: String, val kind: String, val pageCount: Int? = null)

    private enum class HomeSection(val label: String) {
        LIBRARY("Library"),
        RECENT("Recent"),
        FOLDERS("Folders"),
        SETTINGS("Settings"),
    }

    private companion object {
        const val PREFS = "yomi_library"
        const val KEY_URI = "last_uri"
        const val KEY_KIND = "last_kind"
        const val KEY_TITLE = "last_title"
        const val KEY_PAGE_COUNT = "last_page_count"
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
