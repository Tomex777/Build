from pathlib import Path

home = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/HomeScreen.kt")
text = home.read_text()

old_import = "import com.night.sora.model.MediaProgressEntry\n"
new_import = "import com.night.sora.model.MediaProgressEntry\nimport com.night.sora.model.ListeningSignal\n"
if text.count(old_import) != 1:
    raise SystemExit("Home model import marker mismatch")
text = text.replace(old_import, new_import, 1)

old_sig = '''    progressEntries: List<MediaProgressEntry>,
    extensions: List<InstalledExtension>,'''
new_sig = '''    progressEntries: List<MediaProgressEntry>,
    listeningSignals: List<ListeningSignal>,
    extensions: List<InstalledExtension>,'''
if text.count(old_sig) != 1:
    raise SystemExit("Home signature marker mismatch")
text = text.replace(old_sig, new_sig, 1)

old_continue = '''    val continueEntries = progressEntries
        .filter { it.progress < .999f }
        .sortedByDescending { it.updatedAt }
        .take(8)
'''
new_continue = old_continue + '''    val recentMusic = remember(music, listeningSignals) {
        val artistOrder = listeningSignals
            .filter { it.lastPlayedEpochMs > 0L }
            .sortedByDescending { it.lastPlayedEpochMs }
            .mapIndexed { index, signal -> signal.artistName.trim().lowercase() to index }
            .toMap()
        music
            .filter { card -> card.subtitle.substringBefore(" · ").trim().lowercase() in artistOrder }
            .sortedBy { card -> artistOrder[card.subtitle.substringBefore(" · ").trim().lowercase()] ?: Int.MAX_VALUE }
            .take(8)
    }
'''
if text.count(old_continue) != 1:
    raise SystemExit("Home progress block marker mismatch")
text = text.replace(old_continue, new_continue, 1)

replacements = {
    'item { HomeSectionHeader("Continue", "Pick up exactly where you stopped", "History") }': 'item { HomeSectionHeader("Continue", "Pick up exactly where you stopped") }',
    'item { HomeSectionHeader("For you", "Picked from across Sora", "Refresh") }': 'item { HomeSectionHeader("Explore", "Fresh picks from across Sora") }',
    'Text("Your recommendations will fill in as Sora learns what you like.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))': 'Text("Fresh catalog picks will appear here when sources are available.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))',
    'item { HomeSectionHeader("You probably needed this", "Something from your feed", "More") }\n            item { MemeStrip(memes.firstOrNull()) }': '''if (memes.isNotEmpty()) {
                item { HomeSectionHeader("From your meme feed", "A fresh item from your active source") }
                item { MemeStrip(memes.first()) { onOpenSelection(memes.first().selection()) } }
            }''',
    'item { HomeSectionHeader("Bible", "Continue your reading", "Open", onSee = onOpenBible) }': 'item { HomeSectionHeader("Bible", "Featured passage", "Open", onSee = onOpenBible) }',
    'Text("John 1 · verse 5", color = SoraAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)': 'Text("John 1:5", color = SoraAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)',
    'Text("Last read · John 1:1–5", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))': 'Text("Open Bible to read in context", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))',
}
for old, new in replacements.items():
    if text.count(old) != 1:
        raise SystemExit(f"Home marker mismatch: {old[:50]}")
    text = text.replace(old, new, 1)

old_music = '''            item { HomeSectionHeader("Recently played", "Music stays with you across Sora", "Library") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(music.take(8), key = { "music-${it.id}" }) { card ->
                        HomeSquareTile(card) { onOpenSelection(card.selection()) }
                    }
                }
            }
'''
new_music = '''            if (recentMusic.isNotEmpty()) {
                item { HomeSectionHeader("Recently played", "From your actual listening history") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentMusic, key = { "music-${it.id}" }) { card ->
                            HomeSquareTile(card) { onOpenSelection(card.selection()) }
                        }
                    }
                }
            }
'''
if text.count(old_music) != 1:
    raise SystemExit("Recently played marker mismatch")
text = text.replace(old_music, new_music, 1)

old_header = '''@Composable
private fun HomeSectionHeader(title: String, subtitle: String, see: String, onSee: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        TextButton(onClick = onSee, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text(see, color = SoraMuted, fontSize = 12.sp) }
    }
}
'''
new_header = '''@Composable
private fun HomeSectionHeader(title: String, subtitle: String, see: String? = null, onSee: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (see != null && onSee != null) {
            TextButton(onClick = onSee, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text(see, color = SoraMuted, fontSize = 12.sp) }
        }
    }
}
'''
if text.count(old_header) != 1:
    raise SystemExit("HomeSectionHeader marker mismatch")
text = text.replace(old_header, new_header, 1)

old_meme = '''@Composable
private fun MemeStrip(meme: HomeBrowseCard?) {
    Surface(color = Color(0xFFF0EDE5), contentColor = Color(0xFF141412), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (meme != null) "Sora · now" else "Sora · ready offline", color = Color(0xFF656158), fontSize = 11.sp)
                Icon(Icons.Rounded.MoreHoriz, null, tint = Color(0xFF656158), modifier = Modifier.size(18.dp))
            }
            Text(meme?.title ?: "me opening Sora to continue one manga and somehow starting four things", fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFC5C0B3)), contentAlignment = Alignment.Center) {
                if (!meme?.artworkUrl.isNullOrBlank()) AsyncImage(meme?.artworkUrl, meme?.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.TagFaces, null, modifier = Modifier.size(66.dp), tint = Color(0xFF5C594F))
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("♡ Save", color = Color(0xFF6D685D), fontSize = 12.sp)
                Text("↗ Share", color = Color(0xFF6D685D), fontSize = 12.sp)
                Text("Less like this", color = Color(0xFF6D685D), fontSize = 12.sp)
            }
        }
    }
}
'''
new_meme = '''@Composable
private fun MemeStrip(meme: HomeBrowseCard, onClick: () -> Unit) {
    Surface(
        color = Color(0xFFF0EDE5),
        contentColor = Color(0xFF141412),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clickable(onClick = onClick),
    ) {
        Column {
            Text("Sora · meme feed", color = Color(0xFF656158), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
            Text(meme.title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFC5C0B3)), contentAlignment = Alignment.Center) {
                if (!meme.artworkUrl.isNullOrBlank()) AsyncImage(meme.artworkUrl, meme.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.TagFaces, null, modifier = Modifier.size(66.dp), tint = Color(0xFF5C594F))
            }
            Text("Open", color = Color(0xFF6D685D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}
'''
if text.count(old_meme) != 1:
    raise SystemExit("MemeStrip marker mismatch")
text = text.replace(old_meme, new_meme, 1)
home.write_text(text)

app = Path("sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt")
text = app.read_text()
old = '''                    progressEntries = repository.mediaProgress,
                    extensions = extensions,'''
new = '''                    progressEntries = repository.mediaProgress,
                    listeningSignals = repository.listeningSignals,
                    extensions = extensions,'''
if text.count(old) != 1:
    raise SystemExit("SoraApp Home listening marker mismatch")
text = text.replace(old, new, 1)
app.write_text(text)
