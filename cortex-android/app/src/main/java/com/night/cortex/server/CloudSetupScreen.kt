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
    onAutoAssign: () -> Unit,
    onRestart: () -> Unit,
) {
    val settings = state.environment?.entries.orEmpty().associateBy { it.key }
    val emailSetting = settings["MSCC_MEGA_EMAIL"]
    val passwordSetting = settings["MSCC_MEGA_PASSWORD"]
    val sharesSetting = settings["MSCC_MEGA_SHARED_FOLDERS"]
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmAssign by remember { mutableStateOf(false) }
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
                        dashboard == null -> "Status not loaded. Tap Refresh after restarting MSCC."
                        dashboard.configured -> "MEGA is configured. Primary account quota can be verified."
                        else -> "MEGA is not yet connected in the running MSCC process. Restart MSCC.",
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
                mounts.take(15).forEachIndexed { index, mount ->
                    Text(
                        "${index + 1}. ${mount.label}  ·  " +
                            when (index) {
                                0, 1, 2 -> "Video"
                                3 -> "Images & stickers"
                                else -> "Unassigned"
                            },
                        fontSize = 12.sp,
                    )
                }
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
                        enabled = !state.loading && mounts.size >= 4 && sharesSetting != null,
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
                    "Cortex will assign the first three discovered folders to video and " +
                        "the fourth to images/stickers. This replaces the prior folder map. " +
                        "Verify the folder order and write permissions first."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAssign = false
                        onAutoAssign()
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAssign = false }) { Text("Cancel") }
            },
        )
    }
}
