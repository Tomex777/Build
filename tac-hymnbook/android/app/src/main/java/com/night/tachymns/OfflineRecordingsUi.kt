package com.night.tachymns

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Storage controls never touch the original files provided through Android's picker.
 * Downloaded copies belong to the app and can be deleted independently.
 */
@Composable
fun OfflineRecordingsScreen(
    repository: HymnAudioRepository,
    hymns: List<Hymn>,
    libraryVersion: Int,
    onBack: () -> Unit,
    onRemove: (StoredAudio) -> Unit
) {
    val recordings = remember(libraryVersion) { repository.storedRecordings() }
    val names = remember(hymns) { hymns.associateBy { it.key } }
    val bytes = recordings.filter { it.kind == "Downloaded" }.sumOf { it.bytes }
    var toRemove by remember { mutableStateOf<StoredAudio?>(null) }
    toRemove?.let { entry ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(if (entry.kind == "Downloaded") "Delete download?" else "Remove imported recording?") },
            text = {
                Text(
                    if (entry.kind == "Downloaded")
                        "This removes the downloaded audio from the app. The hymn remains available to read."
                    else "This only removes the recording from TAC Hymns. Your original audio file stays untouched."
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(entry); toRemove = null }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to hymnbook")
            }
            Text("Offline audio", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item {
                Text(
                    "${recordings.size} saved ${if (recordings.size == 1) "recording" else "recordings"} · ${storageLabel(bytes)} in app downloads",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
            }
            if (recordings.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            "No audio saved yet. Open a hymn to listen online, download an available recording, or add one from your phone.",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(recordings, key = { it.kind + it.key.token }) { entry ->
                val hymn = names[entry.key.hymnKey]
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 7.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${if (entry.key.collection == "akokun") "Àkọkún" else "Redemption"} ${entry.key.number}",
                                fontWeight = FontWeight.SemiBold
                            )
                            if (hymn != null) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (entry.key.language == "en") hymn.titleEn ?: hymn.titleYo else hymn.titleYo,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            val variant = if (entry.key.language == "en") "English" else "Yorùbá"
                            val arrangement = if (entry.key.voice == "solo") "Solo" else "Choir"
                            val info = listOf(
                                variant, arrangement, entry.kind,
                                if (entry.kind == "Downloaded") storageLabel(entry.bytes) else null
                            ).filterNotNull().joinToString(" · ")
                            Text(
                                info,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { toRemove = entry }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

private fun storageLabel(bytes: Long): String =
    if (bytes < 1024L * 1024L) "${bytes / 1024L} KB"
    else String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
