package com.night.cortex.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.cortex.hosting.HostingProviderId
import com.night.cortex.ui.theme.*
import java.util.Locale

@Composable
fun CortexLibraryScreen(
    state: CortexUiState,
    provider: HostingProviderId,
    refresh: () -> Unit,
    openFile: (String) -> Unit,
    saveFile: () -> Unit,
    updateEditor: (String) -> Unit,
    closeEditor: () -> Unit,
    createFile: (String) -> Unit,
    importFile: (Uri) -> Unit,
    deleteFile: (String) -> Unit,
    syncBlob: () -> Unit,
    restoreBlob: () -> Unit,
    deploy: () -> Unit,
    configureBlob: () -> Unit,
    configureHosting: () -> Unit,
) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri)
    }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var newPath by rememberSaveable { mutableStateOf("") }
    var deletePath by rememberSaveable { mutableStateOf<String?>(null) }
    var showRestoreConfirm by rememberSaveable { mutableStateOf(false) }

    if (state.selectedLocalFile != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(closeEditor) { Icon(Icons.Rounded.ArrowBack, "Back to files") }
                Column(Modifier.weight(1f)) {
                    Text(state.selectedLocalFile.substringAfterLast('/'), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(state.selectedLocalFile, color = CortexMuted, fontSize = 9.sp)
                }
                TextButton(onClick = saveFile, enabled = !state.workspaceBusy) { Text("Save") }
            }
            Surface(
                Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                color = Color(0xFF090B0D),
                shape = RoundedCornerShape(16.dp),
            ) {
                BasicTextField(
                    value = state.editorContent,
                    onValueChange = updateEditor,
                    modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(color = CortexText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(CortexAccent),
                    decorationBox = { inner -> Box { if (state.editorContent.isEmpty()) Text("Start writing…", color = CortexMuted); inner() } },
                )
            }
            state.workspaceMessage?.let { Text(it, Modifier.padding(top = 7.dp), color = CortexMuted, fontSize = 10.sp) }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CORTEX", color = CortexMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text("Library", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "Refresh files") }
        }
        Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(18.dp), color = CortexSurface) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = CortexAccent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Night project", fontWeight = FontWeight.SemiBold)
                        Text(state.localFileCount.toString() + " files stored on this phone", color = CortexMuted, fontSize = 9.sp)
                    }
                    Surface(shape = RoundedCornerShape(99.dp), color = Color(0xFF24272D)) {
                        Text("LOCAL", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = CortexMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Nothing syncs in the background. node_modules and secrets stay off this phone.", color = CortexMuted, fontSize = 9.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(5.dp)); Text("Import") }
            Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("New file")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = if (state.blobConfigured) syncBlob else configureBlob,
                enabled = !state.blobBusy && !state.deployBusy,
                modifier = Modifier.weight(1f),
            ) {
                if (state.blobBusy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.CloudUpload, null)
                Spacer(Modifier.width(5.dp))
                Text(if (state.blobConfigured) "Sync backup" else "Set up backup", fontSize = 10.sp)
            }
            Button(
                onClick = if (state.hostingConfigured) deploy else configureHosting,
                enabled = provider == HostingProviderId.AZURE && !state.deployBusy && !state.blobBusy,
                modifier = Modifier.weight(1f),
            ) {
                if (state.deployBusy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.CloudDone, null)
                Spacer(Modifier.width(5.dp))
                Text("Deploy to Azure", fontSize = 10.sp)
            }
        }
        state.blobMessage?.let { Text(it, Modifier.padding(top = 5.dp), color = CortexMuted, fontSize = 9.sp) }
        state.blobRestoreMessage?.let { Text(it, Modifier.padding(top = 5.dp), color = CortexMuted, fontSize = 9.sp) }
        state.deployMessage?.let { Text(it, Modifier.padding(top = 5.dp), color = CortexMuted, fontSize = 9.sp) }
        state.workspaceMessage?.let { Text(it, Modifier.padding(top = 5.dp), color = CortexMuted, fontSize = 9.sp) }
        Spacer(Modifier.height(10.dp))
        if (state.blobConfigured) {
            TextButton(
                onClick = { showRestoreConfirm = true },
                enabled = !state.blobBusy && !state.blobRestoreBusy && !state.deployBusy,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                if (state.blobRestoreBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.CloudDownload, null)
                Spacer(Modifier.width(6.dp))
                Text("Restore from Blob (downloads only when tapped)", fontSize = 9.sp)
            }
        }
        Text("PROJECT FILES", color = CortexMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (state.localFiles.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = CortexMuted, modifier = Modifier.size(30.dp))
                    Text("Your local project is empty", fontWeight = FontWeight.SemiBold)
                    Text("Import a ZIP or create index.js to get started.", color = CortexMuted, fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(state.localFiles, key = { it.path }) { file ->
                    Row(Modifier.fillMaxWidth().clickable { openFile(file.path) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, null, tint = CortexMuted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.path, maxLines = 1, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(formatBytes(file.sizeBytes), color = CortexMuted, fontSize = 8.sp)
                        }
                        IconButton(onClick = { deletePath = file.path }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, "Delete from phone", tint = CortexMuted, modifier = Modifier.size(17.dp))
                        }
                    }
                    HorizontalDivider(color = CortexLine)
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create project file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved locally. Nothing uploads until you choose Sync backup or Deploy.", color = CortexMuted, fontSize = 11.sp)
                    OutlinedTextField(newPath, { newPath = it }, label = { Text("File path") }, placeholder = { Text("index.js") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { createFile(newPath); newPath = ""; showCreate = false }, enabled = newPath.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
    deletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { deletePath = null },
            title = { Text("Delete local file?") },
            text = { Text("$path will be removed from this phone. Azure copies are left untouched.", color = CortexMuted) },
            confirmButton = { TextButton(onClick = { deleteFile(path); deletePath = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletePath = null }) { Text("Cancel") } },
        )
    }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Download project backup?") },
            text = { Text("Cortex will download changed project files from Azure Blob now. It will stop if a local file has unbacked edits. Large files can use mobile data.", color = CortexMuted) },
            confirmButton = { TextButton(onClick = { restoreBlob(); showRestoreConfirm = false }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun BlobStorageScreen(initialUrl: String, onBack: () -> Unit, onSave: (String) -> Unit) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Azure Blob backup", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Paste a container SAS URL with read, write and create permissions. Cortex uploads only changed files when you tap Sync backup. Restore downloads only after you tap it. The URL is encrypted on this phone.",
            color = CortexMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Container SAS URL") },
            placeholder = { Text("https://account.blob.core.windows.net/container?...") },
            singleLine = false,
            minLines = 2,
        )
        Button(
            onClick = { onSave(url) },
            enabled = url.startsWith("https://") && url.contains("blob.core.windows.net") && url.contains("sig="),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Save storage setting") }
        Text("Ignored from phone workspace: node_modules, .git, .env and private keys.", color = CortexMuted, fontSize = 9.sp)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
