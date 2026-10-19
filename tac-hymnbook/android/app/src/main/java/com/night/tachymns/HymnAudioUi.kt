package com.night.tachymns

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Ordinary reader: one small playback control beneath existing language tabs.
 * Training controls are deliberately exclusive to the separately entered Learn Hymns screen.
 */
@Composable
fun HymnAudioControls(
    hymn: Hymn,
    english: Boolean,
    repository: HymnAudioRepository,
    player: HymnAudioPlayer,
    libraryVersion: Int,
    downloading: Set<String>,
    onImport: (AudioKey) -> Unit,
    onDownload: (AudioKey) -> Unit,
    learning: Boolean = false
) {
    var voice by rememberSaveable(hymn.key, english, learning) { mutableStateOf("solo") }
    val key = AudioKey(hymn.collection, hymn.number, if (english) "en" else "yo", voice)
    val source = remember(key, libraryVersion) { repository.source(key) }
    val approved = remember(key) { repository.recording(key) }
    val isActive = player.activeKey == key

    LaunchedEffect(player.activeKey, player.playing, player.loading) {
        while (player.activeKey != null && (player.playing || player.loading)) {
            player.refresh()
            delay(250)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f))
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_audio), contentDescription = null,
                    modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Recording", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                listOf("solo" to "Solo", "choir" to "Choir").forEach { (type, label) ->
                    val selected = voice == type
                    Surface(
                        modifier = Modifier.clickable { voice = type },
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            if (source == null) {
                Text(
                    "No ${if (voice == "solo") "solo" else "choir"} recording available in " +
                        (if (english) "English" else "Yorùbá") + " yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onImport(key) }) { Text("Add recording") }
                    if (approved != null) {
                        TextButton(
                            onClick = { onDownload(key) },
                            enabled = key.token !in downloading
                        ) {
                            Text(if (key.token in downloading) "Downloading…" else "Download")
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { player.toggle(key, source) },
                        modifier = Modifier.size(43.dp)
                    ) {
                        Icon(
                            painterResource(
                                if (isActive && player.playing) R.drawable.ic_pause
                                else R.drawable.ic_play
                            ),
                            contentDescription = if (isActive && player.playing) "Pause" else "Play recording",
                            modifier = Modifier.size(25.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    val position = if (isActive) player.positionMs else 0
                    val duration = if (isActive) player.durationMs else 0
                    var scrubbing by remember(key) { mutableStateOf(false) }
                    var scrubValue by remember(key) { mutableFloatStateOf(0f) }
                    Slider(
                        value = if (scrubbing) scrubValue else {
                            if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        },
                        onValueChange = {
                            scrubValue = it
                            scrubbing = true
                        },
                        onValueChangeFinished = {
                            if (isActive && duration > 0) player.seekTo((scrubValue * duration).toInt())
                            scrubbing = false
                        },
                        enabled = isActive && duration > 0 && !player.loading,
                        modifier = Modifier.weight(1f).height(36.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${formatAudioTime(position)} / ${formatAudioTime(duration)}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // File replacement is optional; no second play button or duplicate audio controls.
                TextButton(onClick = { onImport(key) }, contentPadding = PaddingValues(horizontal = 3.dp)) {
                    Text("Change recording", fontSize = 12.sp)
                }
            }
            if (isActive && player.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (isActive && player.error != null) {
                Text(
                    player.error.orEmpty(),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error
                )
            }
            if (learning) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (source == null) {
                    Text("Add or download a recording to practise passages.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    Text("Practise a passage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Play the hymn, mark the start and end of a verse, then it will repeat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { player.markStart() },
                            enabled = isActive && player.durationMs > 0
                        ) { Text("Start ${if (isActive) player.startMark?.let(::formatAudioTime) ?: "" else ""}") }
                        OutlinedButton(
                            onClick = { player.markEnd() },
                            enabled = isActive && player.startMark != null && player.positionMs > player.startMark!! + 250
                        ) { Text("End ${if (isActive) player.endMark?.let(::formatAudioTime) ?: "" else ""}") }
                        if (isActive && player.startMark != null) {
                            TextButton(onClick = { player.clearLoop() }) { Text("Clear") }
                        }
                    }
                    if (isActive && player.loopEnabled) {
                        Text("Repeating selected passage", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Speed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        listOf(0.75f, 0.85f, 1f).forEach { rate ->
                            FilterChip(
                                selected = player.rate == rate,
                                onClick = { player.setRate(rate) },
                                label = { Text(if (rate == 1f) "Normal" else "${rate}×", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatAudioTime(milliseconds: Int): String {
    val total = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun LearnHymnsHome(
    hymns: List<Hymn>,
    onBack: () -> Unit,
    onOpen: (Hymn) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var collection by rememberSaveable { mutableStateOf("main") }
    val filtered = hymns.filter {
        it.collection == collection && it.matches(query)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to hymnbook")
            }
            Text("Learn hymns", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Choose a hymn to practise", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("main" to "Redemption", "akokun" to "Àkọkún").forEach { (id, label) ->
                        FilterChip(selected = id == collection, onClick = { collection = id },
                            label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Styled field, not a platform default EditText.
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("Hymn title or number") },
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    Text("No hymns with lyrics in this section yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(filtered, key = { it.key }) { hymn ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(hymn) },
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(hymn.number.toString(), color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hymn.titleYo, fontWeight = FontWeight.Medium)
                            hymn.titleEn?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(it, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(painterResource(R.drawable.ic_next), null)
                    }
                }
            }
        }
    }
}

@Composable
fun LearnHymnReader(
    hymn: Hymn,
    english: Boolean,
    onLanguageChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    repository: HymnAudioRepository,
    player: HymnAudioPlayer,
    libraryVersion: Int,
    downloading: Set<String>,
    onImport: (AudioKey) -> Unit,
    onDownload: (AudioKey) -> Unit
) {
    val actualEnglish = english && hymn.hasEnglish
    val verses = if (actualEnglish) hymn.versesEn else hymn.versesYo
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to learning")
            }
            Text("Learn · ${if (hymn.collection == "akokun") "Àkọkún" else "Redemption"} ${hymn.number}",
                fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            item {
                Text(hymn.titleYo, fontFamily = FontFamily.Serif, fontSize = 26.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 33.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Yorùbá", true to "English").forEach { (isEnglish, label) ->
                        val available = if (isEnglish) hymn.hasEnglish else hymn.hasYoruba
                        FilterChip(
                            selected = actualEnglish == isEnglish,
                            enabled = available, onClick = { onLanguageChange(isEnglish) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))
                HymnAudioControls(
                    hymn, actualEnglish, repository, player, libraryVersion,
                    downloading, onImport, onDownload, learning = true
                )
            }
            items(verses.size) { index ->
                SelectionContainer {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${index + 1}.", color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold)
                        Text(verses[index].joinToString("\n"), fontFamily = FontFamily.Serif,
                            fontSize = 19.sp, lineHeight = 30.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
