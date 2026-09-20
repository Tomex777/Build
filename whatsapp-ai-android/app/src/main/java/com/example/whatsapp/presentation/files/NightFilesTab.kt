package com.example.whatsapp.presentation.files

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.NightMihonReaderActivity
import com.example.whatsapp.data.NightFileLibrary
import com.example.whatsapp.data.NightLibraryFile
import com.example.whatsapp.presentation.reader.mihon.NightMihonArchiveLoader
import com.example.whatsapp.presentation.shell.MainTab
import com.example.whatsapp.presentation.shell.ModernAppScaffold
import java.text.DateFormat
import java.util.Date

private val Bg = Color(0xFF0B0F11)
private val SurfaceDark = Color(0xFF171C1F)
private val Primary = Color(0xFFE7EAEC)
private val Secondary = Color(0xFF9CA5A9)
private val Accent = Color(0xFF21C063)

@Composable
fun NightFilesTab(
    onTabSelected: (MainTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { NightFileLibrary.importUri(context, it) }
        refreshKey++
    }

    val files = remember(refreshKey) { NightFileLibrary.list(context) }

    ModernAppScaffold(
        selectedTab = MainTab.Updates,
        onTabSelected = onTabSelected,
        title = "Library",
        onSettingsClick = onSettingsClick,
        showCamera = false,
        floatingAction = {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                containerColor = Accent,
                contentColor = Color(0xFF08110C),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add files",
                    modifier = Modifier.size(25.dp),
                )
            }
        },
    ) {
        if (files.isEmpty()) {
            EmptyLibrary(onAdd = { picker.launch(arrayOf("*/*")) })
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Bg),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                        Text(
                            text = "Library",
                            color = Primary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Files here stay inside Night and can be referenced by you or the AI later.",
                            color = Secondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }

                items(files, key = { it.id }) { file ->
                    val readableManga = NightMihonArchiveLoader.isSupportedArchive(
                        fileName = file.name,
                        mimeType = file.mimeType,
                    )
                    LibraryFileRow(
                        file = file,
                        readableManga = readableManga,
                        onClick = {
                            if (readableManga) {
                                context.startActivity(
                                    NightMihonReaderActivity.archiveIntent(
                                        context = context,
                                        localPath = file.localPath,
                                        displayName = file.name,
                                    )
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
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
                        tint = Accent,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Text(
                text = "Library",
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
                        tint = Accent,
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
    file: NightLibraryFile,
    readableManga: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = readableManga, onClick = onClick),
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
                        tint = Accent,
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
                    color = Accent,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
