package com.night.tachymns

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Pine = Color(0xFF315B45)
private val Cream = Color(0xFFF8F7F3)
private val Ink = Color(0xFF1F2924)
private val LightPalette = lightColorScheme(
    primary = Pine, onPrimary = Color.White, background = Cream, onBackground = Ink,
    surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFE9ECE6),
    onSurfaceVariant = Color(0xFF5A6A60), outline = Color(0xFFD8DFD7)
)
// Warm graphite and ivory in dark mode; no green-on-black accent.
private val DarkPalette = darkColorScheme(
    primary = Color(0xFFE9CBA1), onPrimary = Color(0xFF29231D),
    background = Color(0xFF1B1C20), onBackground = Color(0xFFF6F3EE),
    surface = Color(0xFF292B30), onSurface = Color(0xFFF6F3EE),
    surfaceVariant = Color(0xFF36383E), onSurfaceVariant = Color(0xFFC4C0B9),
    outline = Color(0xFF4D5058)
)

class MainActivity : ComponentActivity() {
    private fun openYouTubeSearch(query: String) {
        val uri = Uri.parse(
            "https://www.youtube.com/results?search_query=" + Uri.encode(query)
        )
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No browser available for YouTube.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hymns = HymnRepository.load(this)
        val index = IndexRepository.load(this)
        val prefs = getSharedPreferences("reader", MODE_PRIVATE)
        setContent {
            val audioRepository = remember { HymnAudioRepository(applicationContext) }
            val audioPlayer = remember { HymnAudioPlayer(applicationContext) }
            var libraryVersion by remember { mutableIntStateOf(0) }
            var pendingAudioKey by rememberSaveable { mutableStateOf<String?>(null) }
            var audioMessage by remember { mutableStateOf<String?>(null) }
            val downloading = remember { mutableStateListOf<String>() }
            LaunchedEffect(audioMessage) {
                val notice = audioMessage
                if (notice != null) {
                    Toast.makeText(applicationContext, notice, Toast.LENGTH_LONG).show()
                    audioMessage = null
                }
            }
            val scope = rememberCoroutineScope()
            val importPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                val requested = AudioKey.parse(pendingAudioKey)
                pendingAudioKey = null
                if (uri != null && requested != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        audioRepository.import(requested, uri)
                        audioPlayer.stop()
                        libraryVersion++
                        audioMessage = null
                    }.onFailure { audioMessage = "Unable to access that audio file." }
                }
            }
            val onImportAudio: (AudioKey) -> Unit = { key ->
                pendingAudioKey = key.token
                importPicker.launch(arrayOf("audio/*"))
            }
            val onDownloadAudio: (AudioKey) -> Unit = { key ->
                if (key.token !in downloading) {
                    downloading.add(key.token)
                    scope.launch {
                        try {
                            audioRepository.download(key)
                            libraryVersion++
                            audioMessage = null
                        } catch (error: Exception) {
                            audioMessage = error.message ?: "Audio download failed"
                        } finally {
                            downloading.remove(key.token)
                        }
                    }
                }
            }
            val onExploreRecording: (Hymn, Boolean) -> Unit = { hymn, english ->
                val title = if (english) hymn.titleEn ?: hymn.titleYo else hymn.titleYo
                openYouTubeSearch("$title hymn singing ${if (english) "English" else "Yoruba"}")
            }
            val onExploreIndex: (HymnIndexEntry) -> Unit = { entry ->
                val title = entry.titleYo ?: entry.titleEn.orEmpty()
                openYouTubeSearch("$title hymn singing")
            }
            val onRemoveAudio: (StoredAudio) -> Unit = { entry ->
                if (audioPlayer.activeKey == entry.key) audioPlayer.stop()
                if (audioRepository.removeStoredAudio(entry)) libraryVersion++
            }
            DisposableEffect(audioPlayer) { onDispose { audioPlayer.release() } }
            // Keep timing and A-B passage repeat running even when a long hymn scrolls
            // the playback panel out of a lazy list's visible window.
            LaunchedEffect(audioPlayer.activeKey, audioPlayer.playing, audioPlayer.loading) {
                while (audioPlayer.activeKey != null && (audioPlayer.playing || audioPlayer.loading)) {
                    audioPlayer.refresh()
                    delay(250)
                }
            }
            var learnMode by rememberSaveable { mutableStateOf(false) }
            var offlineAudioOpen by rememberSaveable { mutableStateOf(false) }
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            var englishMode by rememberSaveable { mutableStateOf(false) }
            var hubQuery by rememberSaveable { mutableStateOf("") }
            var hubFilter by rememberSaveable { mutableIntStateOf(0) }
            var activeCollection by rememberSaveable {
                mutableStateOf(intent.getStringExtra("preview_collection").takeIf { it == "akokun" } ?: "main")
            }
            var selectedKey by rememberSaveable { mutableStateOf<String?>(intent.getIntExtra("preview_hymn", -1).takeIf { it > 0 }?.let { "main:$it" }) }
            var lastRead by remember { mutableStateOf(prefs.getString("last_key", null) ?: prefs.getInt("last", -1).takeIf { it > 0 }?.let { "main:$it" }) }
            var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1f)) }
            val saved = remember {
                mutableStateListOf<String>().apply {
                    addAll(prefs.getStringSet("saved", emptySet()).orEmpty().map { if (":" in it) it else "main:$it" })
                }
            }
            val palette = if (dark) DarkPalette else LightPalette
            SideEffect {
                window.statusBarColor = palette.background.toArgb()
                window.navigationBarColor = palette.background.toArgb()
                val controls = WindowCompat.getInsetsController(window, window.decorView)
                controls.isAppearanceLightStatusBars = !dark
                controls.isAppearanceLightNavigationBars = !dark
            }
            val openHymn: (Hymn) -> Unit = { hymn ->
                selectedKey = hymn.key
                lastRead = hymn.key
                prefs.edit().putString("last_key", hymn.key).apply()
            }
            val toggleSaved: (Hymn) -> Unit = { hymn ->
                if (saved.contains(hymn.key)) saved.remove(hymn.key) else saved.add(hymn.key)
                prefs.edit().putStringSet("saved", saved.toSet()).apply()
            }
            val selected = hymns.firstOrNull { it.key == selectedKey }
            LaunchedEffect(selectedKey, englishMode) {
                val active = audioPlayer.activeKey
                val language = if (englishMode && selected?.hasEnglish == true) "en" else "yo"
                if (active != null && (
                        selectedKey == null || active.hymnKey != selectedKey ||
                        active.language != language
                    )
                ) audioPlayer.stop()
            }
            BackHandler(enabled = selectedKey != null || learnMode || offlineAudioOpen) {
                when {
                    selectedKey != null -> selectedKey = null
                    offlineAudioOpen -> offlineAudioOpen = false
                    else -> learnMode = false
                }
            }
            MaterialTheme(colorScheme = palette) {
                Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
                    if (selected == null && offlineAudioOpen) {
                        OfflineRecordingsScreen(
                            repository = audioRepository,
                            hymns = hymns,
                            libraryVersion = libraryVersion,
                            onBack = { offlineAudioOpen = false },
                            onRemove = onRemoveAudio
                        )
                    } else if (selected == null && learnMode) {
                        LearnHymnsHome(
                            hymns = hymns,
                            onBack = { learnMode = false },
                            onOpen = { selectedKey = it.key }
                        )
                    } else if (selected == null) {
                        HymnHub(
                            hymns = hymns,
                            index = index,
                            saved = saved,
                            lastRead = lastRead,
                            dark = dark,
                            onOpen = openHymn,
                            onToggleSaved = toggleSaved,
                            query = hubQuery,
                            onQueryChange = { hubQuery = it },
                            filter = hubFilter,
                            onFilterChange = { hubFilter = it },
                            activeCollection = activeCollection,
                            onCollectionChange = {
                                activeCollection = it
                                hubFilter = if (it == "akokun" && hymns.none { h -> h.collection == it }) 1 else 0
                            },
                            onToggleDark = {
                                dark = !dark
                                prefs.edit().putBoolean("dark", dark).apply()
                            },
                            onLearn = { learnMode = true },
                            onOfflineAudio = { offlineAudioOpen = true },
                            onExploreIndex = onExploreIndex
                        )
                    } else if (learnMode) {
                        key(selected.key) {
                            LearnHymnReader(
                                hymn = selected,
                                english = englishMode,
                                onLanguageChange = { englishMode = it },
                                onBack = { selectedKey = null },
                                repository = audioRepository,
                                player = audioPlayer,
                                libraryVersion = libraryVersion,
                                downloading = downloading.toSet(),
                                onImport = onImportAudio,
                                onDownload = onDownloadAudio,
                                onExplore = onExploreRecording
                            )
                        }
                    } else {
                        key(selected.key) {
                        HymnReader(
                            hymn = selected,
                            previous = hymns.lastOrNull { it.collection == selected.collection && it.number < selected.number },
                            next = hymns.firstOrNull { it.collection == selected.collection && it.number > selected.number },
                            saved = selected.key in saved,
                            english = englishMode,
                            onLanguageChange = { englishMode = it },
                            fontScale = fontScale,
                            onChangeFontScale = {
                                fontScale = it.coerceIn(0.85f, 1.55f)
                                prefs.edit().putFloat("font_scale", fontScale).apply()
                            },
                            onBack = { selectedKey = null },
                            onOpen = openHymn,
                            onToggleSaved = { toggleSaved(selected) },
                            audioRepository = audioRepository,
                            audioPlayer = audioPlayer,
                            audioLibraryVersion = libraryVersion,
                            downloading = downloading.toSet(),
                            onImportAudio = onImportAudio,
                            onDownloadAudio = onDownloadAudio,
                            onExploreRecording = onExploreRecording
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HymnHub(
    hymns: List<Hymn>,
    index: List<HymnIndexEntry>,
    saved: List<String>,
    lastRead: String?,
    dark: Boolean,
    onOpen: (Hymn) -> Unit,
    onToggleSaved: (Hymn) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: Int,
    onFilterChange: (Int) -> Unit,
    activeCollection: String,
    onCollectionChange: (String) -> Unit,
    onToggleDark: () -> Unit,
    onLearn: () -> Unit,
    onOfflineAudio: () -> Unit,
    onExploreIndex: (HymnIndexEntry) -> Unit
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val hasAkokun = index.any { it.collection == "akokun" }
    val hasReadable = hymns.any { it.collection == activeCollection }
    val showIndex = !hasReadable || filter == 1
    val visible = hymns.filter { hymn ->
        hymn.collection == activeCollection &&
            hymn.matches(query) && (filter != 2 || hymn.key in saved)
    }
    val indexed = index.filter { it.collection == activeCollection && it.matches(query) }
    val playable = hymns.associateBy { it.key }
    val latest = hymns.firstOrNull {
        it.key == lastRead && it.collection == activeCollection
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TAC", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Orin Ìyìn", fontFamily = FontFamily.Serif,
                        fontSize = 35.sp, fontWeight = FontWeight.SemiBold)
                    Text("Hymnbook", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                Box {
                    TextButton(onClick = { moreOpen = true }) { Text("More") }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Learn hymns") },
                            onClick = { moreOpen = false; onLearn() }
                        )
                        DropdownMenuItem(
                            text = { Text("Offline audio") },
                            onClick = { moreOpen = false; onOfflineAudio() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (dark) "Light mode" else "Dark mode") },
                            onClick = { moreOpen = false; onToggleDark() }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            SearchInput(query, onQueryChange)
            Spacer(Modifier.height(10.dp))
        }
        if (query.isBlank() && latest != null && !showIndex && filter != 2) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(latest) },
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_book), null, Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continue reading", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(latest.number.toString() + "  ·  " + latest.titleYo,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        }
                        Icon(painterResource(R.drawable.ic_next), null,
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        item {
            if (hasAkokun) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("main" to "Redemption", "akokun" to "Àkọkún").forEach { (id, label) ->
                        val chosen = activeCollection == id
                        Surface(
                            modifier = Modifier.clickable { onCollectionChange(id) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (chosen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
                                color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Text(if (showIndex) "Index" else if (filter == 2) "Saved" else "Hymns",
                fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            if (hasReadable) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Read", 1 to "Index", 2 to "Saved").forEach { (mode, label) ->
                        val active = filter == mode
                        Surface(
                            modifier = Modifier.clickable { onFilterChange(mode) },
                            shape = RoundedCornerShape(30.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        if (showIndex) {
            if (indexed.isEmpty()) {
                item { EmptyHymnResults() }
            } else {
                items(indexed, key = { it.key }) { entry ->
                    IndexRow(entry, playable[entry.key], onOpen, onExploreIndex)
                }
            }
        } else if (visible.isEmpty()) {
            item { EmptyHymnResults() }
        } else {
            items(visible, key = { it.key }) { hymn ->
                HymnRow(hymn, hymn.key in saved,
                    onClick = { onOpen(hymn) },
                    onToggleSaved = { onToggleSaved(hymn) })
            }
        }
    }
}

@Composable
private fun EmptyHymnResults() {
    Column(Modifier.fillMaxWidth().padding(vertical = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No hymns found", fontWeight = FontWeight.SemiBold)
        Text("Try a title or hymn number.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun IndexRow(
    entry: HymnIndexEntry,
    hymn: Hymn?,
    onOpen: (Hymn) -> Unit,
    onExploreIndex: (HymnIndexEntry) -> Unit
) {
    val tileModifier = if (hymn == null) Modifier.fillMaxWidth()
                       else Modifier.fillMaxWidth().clickable { onOpen(hymn) }
    Surface(modifier = tileModifier, color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f))) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
                    Text(entry.number.toString(), color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp)
                if (entry.titleYo != null && entry.titleEn != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(entry.titleEn, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp)
                }
            }
            if (hymn != null) {
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_next),
                    contentDescription = "Read hymn " + entry.number,
                    tint = MaterialTheme.colorScheme.primary)
            } else {
                IconButton(onClick = { onExploreIndex(entry) }) {
                    Icon(
                        painterResource(R.drawable.ic_audio),
                        contentDescription = "Find a recording for hymn " + entry.number,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchInput(value: String, onValueChange: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty()) {
                            Text("Search number, title or lyrics",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        field()
                    }
                }
            )
        }
    }
}

@Composable
private fun HymnRow(hymn: Hymn, saved: Boolean, onClick: () -> Unit, onToggleSaved: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f))
    ) {
        Row(Modifier.padding(start = 15.dp, end = 7.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
                    Text(hymn.number.toString(), color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(hymn.titleYo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                hymn.titleEn?.takeIf { it != hymn.titleYo }?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

            }
            IconButton(onClick = onToggleSaved, modifier = Modifier.size(44.dp)) {
                Icon(painterResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
                    contentDescription = if (saved) "Remove saved hymn" else "Save hymn",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HymnReader(
    hymn: Hymn,
    previous: Hymn?,
    next: Hymn?,
    saved: Boolean,
    english: Boolean,
    onLanguageChange: (Boolean) -> Unit,
    fontScale: Float,
    onChangeFontScale: (Float) -> Unit,
    onBack: () -> Unit,
    onOpen: (Hymn) -> Unit,
    onToggleSaved: () -> Unit,
    audioRepository: HymnAudioRepository,
    audioPlayer: HymnAudioPlayer,
    audioLibraryVersion: Int,
    downloading: Set<String>,
    onImportAudio: (AudioKey) -> Unit,
    onDownloadAudio: (AudioKey) -> Unit,
    onExploreRecording: (Hymn, Boolean) -> Unit
) {
    val showEnglish = (english || !hymn.hasYoruba) && hymn.hasEnglish
    val verses = if (showEnglish) hymn.versesEn else hymn.versesYo
    // A horizontal swipe changes hymn, without disturbing vertical scrolling through long verses.
    // Navigation buttons below remain for accessibility.
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(hymn.number, previous?.number, next?.number) {
                var travel = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onHorizontalDrag = { change, delta ->
                        travel += delta
                        change.consume()
                    },
                    onDragEnd = {
                        val threshold = 82.dp.toPx()
                        when {
                            travel < -threshold -> next?.let { onOpen(it) }
                            travel > threshold -> previous?.let { onOpen(it) }
                        }
                    }
                )
            }
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to hymns")
            }
            Text(hymn.sectionLabel + " " + hymn.number, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleSaved) {
                Icon(painterResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
                    contentDescription = if (saved) "Remove saved hymn" else "Save hymn",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 25.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Text(hymn.titleYo, fontFamily = FontFamily.Serif,
                    fontSize = 29.sp, lineHeight = 37.sp, fontWeight = FontWeight.Medium)
                hymn.titleEn?.takeIf { it != hymn.titleYo }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageTab("Yorùbá", !showEnglish, hymn.hasYoruba) { onLanguageChange(false) }
                    LanguageTab("English", showEnglish, hymn.hasEnglish) { onLanguageChange(true) }
                }
                Spacer(Modifier.height(18.dp))
                HymnAudioControls(
                    hymn = hymn,
                    english = showEnglish,
                    repository = audioRepository,
                    player = audioPlayer,
                    libraryVersion = audioLibraryVersion,
                    downloading = downloading,
                    onImport = onImportAudio,
                    onDownload = onDownloadAudio,
                    onExplore = onExploreRecording
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Text size", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { onChangeFontScale(fontScale - 0.1f) },
                        enabled = fontScale > 0.851f,
                        contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Text("A−")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onChangeFontScale(fontScale + 0.1f) },
                        enabled = fontScale < 1.549f,
                        contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Text("A+")
                    }
                }
            }
            if (verses.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp)) {
                            Text("Lyrics not added yet", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("This hymn is catalogued from the book. Its verses are being matched with available sources.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(verses.size) { index ->
                    SelectionContainer {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text((index + 1).toString() + ".",
                                fontSize = 16.sp * fontScale,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Text(verses[index].joinToString("\n"),
                                fontFamily = FontFamily.Serif,
                                fontSize = 19.sp * fontScale,
                                lineHeight = 30.sp * fontScale,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (hymn.textStatus.contains("partial")) {
                item {
                    // Only the captured portion of this hymn is available so far.
                    // Keep this unobtrusive and never present the text as complete.
                    Text("Continued in the printed hymnbook…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { previous?.let { onOpen(it) } }, enabled = previous != null) {
                    Icon(painterResource(R.drawable.ic_back), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(previous?.let { it.sectionLabel + " " + it.number } ?: "Previous")
                }
                TextButton(onClick = { next?.let { onOpen(it) } }, enabled = next != null) {
                    Text(next?.let { it.sectionLabel + " " + it.number } ?: "Next")
                    Spacer(Modifier.width(6.dp))
                    Icon(painterResource(R.drawable.ic_next), null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun LanguageTab(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
    }
}
