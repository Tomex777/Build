package com.night.cortex.server

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.cortex.ui.theme.CortexAccent
import com.night.cortex.ui.theme.CortexDanger
import com.night.cortex.ui.theme.CortexGood
import com.night.cortex.ui.theme.CortexLine
import com.night.cortex.ui.theme.CortexMuted
import com.night.cortex.ui.theme.CortexSurface
import com.night.cortex.ui.theme.CortexSurface2

private enum class PairAction { PAIR, REPAIR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CortexPairingScreen(
    state: PairingState?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onPair: (String, String) -> Unit,
    onReconnect: (String) -> Unit,
    onRepair: (String, String) -> Unit,
) {
    var selected by remember { mutableStateOf<PairingAccount?>(null) }
    var action by remember { mutableStateOf(PairAction.PAIR) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("WhatsApp Pairing", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    state?.let { "Destination: Account ${it.destination} · MSCC ${it.version}" } ?: "Connect MSCC to manage linked accounts",
                    color = CortexMuted,
                    fontSize = 9.sp,
                )
            }
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(Icons.Rounded.Refresh, "Refresh pairing")
            }
        }
        HorizontalDivider(color = CortexLine)

        if (state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Link, null, tint = CortexMuted, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("MSCC pairing is unavailable.", color = CortexMuted)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onRefresh) { Text("Try again") }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.accounts, key = { it.id }) { account ->
                    PairingAccountCard(
                        account = account,
                        destination = state.destination == account.id,
                        busy = busy,
                        onPair = {
                            selected = account
                            action = PairAction.PAIR
                        },
                        onReconnect = { onReconnect(account.id) },
                        onRepair = {
                            selected = account
                            action = PairAction.REPAIR
                        },
                    )
                }
                item {
                    Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "Phone-number pairing code is the normal option. QR is available only when you choose it yourself.",
                            modifier = Modifier.padding(12.dp),
                            color = CortexMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }

    selected?.let { account ->
        PairMethodSheet(
            account = account,
            repair = action == PairAction.REPAIR,
            onDismiss = { selected = null },
            onCode = {
                if (action == PairAction.REPAIR) onRepair(account.id, "code") else onPair(account.id, "code")
                selected = null
            },
            onQr = {
                if (action == PairAction.REPAIR) onRepair(account.id, "qr") else onPair(account.id, "qr")
                selected = null
            },
        )
    }
}

@Composable
private fun PairingAccountCard(
    account: PairingAccount,
    destination: Boolean,
    busy: Boolean,
    onPair: () -> Unit,
    onReconnect: () -> Unit,
    onRepair: () -> Unit,
) {
    val context = LocalContext.current
    Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(CortexSurface2, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(account.id, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Account ${account.id}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${account.numberMasked} · Index ${account.indexCount}/${account.indexLimit}",
                        color = CortexMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(account.status)
                    if (destination) {
                        Spacer(Modifier.height(4.dp))
                        Text("DESTINATION", color = CortexAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (account.pairingCode.isNotBlank()) {
                HorizontalDivider(color = CortexLine)
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("PAIRING CODE", color = CortexMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("MSCC pairing code", account.pairingCode))
                        },
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copy code", fontSize = 10.sp)
                    }
                    Text(
                        "WhatsApp → Linked devices → Link with phone number",
                        color = CortexMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 7.dp),
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
                    Text("QR appears only because you selected QR pairing.", color = CortexMuted, fontSize = 9.sp)
                }
            }

            if (account.pairingError.isNotBlank()) {
                HorizontalDivider(color = CortexLine)
                Text(
                    account.pairingError,
                    modifier = Modifier.padding(12.dp),
                    color = CortexDanger,
                    fontSize = 9.sp,
                )
            }

            HorizontalDivider(color = CortexLine)
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!account.enabled) {
                    Text("This account is not configured on the server.", color = CortexMuted, fontSize = 9.sp)
                } else if (account.connected) {
                    OutlinedButton(
                        onClick = onReconnect,
                        enabled = !busy,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reconnect", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onRepair,
                        enabled = !busy,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Re-pair", fontSize = 10.sp)
                    }
                } else {
                    Button(
                        onClick = onPair,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = CortexAccent),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pair account", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onReconnect,
                        enabled = !busy,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val good = status.equals("connected", true)
    val bad = status.equals("auth-invalid", true)
    Surface(
        color = when {
            good -> Color(0xFF166534)
            bad -> Color(0xFF7F1D1D)
            else -> Color(0xFF4B5563)
        },
        shape = RoundedCornerShape(3.dp),
    ) {
        Text(
            status.replace('-', ' ').uppercase(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = 7.sp,
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
                .background(Color.White, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("QR is preparing…", color = Color.Black, fontSize = 10.sp)
        }
    } else {
        Image(
            bitmap = image,
            contentDescription = "WhatsApp pairing QR",
            modifier = Modifier
                .size(220.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .padding(8.dp),
        )
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CortexSurface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(
                    if (repair) "Re-pair Account ${account.id}" else "Pair Account ${account.id}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Choose how you want to link this account.",
                    color = CortexMuted,
                    fontSize = 10.sp,
                )
            }
            PairMethodRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Link with phone number",
                subtitle = "Recommended on this phone · generates the pairing code",
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
                .size(38.dp)
                .background(if (primary) CortexAccent.copy(alpha = .18f) else CortexSurface2, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (primary) CortexAccent else Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = CortexMuted, fontSize = 9.sp)
        }
        if (primary) {
            Text("PRIMARY", color = CortexAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}
