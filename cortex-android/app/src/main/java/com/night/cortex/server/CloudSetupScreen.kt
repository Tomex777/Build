package com.night.cortex.server

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.cortex.ui.theme.CortexMuted
import com.night.cortex.ui.theme.CortexSurface2

/**
 * All MEGA configuration takes place in Cortex. No donor account passwords
 * are needed; each donor keeps ownership and grants folder upload access.
 */
@Composable
internal fun CloudSetupPage(
    state: ServerPanelState,
    onRefresh: () -> Unit,
    onSave: (String, String) -> Unit,
    onAutoAssign: (Set<String>, String?) -> Unit,
    onRestart: () -> Unit,
) {
    val settings = state.environment?.entries.orEmpty().associateBy { it.key }
    val emailSetting = settings["MSCC_MEGA_EMAIL"]
    val passwordSetting = settings["MSCC_MEGA_PASSWORD"]
    val sharesSetting = settings["MSCC_MEGA_SHARED_FOLDERS"]
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmAssign by remember { mutableStateOf(false) }
    var selectedFolderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var imageFolderId by remember { mutableStateOf<String?>(null) }
    val mounts = state.cloudMounts.sortedBy { it.nodeId }
    val configured = emailSetting?.hasValue == true && passwordSetting?.hasValue == true

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("cortex-cloud-screen"),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text("Community Cloud", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "5 MEGA accounts · 3 for video · 1 for images/stickers · 1 primary for music, books and files.",
            color = CortexMuted,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !state.loading) { Text("Refresh") }
            OutlinedButton(onClick = onRestart, enabled = !state.loading && configured) { Text("Restart MSCC") }
        }

        Surface(color = CortexSurface2, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("1 · Primary MEGA account", fontWeight = FontWeight.SemiBold)
                Text(
                    if (configured) "Credentials saved privately on Azure."
                    else "Enter one primary MEGA account. It will receive folder-sharing invitations.",
                    fontSize = 12.sp,
                    color = CortexMuted,
                )
                if (emailSetting == null || passwordSetting == null) {
                    Text(
                        "This Cortex Agent has not been updated with MEGA controls yet. Update the Cortex Agent before connecting.",
                        fontSize = 12.sp,
                        color = CortexMuted,
                    )
                } else {
                    Text(
                        "Email: " + (emailSetting.value.ifBlank { "Not configured" }),
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { if (!it.contains('\n') && it.length <= 254) email = it },
                        label = { Text("Primary MEGA email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onSave("MSCC_MEGA_EMAIL", email.trim())
                            email = ""
                        },
                        enabled = !state.loading && email.contains("@") && email.isNotBlank(),
                    ) { Text("Save email") }
                    Text(
                        if (passwordSetting.hasValue) "Password: saved securely (hidden)"
                        else "Password: not configured",
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { if (!it.contains('\n') && it.length <= 4096) password = it },
                        label = { Text("MEGA password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onSave("MSCC_MEGA_PASSWORD", password)
                            password = ""
                        },
                        enabled = !state.loading && password.isNotBlank(),
                    ) { Text("Save password securely") }
                    Text(
                        "The password stays in the protected Azure environment, not in GitHub or the public bot.",
                        fontSize = 11.sp,
                        color = CortexMuted,
                    )
                }
            }
        }

        val dashboard = state.cloudDashboard
        Surface(color = CortexSurface2, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("2 · Cloud connection", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !configured -> "Save the primary account, then restart MSCC."
                        state.cloudConnectionFailed -> "Connection check failed. Verify the primary credentials, MSCC deployment and network, then refresh."
                        dashboard == null -> "Status not loaded. Tap Refresh after restarting MSCC."
                        dashboard.configured -> "MEGA is configured. Primary account quota can be verified."
                        else -> "MEGA is not yet connected in the running MSCC process. Restart MSCC."
                    },
                    fontSize = 12.sp,
                )
                if (dashboard?.configured == true) {
                    val used = dashboard.used
                    val total = dashboard.total
                    if (used != null && total != null) {
                        Text("Primary account: ${used / (1024 * 1024)} / ${total / (1024 * 1024)} MiB",
                            fontSize = 12.sp)
                    }
                    dashboard.targets.filter { it.type == "contributed" }.forEach { target ->
                        Text(
                            "${target.label} · ${target.mediaTypes.joinToString()} · " +
                                if (target.detected && target.uploadEnabled) "ready" else "offline/not approved",
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Surface(color = CortexSurface2, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("3 · Shared folders", fontWeight = FontWeight.SemiBold)
                Text(
                    "In each of the other four MEGA accounts, create a dedicated folder, " +
                        "share it with the primary MEGA email and grant Read & Write. " +
                        "Accept the four invitations in the primary account.",
                    fontSize = 12.sp,
                    color = CortexMuted,
                )
                Text(
                    "Detected folder invitations: ${mounts.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Select exactly four dedicated folders below. " +
                        "Mark one as Images; the remaining three become Video archives. " +
                        "No account passwords are required.",
                    color = CortexMuted,
                    fontSize = 11.sp,
                )
                mounts.take(20).forEach { mount ->
                    val selected = mount.nodeId in selectedFolderIds
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                selectedFolderIds = if (checked) {
                                    selectedFolderIds + mount.nodeId
                                } else {
                                    selectedFolderIds - mount.nodeId
                                }
                                if (!checked && imageFolderId == mount.nodeId) imageFolderId = null
                            },
                            enabled = !state.loading && (selected || selectedFolderIds.size < 4),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(mount.label, fontSize = 12.sp)
                            Text(
                                when {
                                    !selected -> "Not selected"
                                    imageFolderId == mount.nodeId -> "Images & Stickers"
                                    else -> "Video"
                                },
                                color = CortexMuted,
                                fontSize = 11.sp,
                            )
                        }
                        if (selected) {
                            RadioButton(
                                selected = imageFolderId == mount.nodeId,
                                onClick = { imageFolderId = mount.nodeId },
                                enabled = !state.loading,
                            )
                            Text("Images", fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
                        }
                    }
                }
                Text(
                    "Selected: ${selectedFolderIds.size} / 4 · " +
                        if (imageFolderId != null && imageFolderId in selectedFolderIds)
                            "one image folder chosen"
                        else "select which one stores images",
                    color = CortexMuted,
                    fontSize = 11.sp,
                )
                if (configured && state.cloudMountsLoaded && mounts.size < 4) {
                    Text(
                        "Four distinct writable folder invitations are needed. " +
                            "Refresh after accepting them.",
                        fontSize = 11.sp,
                        color = CortexMuted,
                    )
                }
                if (sharesSetting == null) {
                    Text("Folder mapping requires the updated Cortex Agent.", color = CortexMuted, fontSize = 11.sp)
                } else {
                    Text(
                        if (sharesSetting.hasValue) "Folder roles saved; changing them replaces the previous map."
                        else "Folder roles not assigned yet.",
                        fontSize = 11.sp,
                        color = CortexMuted,
                    )
                    Button(
                        enabled = !state.loading && selectedFolderIds.size == 4 && imageFolderId in selectedFolderIds && sharesSetting != null,
                        onClick = { confirmAssign = true },
                    ) { Text("Assign 3 video folders + 1 images") }
                    Text(
                        "Cortex balances each new video upload across the three folders " +
                            "by visible bytes. Other media uses the primary account. " +
                            "Actual donor free space cannot be read from shared folders.",
                        fontSize = 11.sp,
                        color = CortexMuted,
                    )
                }
            }
        }
        HorizontalDivider()
        Text(
            "After saving the folder layout, restart MSCC and test a small file. " +
                "No uploaded WhatsApp data is archived without an explicit command.",
            color = CortexMuted,
            fontSize = 11.sp,
        )
    }

    if (confirmAssign) {
        AlertDialog(
            onDismissRequest = { confirmAssign = false },
            title = { Text("Apply Community Cloud layout?") },
            text = {
                Text(
                    "Cortex will assign your chosen Images folder to images/stickers " +
                        "and the remaining three selected folders to video. " +
                        "This replaces the prior folder map. Confirm the donors granted write permission."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAssign = false
                        onAutoAssign(selectedFolderIds, imageFolderId)
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAssign = false }) { Text("Cancel") }
            },
        )
    }
}
