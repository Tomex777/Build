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
    val startupMode: String = "unknown",
    val gitRepository: String = "",
    val gitBranch: String = "",
    val additionalNodePackages: List<String> = emptyList(),
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

data class CommandSetting(
    val key: String,
    val label: String,
    val description: String,
    val command: String,
    val enabled: Boolean,
)

data class RuntimeConfigField(
    val key: String,
    val label: String = "",
    val type: String = "",
    val description: String = "",
)

data class RuntimeModule(
    val id: String,
    val displayName: String,
    val version: String,
    val status: String,
    val enabled: Boolean,
    val commands: List<String>,
    val configuration: List<RuntimeConfigField>,
    val loadError: String,
    val lastReload: String,
    val moduleDirectory: String,
    val dependencies: List<String>,
    val permissions: List<String>,
)

data class RuntimeCommand(
    val name: String,
    val moduleId: String,
    val description: String,
    val aliases: List<String>,
    val enabled: Boolean,
    val permission: String,
    val usage: String,
    val error: String,
)

data class RuntimeRegistry(
    val version: Int,
    val generatedAt: String,
    val source: String,
    val modules: List<RuntimeModule>,
    val commands: List<RuntimeCommand>,
)

data class PairingAccount(
    val id: String,
    val enabled: Boolean,
    val connected: Boolean,
    val status: String,
    val numberMasked: String,
    val indexCount: Int,
    val indexLimit: Int,
    val pairingMode: String,
    val pairingCode: String,
    val pairingQr: String,
    val pairingError: String,
    val displayName: String = "",
)

data class PairingState(
    val version: String,
    val destination: String,
    val accounts: List<PairingAccount>,
    val maxAccounts: Int? = null,
    val canAddAccount: Boolean = false,
)

data class PendingDownload(
    val name: String,
    val bytes: ByteArray,
)

data class ServerPanelState(
    val configured: Boolean = false,
    val agentReachable: Boolean = false,
    val lastSuccessfulSyncAt: Long? = null,
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
    val commandSettings: List<CommandSetting> = emptyList(),
    val runtimeRegistry: RuntimeRegistry? = null,
    val pairing: PairingState? = null,
    val pendingDownload: PendingDownload? = null,
)
