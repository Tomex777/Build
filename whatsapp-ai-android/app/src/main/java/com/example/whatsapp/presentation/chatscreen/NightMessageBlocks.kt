package com.example.whatsapp.presentation.chatscreen

import com.example.whatsapp.extensions.messages.ExtensionMessageSnapshot

/**
 * Reusable declarative content for Night's non-basic messages.
 *
 * Basic chat media keeps the native WhatsApp-style message classes. AI tools
 * and extensions compose richer results from these blocks instead of injecting
 * arbitrary Compose UI.
 */
data class BlockResultMessage(
    override val id: String,
    val blocks: List<NightMessageBlock>,
    val time: String,
    val mine: Boolean = false,
) : RichResultMessage

enum class NightBlockActionStyle {
    Primary,
    Secondary,
    Destructive,
}

data class NightBlockAction(
    val id: String,
    val label: String,
    val style: NightBlockActionStyle = NightBlockActionStyle.Secondary,
    val enabled: Boolean = true,
)

data class NightKeyValue(
    val label: String,
    val value: String,
)

data class NightTableRow(
    val cells: List<String>,
)

data class NightTaskItem(
    val id: String,
    val label: String,
    val completed: Boolean = false,
    val detail: String = "",
)

data class NightChoiceOption(
    val id: String,
    val label: String,
    val description: String = "",
    val selected: Boolean = false,
    val previewPath: String? = null,
)

data class NightSourceItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val actionId: String? = null,
)

enum class NightToolState {
    Running,
    Succeeded,
    Failed,
}

enum class NightConnectionState {
    Disconnected,
    Connecting,
    Connected,
    NeedsAuth,
    Error,
}

enum class NightTransferState {
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
}

enum class NightPermissionGrant {
    AskEveryTime,
    AllowOnce,
    ThisChat,
    AlwaysSelectedActions,
    FullAccess,
}

sealed interface NightMessageBlock {
    data class RichText(val text: String) : NightMessageBlock

    data class MediaPreview(
        val path: String,
        val title: String = "",
        val subtitle: String = "",
        val actionId: String? = null,
    ) : NightMessageBlock

    data class Code(
        val code: String,
        val language: String = "",
        val title: String = "",
        val copyActionId: String? = null,
    ) : NightMessageBlock

    data class Copy(
        val label: String = "Copy",
        val text: String,
        val actionId: String,
    ) : NightMessageBlock

    data class KeyValues(
        val items: List<NightKeyValue>,
    ) : NightMessageBlock

    data class Table(
        val headers: List<String>,
        val rows: List<NightTableRow>,
    ) : NightMessageBlock

    data class Tasks(
        val title: String,
        val items: List<NightTaskItem>,
        val progress: Float? = null,
    ) : NightMessageBlock

    data class Progress(
        val title: String,
        val detail: String = "",
        val progress: Float? = null,
        val status: String = "",
    ) : NightMessageBlock

    data class ToolResult(
        val toolName: String,
        val title: String,
        val detail: String = "",
        val state: NightToolState = NightToolState.Succeeded,
        val actions: List<NightBlockAction> = emptyList(),
    ) : NightMessageBlock

    data class Error(
        val title: String,
        val detail: String,
        val retryAction: NightBlockAction? = null,
    ) : NightMessageBlock

    data class Sources(
        val title: String = "Sources",
        val items: List<NightSourceItem>,
    ) : NightMessageBlock

    data class Confirmation(
        val title: String,
        val body: String,
        val destructive: Boolean = false,
        val actions: List<NightBlockAction>,
    ) : NightMessageBlock

    data class Permission(
        val title: String,
        val body: String,
        val scopes: List<String>,
        val grant: NightPermissionGrant = NightPermissionGrant.AskEveryTime,
        val destructiveConfirmationRequired: Boolean = true,
        val actions: List<NightBlockAction>,
    ) : NightMessageBlock

    data class Choice(
        val id: String,
        val title: String,
        val prompt: String = "",
        val options: List<NightChoiceOption>,
        val multiSelect: Boolean = false,
        val allowOther: Boolean = true,
        val actions: List<NightBlockAction> = emptyList(),
    ) : NightMessageBlock

    data class Diff(
        val title: String = "Changes",
        val before: String,
        val after: String,
    ) : NightMessageBlock

    data class Connection(
        val title: String,
        val provider: String,
        val state: NightConnectionState,
        val detail: String = "",
        val actions: List<NightBlockAction> = emptyList(),
    ) : NightMessageBlock

    data class Transfer(
        val title: String,
        val detail: String = "",
        val state: NightTransferState,
        val progress: Float? = null,
        val actions: List<NightBlockAction> = emptyList(),
    ) : NightMessageBlock

    data class Details(
        val summary: String,
        val body: String,
        val expanded: Boolean = false,
        val toggleActionId: String? = null,
    ) : NightMessageBlock

    data class ExtensionCard(
        val snapshot: ExtensionMessageSnapshot,
        val extensionAvailable: Boolean = true,
    ) : NightMessageBlock

    data class Actions(
        val actions: List<NightBlockAction>,
    ) : NightMessageBlock
}
