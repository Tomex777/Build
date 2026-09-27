package com.night.cortex.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.cortex.ui.theme.CortexAccent
import com.night.cortex.ui.theme.CortexMuted
import com.night.cortex.ui.theme.CortexSurface
import com.night.cortex.ui.theme.CortexSurface2
import java.text.DateFormat
import java.util.Date

private data class HealthRow(
    val label: String,
    val status: String,
    val detail: String,
    val good: Boolean?,
)

@Composable
fun CortexHealthScreen(state: ServerPanelState) {
    val snapshot = state.snapshot
    val pairing = state.pairing
    val msccResponding = state.agentReachable && (pairing != null || state.runtimeRegistry != null)
    val nightLive = state.agentReachable && snapshot != null
    val nightRunning = nightLive && (
        snapshot?.state.equals("active", true) || snapshot?.state.equals("running", true)
    )

    val rows = listOf(
        HealthRow(
            label = "PHONE",
            status = if (state.phoneOnline) "ONLINE" else "OFFLINE",
            detail = if (state.phoneOnline) "Android has an active network." else "No active phone network is available.",
            good = state.phoneOnline,
        ),
        HealthRow(
            label = "AGENT",
            status = when {
                !state.configured -> "NOT CONFIGURED"
                state.agentReachable -> "REACHABLE"
                else -> "UNREACHABLE"
            },
            detail = when {
                !state.configured -> "Connect the HTTPS Cortex Agent endpoint and token."
                state.agentReachable -> "Authenticated Cortex Agent requests are succeeding."
                else -> "The app cannot currently reach the configured Agent."
            },
            good = when {
                !state.configured -> null
                else -> state.agentReachable
            },
        ),
        HealthRow(
            label = "VM",
            status = if (state.agentReachable && snapshot != null) "REACHABLE" else "UNKNOWN",
            detail = if (state.agentReachable && snapshot != null) {
                "VM uptime ${healthUptime(snapshot.vmUptimeMs)} · disk ${healthBytes(snapshot.diskUsedBytes)} / ${healthBytes(snapshot.diskLimitBytes)}"
            } else {
                "VM state is not inferred while the live Agent is unavailable."
            },
            good = if (state.agentReachable && snapshot != null) true else null,
        ),
        HealthRow(
            label = "MSCC",
            status = when {
                msccResponding -> "RESPONDING"
                state.agentReachable -> "UNAVAILABLE"
                else -> "UNKNOWN"
            },
            detail = when {
                msccResponding -> "MSCC control data is returning through the Agent."
                state.agentReachable -> "Agent is reachable, but MSCC control data is unavailable."
                else -> "MSCC state is not inferred from stale data."
            },
            good = when {
                msccResponding -> true
                state.agentReachable -> false
                else -> null
            },
        ),
        HealthRow(
            label = "NIGHT",
            status = when {
                !nightLive -> "UNKNOWN"
                nightRunning -> "RUNNING"
                else -> snapshot?.state?.uppercase() ?: "UNKNOWN"
            },
            detail = when {
                !nightLive && snapshot != null -> "Live Agent unavailable. Last known service state: ${snapshot.state}."
                !nightLive -> "No live service state is available."
                else -> "Service uptime ${healthUptime(snapshot?.uptimeMs)} · systemd result ${snapshot?.serviceResult ?: "unknown"}."
            },
            good = when {
                !nightLive -> null
                nightRunning -> true
                else -> false
            },
        ),
        HealthRow(
            label = "PAIRING ACCOUNTS",
            status = if (pairing == null || !state.agentReachable) "UNKNOWN" else {
                val connected = pairing.accounts.count { it.connected }
                "$connected / ${pairing.accounts.size} CONNECTED"
            },
            detail = if (pairing == null || !state.agentReachable) {
                "Live pairing state is unavailable."
            } else {
                val destination = pairing.accounts.firstOrNull { it.id == pairing.destination }?.displayName
                    ?.takeIf { it.isNotBlank() } ?: pairing.destination
                "Destination: $destination · registry ${pairing.accounts.size} account(s)."
            },
            good = null,
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Health", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Live state only. Cortex does not turn stale values into green status.",
                    color = CortexMuted,
                    fontSize = 10.sp,
                )
                state.lastSuccessfulSyncAt?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Last successful Agent sync: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)),
                        color = CortexMuted,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        rows.forEach { row ->
            item(key = row.label) { HealthCard(row) }
        }
    }
}

@Composable
private fun HealthCard(row: HealthRow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CortexSurface,
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.label, color = CortexMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(row.detail, fontSize = 10.sp, color = Color(0xFFE5E8EB))
            }
            Surface(
                color = when (row.good) {
                    true -> Color(0xFF166534)
                    false -> Color(0xFF7F1D1D)
                    null -> CortexSurface2
                },
                shape = RoundedCornerShape(3.dp),
            ) {
                Text(
                    row.status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = if (row.good == null) CortexAccent else Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun healthUptime(value: Long?): String {
    if (value == null) return "—"
    val seconds = value / 1000
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "$days d $hours h"
        hours > 0 -> "$hours h $minutes m"
        else -> "$minutes m"
    }
}

private fun healthBytes(value: Long?): String {
    if (value == null) return "—"
    return when {
        value >= 1024L * 1024L * 1024L -> String.format("%.1f GB", value / 1024.0 / 1024.0 / 1024.0)
        value >= 1024L * 1024L -> String.format("%.1f MB", value / 1024.0 / 1024.0)
        value >= 1024L -> String.format("%.0f KB", value / 1024.0)
        else -> "$value B"
    }
}
