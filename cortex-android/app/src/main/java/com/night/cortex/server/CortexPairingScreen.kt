package com.night.cortex.server

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.cortex.ui.theme.CortexAccent
import com.night.cortex.ui.theme.CortexBackground
import com.night.cortex.ui.theme.CortexDanger
import com.night.cortex.ui.theme.CortexGood
import com.night.cortex.ui.theme.CortexHeader
import com.night.cortex.ui.theme.CortexLine
import com.night.cortex.ui.theme.CortexMuted
import com.night.cortex.ui.theme.CortexSurface
import com.night.cortex.ui.theme.CortexSurface2
import com.night.cortex.ui.theme.CortexText
import java.text.DateFormat
import java.util.Date

private enum class PairAction { PAIR, REPAIR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CortexPairingScreen(
    state: PairingState?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onAddAccount: (String, String) -> Unit,
    onDestination: (String) -> Unit,
    onPair: (String, String) -> Unit,
    onReconnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRepair: (String, String) -> Unit,
    onRename: (String, String) -> Unit = { _, _ -> },
    onAssignProfile: (String, String) -> Unit = { _, _ -> },
) {
    var selected by remember { mutableStateOf<PairingAccount?>(null) }
    var destinationCandidate by remember { mutableStateOf<PairingAccount?>(null) }
    var disconnectCandidate by remember { mutableStateOf<PairingAccount?>(null) }
    var removeCandidate by remember { mutableStateOf<PairingAccount?>(null) }
    var action by remember { mutableStateOf(PairAction.PAIR) }
    var addingNumber by remember { mutableStateOf(false) }
    var renameCandidate by remember { mutableStateOf<PairingAccount?>(null) }
    var profileCandidate by remember { mutableStateOf<PairingAccount?>(null) }
    var repairConfirmation by remember { mutableStateOf<Pair<PairingAccount, String>?>(null) }
    val duplicateNames = remember(state?.accounts) {
        duplicateSavedNameIds(state?.accounts.orEmpty())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CortexBackground,
        contentColor = CortexText,
    ) {
        Column(Modifier.fillMaxSize().testTag("pairing-screen-root")) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("WhatsApp Accounts", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        state?.let {
                            val destinationName = it.accounts.firstOrNull { account -> account.id == it.destination }?.title
                                ?: "Account ${it.destination}"
                            "${it.accounts.count { account -> account.connected }} connected · ${it.accounts.size} total · CC: $destinationName [${it.destination}]"
                        } ?: "Connect to your server to manage linked accounts",
                        color = CortexMuted,
                        fontSize = 11.sp,
                    )
                }
                if (
                    state?.canAddAccount == true &&
                    (state.maxAccounts == null || state.accounts.size < state.maxAccounts)
                ) {
                    IconButton(onClick = { addingNumber = true }, enabled = !busy) {
                        Icon(Icons.Rounded.Add, "Add number")
                    }
                }
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(Icons.Rounded.Refresh, "Refresh accounts")
                }
            }
            HorizontalDivider(color = CortexLine)

            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Link, null, tint = CortexMuted, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Pairing is unavailable.", color = CortexMuted)
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onRefresh) { Text("Try again") }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("pairing-account-list"),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (duplicateNames.isNotEmpty()) {
                        item(key = "duplicate-session-names") {
                            Surface(
                                color = CortexDanger.copy(alpha = .13f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    "Multiple sessions have the same saved account name. " +
                                        "They are not necessarily the same WhatsApp connection. " +
                                        "Use the session ID and masked number to tell them apart.",
                                    color = CortexText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                        .testTag("duplicate-session-name-warning"),
                                )
                            }
                        }
                    }
                    if (state.accounts.isEmpty()) {
                        item {
                            Surface(color = CortexSurface, shape = RoundedCornerShape(12.dp)) {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(Icons.Rounded.PhoneAndroid, null, tint = CortexMuted, modifier = Modifier.size(30.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No accounts paired yet.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Add a number, then link it with the phone-number pairing code. QR remains an explicit alternative.",
                                        color = CortexMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    if (state.canAddAccount) {
                        item {
                            val atLimit = state.maxAccounts != null && state.accounts.size >= state.maxAccounts
                            Button(
                                onClick = { addingNumber = true },
                                enabled = !busy && !atLimit,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CortexAccent),
                                shape = RoundedCornerShape(9.dp),
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (atLimit) "Account limit reached (${state.accounts.size}/${state.maxAccounts})"
                                    else "Add number",
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                    items(state.accounts, key = { it.id }) { account ->
                        PairingAccountCard(
                            account = account,
                            destination = state.destination == account.id,
                            duplicateName = account.id in duplicateNames,
                            busy = busy,
                            onPair = {
                                selected = account
                                action = PairAction.PAIR
                            },
                            onDestination = { destinationCandidate = account },
                            onReconnect = { onReconnect(account.id) },
                            onRename = { renameCandidate = account },
                            onEditProfile = { profileCandidate = account },
                            onDisconnect = { disconnectCandidate = account },
                            onRemove = { removeCandidate = account },
                            onRepair = {
                                selected = account
                                action = PairAction.REPAIR
                            },
                        )
                    }
                }
            }
        }
    }

    renameCandidate?.let { account ->
        var changedName by remember(account.id, account.displayName) { mutableStateOf(account.displayName) }
        val valid = changedName.trim().isNotEmpty() && changedName.trim().length <= 48
        AlertDialog(
            onDismissRequest = { renameCandidate = null },
            containerColor = CortexSurface,
            titleContentColor = CortexText,
            textContentColor = CortexMuted,
            title = { Text("Rename ${account.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Session ID: ${account.id} · ${account.numberMasked}", fontSize = 11.sp)
                    OutlinedTextField(
                        value = changedName,
                        onValueChange = { changedName = it.take(48) },
                        label = { Text("Saved account name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename-session-name"),
                        colors = cortexPairingTextFieldColors(),
                    )
                    Text(
                        "This only changes the label in MSCC. The linked WhatsApp session and its bot profile are preserved.",
                        color = CortexMuted,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && valid && changedName.trim() != account.displayName,
                    onClick = {
                        onRename(account.id, changedName.trim())
                        renameCandidate = null
                    },
                    modifier = Modifier.testTag("confirm-rename-session"),
                ) { Text("Save name", color = CortexAccent) }
            },
            dismissButton = { TextButton(onClick = { renameCandidate = null }) { Text("Cancel") } },
        )
    }

    profileCandidate?.let { account ->
        var choice by remember(account.id, account.profile) { mutableStateOf(account.profile) }
        val available = state?.profiles.orEmpty().filter { option ->
            option.id != "control" || account.id == "A"
        }.filter { option ->
            account.id != "A" || option.id == "control"
        }
        AlertDialog(
            onDismissRequest = { profileCandidate = null },
            containerColor = CortexSurface,
            titleContentColor = CortexText,
            textContentColor = CortexMuted,
            title = { Text("Bot profile · ${account.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Account: ${account.id} · ${account.numberMasked}\nCurrent: ${account.profileLabel}",
                        color = CortexMuted, fontSize = 11.sp,
                    )
                    Text(
                        "Changing the bot personality affects command routing, not WhatsApp authentication or the saved account name.",
                        color = CortexMuted, fontSize = 11.sp,
                    )
                    Column(
                        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (available.isEmpty()) {
                            Text(
                                "Bot profiles are unavailable. Update MSCC and refresh Accounts.",
                                color = CortexDanger, fontSize = 12.sp,
                            )
                        }
                        available.forEach { option ->
                            val selectedOption = option.id == choice
                            Surface(
                                color = if (selectedOption) CortexAccent.copy(alpha = .16f) else CortexSurface2,
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { choice = option.id }
                                    .testTag("choose-bot-profile-${option.id}"),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        (if (selectedOption) "✓  " else "") + option.displayName,
                                        color = CortexText, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(option.id, color = CortexMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && choice.isNotBlank() && choice != account.profile &&
                        available.any { it.id == choice },
                    onClick = {
                        onAssignProfile(account.id, choice)
                        profileCandidate = null
                    },
                    modifier = Modifier.testTag("confirm-bot-profile"),
                ) { Text("Apply profile", color = CortexAccent) }
            },
            dismissButton = { TextButton(onClick = { profileCandidate = null }) { Text("Cancel") } },
        )
    }

    if (addingNumber) {
        AddNumberSheet(
            onDismiss = { addingNumber = false },
            onSubmit = { phone, name ->
                onAddAccount(phone, name)
                addingNumber = false
            },
        )
    }

    disconnectCandidate?.let { account ->
        AlertDialog(
            onDismissRequest = { disconnectCandidate = null },
            containerColor = CortexSurface,
            titleContentColor = CortexText,
            textContentColor = CortexMuted,
            title = { Text("Pause ${account.title}?") },
            text = {
                Text(
                    "This pauses the WhatsApp connection, even across MSCC restarts, without deleting saved authentication. " +
                        "Use Resume to reconnect whenever you want."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisconnect(account.id)
                        disconnectCandidate = null
                    },
                    modifier = Modifier.testTag("confirm-disconnect-account"),
                ) {
                    Text("Pause session", color = CortexDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { disconnectCandidate = null }) { Text("Cancel") }
            },
        )
    }

    removeCandidate?.let { account ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            containerColor = CortexSurface,
            titleContentColor = CortexText,
            textContentColor = CortexMuted,
            title = { Text("Remove ${account.title}?") },
            text = {
                Text(
                    "This removes the account from Cortex while preserving its saved sign-in state on the server. " +
                        "Cortex verifies that preservation in the response. You can only remove an account after choosing a different CC destination."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(account.id)
                        removeCandidate = null
                    }
                ) {
                    Text("Remove account", color = CortexDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeCandidate = null }) { Text("Cancel") }
            },
        )
    }

    destinationCandidate?.let { account ->
        DestinationSheet(
            account = account,
            onDismiss = { destinationCandidate = null },
            onConfirm = {
                onDestination(account.id)
                destinationCandidate = null
            },
        )
    }

    repairConfirmation?.let { (account, mode) ->
        AlertDialog(
            onDismissRequest = { repairConfirmation = null },
            containerColor = CortexSurface,
            titleContentColor = CortexText,
            textContentColor = CortexMuted,
            title = { Text("Re-pair ${account.title}?") },
            text = {
                Text(
                    "MSCC will back up this account's saved WhatsApp authentication and start " +
                        (if (mode == "qr") "QR" else "phone-code") +
                        " pairing. Re-pair only after regular reconnect fails, or when sign-in is invalid."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        onRepair(account.id, mode)
                        repairConfirmation = null
                    },
                    modifier = Modifier.testTag("confirm-repair-session"),
                ) { Text("Back up & re-pair", color = CortexDanger) }
            },
            dismissButton = {
                TextButton(onClick = { repairConfirmation = null }) { Text("Cancel") }
            },
        )
    }

    selected?.let { account ->
        PairMethodSheet(
            account = account,
            repair = action == PairAction.REPAIR,
            onDismiss = { selected = null },
            onCode = {
                if (action == PairAction.REPAIR) repairConfirmation = account to "code"
                else onPair(account.id, "code")
                selected = null
            },
            onQr = {
                if (action == PairAction.REPAIR) repairConfirmation = account to "qr"
                else onPair(account.id, "qr")
                selected = null
            },
        )
    }
}

@Composable
private fun PairingAccountCard(
    account: PairingAccount,
    destination: Boolean,
    duplicateName: Boolean,
    busy: Boolean,
    onPair: () -> Unit,
    onDestination: () -> Unit,
    onReconnect: () -> Unit,
    onRename: () -> Unit,
    onEditProfile: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit,
    onRepair: () -> Unit,
) {
    val context = LocalContext.current
    val normalizedStatus = account.status.lowercase()
    val requiresRepair = normalizedStatus in setOf(
        "auth-invalid",
        "logged-out",
        "revoked",
        "session-expired",
        "expired",
    )
    val pairingActive = !account.connected &&
        account.pairingError.isBlank() &&
        (
            normalizedStatus == "pairing" ||
                account.pairingCode.isNotBlank() ||
                account.pairingQr.isNotBlank()
        )
    val reconnecting = normalizedStatus in setOf("connecting", "reconnecting")
    Surface(color = CortexSurface, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(CortexSurface2, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        account.badge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (duplicateName) "${account.title} [${account.id}]" else account.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("session-title-${account.id}"),
                    )
                    Text(
                        "ID: ${account.id} · ${account.accountRoleLabel}",
                        color = CortexMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("session-identity-${account.id}"),
                    )
                    Text(
                        account.numberMasked,
                        color = CortexMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(account.status)
                    if (destination) {
                        Spacer(Modifier.height(4.dp))
                        Text("DESTINATION", color = CortexAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = CortexLine)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("BOT PROFILE", color = CortexMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(9.dp))
                Text(
                    account.profileLabel,
                    color = if (account.profile == "unassigned") CortexMuted else CortexText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("session-profile-${account.id}"),
                    textAlign = TextAlign.End,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onRename,
                    enabled = !busy,
                    modifier = Modifier.testTag("rename-account-${account.id}"),
                ) { Text("Rename", color = CortexAccent, fontSize = 11.sp) }
                if (account.id != "A") {
                    TextButton(
                        onClick = onEditProfile,
                        enabled = !busy,
                        modifier = Modifier.testTag("edit-profile-${account.id}"),
                    ) { Text("Change bot profile", color = CortexAccent, fontSize = 11.sp) }
                }
            }
            if (duplicateName) {
                Text(
                    "Duplicate saved name · verify this session's ID and number.",
                    color = CortexDanger,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 5.dp),
                )
            }

            if (account.pairingCode.isNotBlank()) {
                HorizontalDivider(color = CortexLine)
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("PAIRING CODE", color = CortexMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        account.pairingCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            copySensitivePairingCode(context, account.pairingCode)
                        },
                        shape = RoundedCornerShape(9.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copy code", fontSize = 11.sp)
                    }
                    Text(
                        "WhatsApp → Linked devices → Link with phone number",
                        color = CortexMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                    Text(
                        "This code is temporary. If it expires, start pairing again.",
                        color = CortexMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            if (account.pairingQr.isNotBlank()) {
                HorizontalDivider(color = CortexLine)
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("QR PAIRING", color = CortexMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    PairingQr(account.pairingQr)
                    Spacer(Modifier.height(6.dp))
                    Text("QR appears only because you selected QR pairing.", color = CortexMuted, fontSize = 10.sp)
                }
            }

            if (account.pairingError.isNotBlank()) {
                HorizontalDivider(color = CortexLine)
                Text(
                    account.pairingError,
                    modifier = Modifier.padding(12.dp),
                    color = CortexDanger,
                    fontSize = 11.sp,
                )
            }

            if (account.enabled && !destination) {
                HorizontalDivider(color = CortexLine)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy, onClick = onDestination)
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Make destination", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Recovered media will be sent to ${account.title}.", color = CortexMuted, fontSize = 10.sp)
                    }
                    Text("CHANGE", color = CortexAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!account.connected) {
                HorizontalDivider(color = CortexLine)
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!account.enabled) {
                        Text("This account is not configured on the server.", color = CortexMuted, fontSize = 11.sp)
                    } else if (requiresRepair) {
                        Button(
                            onClick = onRepair,
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = CortexAccent),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Re-pair account", fontSize = 11.sp)
                        }
                    } else if (pairingActive) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Waiting for link…", fontSize = 11.sp)
                        }
                    } else if (reconnecting) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Connecting…", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = onPair,
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = CortexAccent),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pair account", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onReconnect,
                            enabled = !busy,
                            shape = RoundedCornerShape(9.dp),
                        ) {
                            Icon(Icons.Rounded.RestartAlt, "Reconnect", Modifier.size(14.dp))
                        }
                    }
                }
            }
            if (account.enabled && (account.connected || !destination)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (account.connected) {
                        TextButton(
                            onClick = onDisconnect,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Pause", fontSize = 10.sp)
                        }
                    }
                    if (!destination) {
                        TextButton(
                            onClick = onRemove,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Delete, null, Modifier.size(13.dp), tint = CortexDanger)
                            Spacer(Modifier.width(4.dp))
                            Text("Remove account", color = CortexDanger, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun sessionClockTime(timeMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timeMs))

private fun copySensitivePairingCode(context: android.content.Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Cortex pairing code", code)
    val extras = clip.description.extras ?: PersistableBundle()
    extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    clip.description.extras = extras
    clipboard.setPrimaryClip(clip)

    // Android clears sensitive clipboard previews on modern releases, but
    // Cortex also bounds exposure on older supported versions. Only clear the
    // clipboard if the user has not copied something else in the meantime.
    Handler(Looper.getMainLooper()).postDelayed({
        val current = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (current == code) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }, 60_000L)
}

@Composable
private fun StatusPill(status: String) {
    val normalized = status.lowercase()
    val good = normalized == "connected"
    val bad = normalized in setOf("auth-invalid", "logged-out", "revoked", "session-expired", "expired", "failed", "error")
    Surface(
        color = when {
            good -> Color(0xFF166534)
            bad -> Color(0xFF7F1D1D)
            else -> Color(0xFF4B5563)
        },
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            when (normalized) {
                "connected" -> "CONNECTED"
                "paused" -> "PAUSED"
                "auth-invalid", "logged-out", "revoked", "session-expired", "expired" -> "SIGN-IN REQUIRED"
                "pairing" -> "PAIRING"
                "connecting" -> "CONNECTING"
                "reconnecting" -> "RECONNECTING"
                "pending" -> "WAITING"
                "failed", "error" -> "ERROR"
                else -> status.replace('-', ' ').uppercase()
            },
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PairingQr(dataUri: String) {
    val image = remember(dataUri) {
        runCatching {
            val encoded = dataUri.substringAfter("base64,", "")
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (image == null) {
        Box(
            Modifier
                .size(220.dp)
                .background(CortexSurface2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("QR is preparing…", color = CortexText, fontSize = 12.sp)
        }
    } else {
        Image(
            bitmap = image,
            contentDescription = "WhatsApp pairing QR",
            modifier = Modifier
                .size(220.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationSheet(
    account: PairingAccount,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
        contentColor = CortexText,
        scrimColor = Color.Black.copy(alpha = .68f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Change destination", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Use ${account.title} (${account.numberMasked}) as the private destination for recovered media?",
                color = CortexMuted,
                fontSize = 11.sp,
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
            ) {
                Text("Use ${account.title}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairMethodSheet(
    account: PairingAccount,
    repair: Boolean,
    onDismiss: () -> Unit,
    onCode: () -> Unit,
    onQr: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
        contentColor = CortexText,
        scrimColor = Color.Black.copy(alpha = .68f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("pair-method-sheet")
                .padding(bottom = 24.dp)
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    if (repair) "Re-pair ${account.title}" else "Pair ${account.title}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Choose how you want to link this account.",
                    color = CortexMuted,
                    fontSize = 11.sp,
                )
            }
            PairMethodRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Link with phone number",
                subtitle = "Recommended · generates a pairing code",
                primary = true,
                onClick = onCode,
            )
            PairMethodRow(
                icon = Icons.Rounded.QrCode2,
                title = "Use QR code",
                subtitle = "Only opens QR pairing when you explicitly choose it",
                primary = false,
                onClick = onQr,
            )
        }
    }
}

@Composable
private fun PairMethodRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(if (primary) CortexAccent.copy(alpha = .18f) else CortexSurface2, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (primary) CortexAccent else Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = CortexMuted, fontSize = 11.sp)
        }
        if (primary) {
            Text("PRIMARY", color = CortexAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}


private val PairingAccount.badge: String
    get() {
        val friendly = displayName.trim()
        if (friendly.isNotBlank()) {
            return friendly
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .take(2)
        }
        val numeric = id.substringAfterLast('-', id).takeLast(2)
        return numeric.ifBlank { id.take(2) }.uppercase()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNumberSheet(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val digits = phone.filter(Char::isDigit)
    val valid = digits.length in 7..15

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
        contentColor = CortexText,
        scrimColor = Color.Black.copy(alpha = .68f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add number", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Create a separate WhatsApp session. Its saved name is independent of its bot profile; unassigned profiles cannot run public commands.",
                color = CortexMuted,
                fontSize = 10.sp,
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                placeholder = { Text("234…") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                colors = cortexPairingTextFieldColors(),
                singleLine = true,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Friendly name (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = cortexPairingTextFieldColors(),
                singleLine = true,
            )
            Button(
                onClick = { onSubmit(digits, name.trim()) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
            ) {
                Text("Create account")
            }
        }
    }
}

@Composable
private fun cortexPairingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CortexText,
    unfocusedTextColor = CortexText,
    focusedContainerColor = CortexHeader.copy(alpha = .42f),
    unfocusedContainerColor = CortexHeader.copy(alpha = .42f),
    cursorColor = CortexAccent,
    focusedBorderColor = CortexAccent,
    unfocusedBorderColor = CortexLine,
    focusedLabelColor = CortexAccent,
    unfocusedLabelColor = CortexMuted,
    focusedPlaceholderColor = CortexMuted,
    unfocusedPlaceholderColor = CortexMuted,
)
