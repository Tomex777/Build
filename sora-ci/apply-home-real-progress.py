from pathlib import Path

home = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/HomeScreen.kt")
text = home.read_text()

text = text.replace(
    "import com.night.sora.model.LibraryEntry\n",
    "import com.night.sora.model.MediaProgressEntry\n",
    1,
)

old_signature = '''fun HomeScreen(
    modifier: Modifier = Modifier,
    entries: List<LibraryEntry>,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    onOpenSelection: (ExtensionMediaSelection) -> Unit,
    onOpenBible: () -> Unit,
    onSearch: () -> Unit,
) {'''
new_signature = '''fun HomeScreen(
    modifier: Modifier = Modifier,
    progressEntries: List<MediaProgressEntry>,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    onOpenSelection: (ExtensionMediaSelection) -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,
    onOpenBible: () -> Unit,
    onSearch: () -> Unit,
) {'''
if text.count(old_signature) != 1:
    raise SystemExit("HomeScreen signature marker mismatch")
text = text.replace(old_signature, new_signature, 1)

old_continue = "    val continueEntries = entries.filter { it.contentType != null }.take(8)\n"
new_continue = '''    val continueEntries = progressEntries
        .filter { it.progress < .999f }
        .sortedByDescending { it.updatedAt }
        .take(8)
'''
if text.count(old_continue) != 1:
    raise SystemExit("Home continue entries marker mismatch")
text = text.replace(old_continue, new_continue, 1)

old_row = '''                        items(continueEntries, key = { it.id }) { entry ->
                            ContinueCard(entry) { entry.toMediaSelection()?.let(onOpenSelection) }
                        }
'''
new_row = '''                        items(
                            continueEntries,
                            key = { "${it.extensionPackage}:${it.sourceId}:${it.mediaId}:${it.itemId}" },
                        ) { entry ->
                            ContinueCard(entry) { onResumeProgress(entry) }
                        }
'''
if text.count(old_row) != 1:
    raise SystemExit("Home continue row marker mismatch")
text = text.replace(old_row, new_row, 1)

old_card_start = '''@Composable
private fun ContinueCard(entry: LibraryEntry, onClick: () -> Unit) {
    Row(Modifier.width(286.dp).height(154.dp).clip(RoundedCornerShape(18.dp)).background(SoraSurface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxHeight().width(118.dp).background(Color(0xFF2A2925)), contentAlignment = Alignment.Center) {
            if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(entry.label.take(1), fontSize = 36.sp, fontWeight = FontWeight.Black, color = SoraMuted)
        }
        Column(Modifier.fillMaxHeight().weight(1f).padding(13.dp)) {
            Text(entry.kind.uppercase(), color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            Text(entry.label, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            Text(entry.detail, color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(progress = { .62f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = SoraAccent, trackColor = Color(0xFF49473F))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(SoraAccent, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = SoraAccentInk, modifier = Modifier.size(13.dp)) }
                Text("Resume", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
'''
new_card_start = '''@Composable
private fun ContinueCard(entry: MediaProgressEntry, onClick: () -> Unit) {
    Row(Modifier.width(286.dp).height(154.dp).clip(RoundedCornerShape(18.dp)).background(SoraSurface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxHeight().width(118.dp).background(Color(0xFF2A2925)), contentAlignment = Alignment.Center) {
            if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(entry.title.take(1), fontSize = 36.sp, fontWeight = FontWeight.Black, color = SoraMuted)
        }
        Column(Modifier.fillMaxHeight().weight(1f).padding(13.dp)) {
            Text(entry.contentType.label.uppercase(), color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            Text(entry.title, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            Text(entry.itemLabel, color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(progress = { entry.progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = SoraAccent, trackColor = Color(0xFF49473F))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(SoraAccent, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = SoraAccentInk, modifier = Modifier.size(13.dp)) }
                Text("Resume · ${(entry.progress * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
'''
if text.count(old_card_start) != 1:
    raise SystemExit("ContinueCard marker mismatch")
text = text.replace(old_card_start, new_card_start, 1)
home.write_text(text)

app = Path("sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt")
text = app.read_text()
old_call = '''                RootTab.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    entries = repository.library,
                    extensions = extensions,
                    manager = extensionManager,
                    onOpenSelection = ::openMedia,
                    onOpenBible = { push(AppScreen.Bible) },
                    onSearch = { tab = RootTab.MEDIA },
                )'''
new_call = '''                RootTab.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    progressEntries = repository.mediaProgress,
                    extensions = extensions,
                    manager = extensionManager,
                    onOpenSelection = ::openMedia,
                    onResumeProgress = { entry ->
                        resumeMediaProgress(
                            entry = entry,
                            extensions = extensions,
                            manager = extensionManager,
                            onOpenReader = { push(AppScreen.Reader(it)) },
                            onOpenPlayer = { push(AppScreen.VideoPlayer(it)) },
                            onFallback = ::openMedia,
                        )
                    },
                    onOpenBible = { push(AppScreen.Bible) },
                    onSearch = { tab = RootTab.MEDIA },
                )'''
if text.count(old_call) != 1:
    raise SystemExit("SoraApp HomeScreen call marker mismatch")
text = text.replace(old_call, new_call, 1)
app.write_text(text)
