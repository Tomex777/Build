package com.night.cortex.server

import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingSnapshot

data class StartupInfo(
    val runtime: String = "Node.js",
    val version: String = "",
    val entryFile: String = "index.js",
    val startCommand: String = "node index.js",
    val projectRoot: String = "",
    val service: String = "",
)

data class ActivityEntry(
    val id: String,
    val at: String,
    val action: String,
    val detail: String = "",
)

data class BackupEntry(
    val name: String,
    val sizeBytes: Long,
    val createdAt: String,
    val privateBackup: Boolean,
)

data class PendingDownload(
    val name: String,
    val bytes: ByteArray,
)

data class ServerPanelState(
    val configured: Boolean = false,
    val baseUrl: String = "",
    val hasToken: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val snapshot: HostingSnapshot? = null,
    val logs: List<String> = emptyList(),
    val currentPath: String = "/",
    val files: List<HostingFileEntry> = emptyList(),
    val selectedFile: String? = null,
    val editorContent: String = "",
    val editorDirty: Boolean = false,
    val startup: StartupInfo? = null,
    val activity: List<ActivityEntry> = emptyList(),
    val backups: List<BackupEntry> = emptyList(),
    val pendingDownload: PendingDownload? = null,
)
