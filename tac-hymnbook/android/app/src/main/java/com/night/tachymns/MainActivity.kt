package com.night.tachymns

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hymns = HymnRepository.load(this)
        val prefs = getSharedPreferences("reader", MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            var englishMode by rememberSaveable { mutableStateOf(false) }
            var hubQuery by rememberSaveable { mutableStateOf("") }
            var hubFilter by rememberSaveable { mutableIntStateOf(0) }
            var selectedNumber by rememberSaveable { mutableStateOf<Int?>(intent.getIntExtra("preview_hymn", -1).takeIf { it > 0 }) }
            var lastRead by remember { mutableIntStateOf(prefs.getInt("last", -1)) }
            var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1f)) }
            val saved = remember {
                mutableStateListOf<Int>().apply {
                    addAll(prefs.getStringSet("saved", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() })
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
            val openHymn: (Int) -> Unit = { number ->
                selectedNumber = number
                lastRead = number
                prefs.edit().putInt("last", number).apply()
            }
            val toggleSaved: (Int) -> Unit = { number ->
                if (saved.contains(number)) saved.remove(number) else saved.add(number)
                prefs.edit().putStringSet("saved", saved.map { it.toString() }.toSet()).apply()
            }
            val selected = hymns.firstOrNull { it.number == selectedNumber }
            BackHandler(enabled = selectedNumber != null) { selectedNumber = null }
            MaterialTheme(colorScheme = palette) {
                Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
                    if (selected == null) {
                        HymnHub(
                            hymns, saved, lastRead, dark, openHymn, toggleSaved,
                            query = hubQuery,
                            onQueryChange = { hubQuery = it },
                            filter = hubFilter,
                            onFilterChange = { hubFilter = it },
                            onToggleDark = {
                                dark = !dark
                                prefs.edit().putBoolean("dark", dark).apply()
                            }
                        )
                    } else {
                        key(selected.number) {
                        HymnReader(
                            hymn = selected,
                            previous = hymns.lastOrNull { it.number < selected.number },
                            next = hymns.firstOrNull { it.number > selected.number },
                            saved = selected.number in saved,
                            english = englishMode,
                            onLanguageChange = { englishMode = it },
                            fontScale = fontScale,
                            onChangeFontScale = {
                                fontScale = it.coerceIn(0.85f, 1.55f)
                                prefs.edit().putFloat("font_scale", fontScale).apply()
                            },
                            onBack = { selectedNumber = null },
                            onOpen = openHymn,
                            onToggleSaved = { toggleSaved(selected.number) }
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
    saved: List<Int>,
    lastRead: Int,
    dark: Boolean,
    onOpen: (Int) -> Unit,
    onToggleSaved: (Int) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: Int,
    onFilterChange: (Int) -> Unit,
    onToggleDark: () -> Unit
) {
    val visible = hymns.filter { hymn ->
        hymn.matches(query) && (filter != 1 || hymn.number in saved)
    }
    val latest = hymns.firstOrNull { it.number == lastRead }
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
                TextButton(onClick = onToggleDark) {
                    Text(if (dark) "Light mode" else "Dark mode")
                }
            }
            Spacer(Modifier.height(24.dp))
            SearchInput(query, onQueryChange)
            Spacer(Modifier.height(10.dp))
        }
        if (query.isBlank() && latest != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(latest.number) },
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
            Text("Hymns", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Saved").forEachIndexed { index, label ->
                    val active = filter == index
                    Surface(
                        modifier = Modifier.clickable { onFilterChange(index) },
                        shape = RoundedCornerShape(30.dp),
                        color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            color = if (active) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp)
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hymns found", fontWeight = FontWeight.SemiBold)
                    Text("Try a title, lyric, or hymn number.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            items(visible, key = { it.number }) { hymn ->
                HymnRow(hymn, hymn.number in saved,
                    onClick = { onOpen(hymn.number) },
                    onToggleSaved = { onToggleSaved(hymn.number) })
            }
        }
    }
}

@Composable
private fun SearchInput(value: String, onValueChange: (String) -> Unit) {
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
                hymn.titleEn?.let {
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
    onOpen: (Int) -> Unit,
    onToggleSaved: () -> Unit
) {
    val showEnglish = english && hymn.hasEnglish
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
                            travel < -threshold -> next?.let { onOpen(it.number) }
                            travel > threshold -> previous?.let { onOpen(it.number) }
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
            Text("Hymn " + hymn.number, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
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
                hymn.titleEn?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageTab("Yorùbá", !showEnglish, hymn.hasYoruba) { onLanguageChange(false) }
                    LanguageTab("English", showEnglish, hymn.hasEnglish) { onLanguageChange(true) }
                }
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
                TextButton(onClick = { previous?.let { onOpen(it.number) } }, enabled = previous != null) {
                    Icon(painterResource(R.drawable.ic_back), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(previous?.let { "Hymn " + it.number } ?: "Previous")
                }
                TextButton(onClick = { next?.let { onOpen(it.number) } }, enabled = next != null) {
                    Text(next?.let { "Hymn " + it.number } ?: "Next")
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
