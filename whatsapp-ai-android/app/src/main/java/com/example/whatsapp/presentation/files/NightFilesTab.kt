package com.example.whatsapp.presentation.files

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.NightMihonReaderActivity
import com.example.whatsapp.data.night.NightLibraryFolder
import com.example.whatsapp.data.night.NightLibraryFolderStore
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
    onFileOpen: (NightLibraryItemEntity) -> Unit = {},
    accentColor: Color = Color(0xFFD44368),
) {
    val context = LocalContext.current
    val repository = remember { NightRepository.get(context.applicationContext) }
    val libraryStore = remember { NightLibraryStore.get(context.applicationContext) }
    val folderStore = remember { NightLibraryFolderStore.get(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val files by repository.observeLibrary().collectAsState(initial = emptyList())
    var libraryReady by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("All") }
    var selectedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderRevision by remember { mutableIntStateOf(0) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var fabActionsOpen by remember { mutableStateOf(false) }
    var moveFile by remember { mutableStateOf<NightLibraryItemEntity?>(null) }
    var deleteFolder by remember { mutableStateOf<NightLibraryFolder?>(null) }

    val folders = remember(folderRevision) { folderStore.folders() }

    val filteredFiles = remember(
        files,
        query,
        typeFilter,
        selectedFolderId,
        folderRevision,
    ) {
        val needle = query.trim().lowercase()
        files
            .filter { file ->
                val matchesQuery =
                    needle.isBlank() ||
                        file.name.lowercase().contains(needle) ||
                        file.mimeType.lowercase().contains(needle)
                val matchesType =
                    typeFilter == "All" || libraryCategory(file) == typeFilter
                val matchesFolder =
                    selectedFolderId == null ||
                        folderStore.folderForItem(file.id) == selectedFolderId
                matchesQuery && matchesType && matchesFolder
            }
            .sortedByDescending { it.createdAt }
    }

    LaunchedEffect(Unit) {
        libraryStore.migrateLegacy()
        libraryReady = true
    }

    LaunchedEffect(files.map { it.id }) {
        folderStore.prune(files.mapTo(linkedSetOf()) { it.id })
    }

    LaunchedEffect(folders, selectedFolderId) {
        if (
            selectedFolderId != null &&
            folders.none { it.id == selectedFolderId }
        ) {
            selectedFolderId = null
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        scope.launch {
            uris.forEach { uri ->
                libraryStore.importUri(uri)
                    .onSuccess { item ->
                        selectedFolderId?.let { folderId ->
                            folderStore.assign(item.id, folderId)
                            folderRevision += 1
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: "Could not add this file.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
            }
        }
    }

    ModernAppScaffold(
        selectedTab = MainTab.Updates,
        onTabSelected = onTabSelected,
        title = "Library",
        showCamera = false,
        showSearch = false,
        showMenu = false,
        accentColor = accentColor,
        floatingAction = {
            Surface(
                color = accentColor,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .size(60.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { picker.launch(arrayOf("*/*")) },
                            onLongPress = { fabActionsOpen = true },
                        )
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add files",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg),
        ) {
            LibrarySearchAndFilters(
                query = query,
                onQueryChange = { query = it },
                selectedType = typeFilter,
                onTypeSelected = { typeFilter = it },
                hasActiveFilters = query.isNotBlank() || typeFilter != "All" || selectedFolderId != null,
                onClearFilters = {
                    query = ""
                    typeFilter = "All"
                    selectedFolderId = null
                },
                accentColor = accentColor,
            )

            LibraryFolderStrip(
                folders = folders,
                selectedFolderId = selectedFolderId,
                accentColor = accentColor,
                onSelect = { selectedFolderId = it },
                onNewFolder = {
                    newFolderName = ""
                    newFolderOpen = true
                },
                onLongPress = { deleteFolder = it },
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
                            text = if (selectedFolderId != null) {
                                "This folder has no matching files"
                            } else {
                                "No Library files match this view"
                            },
                            color = Secondary,
                            fontSize = 12.sp,
                        )
                    }
                }

                else -> {
                    val datedFiles = filteredFiles.groupBy {
                        DateFormat.getDateInstance(DateFormat.MEDIUM)
                            .format(Date(it.createdAt))
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
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = 8.dp,
                                        bottom = 2.dp,
                                    ),
                                )
                            }
                            items(datedGroup, key = { it.id }) { file ->
                                val readableManga =
                                    NightMihonArchiveLoader.isSupportedArchive(
                                        fileName = file.name,
                                        mimeType = file.mimeType,
                                    )
                                LibraryFileRow(
                                    file = file,
                                    readableManga = readableManga,
                                    folderName = folders
                                        .firstOrNull {
                                            it.id == folderStore.folderForItem(file.id)
                                        }
                                        ?.name,
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
                                    onLongClick = { moveFile = file },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (fabActionsOpen) {
        AlertDialog(
            onDismissRequest = { fabActionsOpen = false },
            title = { Text("Library actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                fabActionsOpen = false
                                picker.launch(arrayOf("*/*"))
                            },
                    ) {
                        Text(
                            "Add files",
                            color = Primary,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                fabActionsOpen = false
                                newFolderName = ""
                                newFolderOpen = true
                            },
                    ) {
                        Text(
                            "New folder",
                            color = Primary,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (newFolderOpen) {
        AlertDialog(
            onDismissRequest = { newFolderOpen = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it.take(48) },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            folderStore.createFolder(newFolderName)
                        }.onSuccess { folder ->
                            selectedFolderId = folder.id
                            folderRevision += 1
                            newFolderOpen = false
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: "Could not create folder.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    enabled = newFolderName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                    ),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { newFolderOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    moveFile?.let { file ->
        AlertDialog(
            onDismissRequest = { moveFile = null },
            title = { Text("Move ${file.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FolderMoveChoice(
                        label = "No folder",
                        selected = folderStore.folderForItem(file.id) == null,
                        accentColor = accentColor,
                    ) {
                        folderStore.assign(file.id, null)
                        folderRevision += 1
                        moveFile = null
                    }
                    folders.forEach { folder ->
                        FolderMoveChoice(
                            label = folder.name,
                            selected = folderStore.folderForItem(file.id) == folder.id,
                            accentColor = accentColor,
                        ) {
                            folderStore.assign(file.id, folder.id)
                            folderRevision += 1
                            moveFile = null
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveFile = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    deleteFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text("Delete folder?") },
            text = {
                Text(
                    "Files stay in Library. Only the “${folder.name}” folder is removed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        folderStore.deleteFolder(folder.id)
                        if (selectedFolderId == folder.id) {
                            selectedFolderId = null
                        }
                        folderRevision += 1
                        deleteFolder = null
                    },
                ) {
                    Text("Delete", color = Color(0xFFFF5C72))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolder = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LibraryFolderStrip(
    folders: List<NightLibraryFolder>,
    selectedFolderId: String?,
    accentColor: Color,
    onSelect: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onLongPress: (NightLibraryFolder) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderChip(
            name = "All files",
            selected = selectedFolderId == null,
            accentColor = accentColor,
            onClick = { onSelect(null) },
        )

        folders.forEach { folder ->
            Surface(
                color = if (selectedFolderId == folder.id) {
                    accentColor.copy(alpha = 0.18f)
                } else {
                    SurfaceDark
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.pointerInput(folder.id, selectedFolderId) {
                    detectTapGestures(
                        onTap = { onSelect(folder.id) },
                        onLongPress = { onLongPress(folder) },
                    )
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (selectedFolderId == folder.id) accentColor else Secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = folder.name,
                        color = if (selectedFolderId == folder.id) accentColor else Secondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        FolderChip(
            name = "+ New",
            selected = false,
            accentColor = accentColor,
            onClick = onNewFolder,
        )
    }
}

@Composable
private fun FolderChip(
    name: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) {
            accentColor.copy(alpha = 0.18f)
        } else {
            SurfaceDark
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = name,
            color = if (selected) accentColor else Secondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun FolderMoveChoice(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) {
            accentColor.copy(alpha = 0.16f)
        } else {
            SurfaceDark
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else Primary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun LibrarySearchAndFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
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

    if (hasActiveFilters) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, end = 12.dp, top = 1.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClearFilters) {
                Text("Clear filters", color = accentColor, fontSize = 12.sp)
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
    folderName: String?,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(file.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
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
                            file.mimeType.contains("pdf") ||
                                file.mimeType.startsWith("text/") ->
                                Icons.Default.Description
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
                    text = buildString {
                        append(formatSize(file.sizeBytes))
                        append(" • ")
                        append(
                            DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .format(Date(file.createdAt))
                        )
                        if (!folderName.isNullOrBlank()) {
                            append(" • ")
                            append(folderName)
                        }
                    },
                    color = Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = if (readableManga) {
                        "Mihon reader • Tap to read"
                    } else {
                        "Available to Night • Hold to move"
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
    bytes >= 1024L * 1024L ->
        String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L ->
        String.format("%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
