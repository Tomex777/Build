package com.example.whatsapp.presentation.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.NightMihonReaderActivity
import com.example.whatsapp.data.night.NightLibraryItemEntity
import com.example.whatsapp.data.night.NightLibraryStore
import com.example.whatsapp.data.night.NightRepository
import com.example.whatsapp.presentation.reader.mihon.NightMihonArchiveLoader
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernAppScaffold
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0B0F11)
private val SurfaceDark = Color(0xFF171C1F)
private val Primary = Color(0xFFE7EAEC)
private val Secondary = Color(0xFF9CA5A9)

@Composable
fun NightFilesTab(
    onTabSelected: (MainTab) -> Unit,
    onSettingsClick: () -> Unit,
    onScriptsClick: () -> Unit,
    onFileOpen: (NightLibraryItemEntity) -> Unit = {},
    accentColor: Color = Color(0xFFD44368),
) {
    val context = LocalContext.current
    val repository = remember { NightRepository.get(context.applicationContext) }
    val libraryStore = remember { NightLibraryStore.get(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val files by repository.observeLibrary().collectAsState(initial = emptyList())
    var libraryReady by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("All") }

    val filteredFiles = remember(files, query, typeFilter) {
        val needle = query.trim().lowercase()
        files
            .filter { file ->
                val matchesQuery =
                    needle.isBlank() ||
                        file.name.lowercase().contains(needle) ||
                        file.mimeType.lowercase().contains(needle)
                val matchesType =
                    typeFilter == "All" || libraryCategory(file) == typeFilter
                matchesQuery && matchesType
            }
            .sortedByDescending { it.createdAt }
    }

    LaunchedEffect(Unit) {
        libraryStore.migrateLegacy()
        libraryReady = true
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        scope.launch {
            uris.forEach { uri ->
                libraryStore.importUri(uri)
            }
        }
    }

    ModernAppScaffold(
        selectedTab = MainTab.Updates,
        onTabSelected = onTabSelected,
        title = "Library",
        onSettingsClick = onSettingsClick,
        showCamera = false,
        showSearch = false,
        accentColor = accentColor,
        floatingAction = {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                containerColor = accentColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(62.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add files",
                    modifier = Modifier.size(25.dp),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg),
        ) {
            LibraryWorkspaceShortcut(
                accentColor = accentColor,
                onClick = onScriptsClick,
            )

            LibrarySearchAndFilters(
                query = query,
                onQueryChange = { query = it },
                selectedType = typeFilter,
                onTypeSelected = { typeFilter = it },
                accentColor = accentColor,
            )

            when {
                !libraryReady -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Loading your library…",
                            color = Secondary,
                            fontSize = 12.sp,
                        )
                    }
                }

                files.isEmpty() -> {
                    EmptyLibrary(
                        onAdd = { picker.launch(arrayOf("*/*")) },
                        accentColor = accentColor,
                    )
                }

                filteredFiles.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No Library files match this view",
                            color = Secondary,
                            fontSize = 12.sp,
                        )
                    }
                }

                else -> {
                    val datedFiles = filteredFiles.groupBy {
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.createdAt))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        datedFiles.forEach { (dateLabel, datedGroup) ->
                            item(key = "date_" + dateLabel) {
                                Text(
                                    text = dateLabel,
                                    color = Secondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                                )
                            }
                            items(datedGroup, key = { it.id }) { file ->
                                val readableManga = NightMihonArchiveLoader.isSupportedArchive(
                                    fileName = file.name,
                                    mimeType = file.mimeType,
                                )
                                LibraryFileRow(
                                    file = file,
                                    readableManga = readableManga,
                                    accentColor = accentColor,
                                    onClick = {
                                        if (readableManga) {
                                            context.startActivity(
                                                NightMihonReaderActivity.archiveIntent(
                                                    context = context,
                                                    localPath = file.localPath,
                                                    displayName = file.name,
                                                )
                                            )
                                        } else {
                                            onFileOpen(file)
                                        }
                                    },
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
private fun LibrarySearchAndFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(44.dp)
            .background(SurfaceDark, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Primary, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search Library", color = Secondary, fontSize = 13.sp)
                }
                inner()
            },
        )
    }

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        listOf("All", "Images", "Video", "Audio", "PDF", "Text", "Manga", "Other")
            .forEach { type ->
                Surface(
                    color = if (selectedType == type) {
                        accentColor.copy(alpha = 0.18f)
                    } else {
                        SurfaceDark
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.clickable { onTypeSelected(type) },
                ) {
                    Text(
                        text = type,
                        color = if (selectedType == type) accentColor else Secondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
    }
}

@Composable
private fun LibraryWorkspaceShortcut(
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 5.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF222A2D),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scripts & projects",
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Create, edit, import and export Night JavaScript and local web projects",
                    color = Secondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(
    onAdd: () -> Unit,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.size(82.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Text(
                text = "No files yet",
                color = Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = "Add files once, then reference them from chat later. Phone-wide browsing will come from a separate extension.",
                color = Secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(top = 18.dp)
                    .clickable(onClick = onAdd),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("Add files", color = Primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LibraryFileRow(
    file: NightLibraryItemEntity,
    readableManga: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF222A2D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            file.mimeType.startsWith("image/") -> Icons.Default.Image
                            file.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
                            file.mimeType.contains("pdf") || file.mimeType.startsWith("text/") -> Icons.Default.Description
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatSize(file.sizeBytes) + " • " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(file.createdAt)),
                    color = Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = if (readableManga) {
                        "Mihon reader • Tap to read"
                    } else {
                        "Available to Night • ID " + file.id.take(8)
                    },
                    color = accentColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

private fun libraryCategory(file: NightLibraryItemEntity): String {
    if (
        NightMihonArchiveLoader.isSupportedArchive(
            fileName = file.name,
            mimeType = file.mimeType,
        )
    ) {
        return "Manga"
    }
    val mime = file.mimeType.lowercase()
    val name = file.name.lowercase()
    return when {
        mime.startsWith("image/") -> "Images"
        mime.startsWith("video/") -> "Video"
        mime.startsWith("audio/") -> "Audio"
        mime.contains("pdf") || name.endsWith(".pdf") -> "PDF"
        mime.startsWith("text/") ||
            name.endsWith(".txt") ||
            name.endsWith(".md") ||
            name.endsWith(".js") ||
            name.endsWith(".jsx") ||
            name.endsWith(".css") ||
            name.endsWith(".html") ||
            name.endsWith(".json") ||
            name.endsWith(".kt") ||
            name.endsWith(".java") -> "Text"
        else -> "Other"
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
