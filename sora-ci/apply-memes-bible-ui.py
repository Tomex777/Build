from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(f"{label} marker mismatch: {text.count(old)}")
    return text.replace(old, new, 1)

# Sora root routing: memes get their own detail screen and Media can open full Bible.
app = Path("sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt")
text = app.read_text()
text = replace_once(
    text,
    "import com.night.sora.model.ExtensionMediaSelection\n",
    "import com.night.sora.model.ContentType\nimport com.night.sora.model.ExtensionMediaSelection\n",
    "SoraApp ContentType import",
)
text = replace_once(
    text,
    "    data class MediaDetails(val selection: ExtensionMediaSelection) : AppScreen\n",
    "    data class MediaDetails(val selection: ExtensionMediaSelection) : AppScreen\n    data class MemeDetails(val selection: ExtensionMediaSelection) : AppScreen\n",
    "SoraApp MemeDetails route",
)
text = replace_once(
    text,
    '''    fun openMedia(selection: ExtensionMediaSelection) {
        repository.recordActivity(selection, "opened")
        push(AppScreen.MediaDetails(selection))
    }
''',
    '''    fun openMedia(selection: ExtensionMediaSelection) {
        repository.recordActivity(selection, "opened")
        if (selection.type == ContentType.MEME) push(AppScreen.MemeDetails(selection))
        else push(AppScreen.MediaDetails(selection))
    }
''',
    "SoraApp openMedia",
)
text = replace_once(
    text,
    '''                    onOpenExtensions = { push(AppScreen.Extensions) }, onOpenDetails = ::openMedia,
                    onResumeProgress = { entry ->''',
    '''                    onOpenExtensions = { push(AppScreen.Extensions) }, onOpenDetails = ::openMedia,
                    onOpenBible = { push(AppScreen.Bible) },
                    onResumeProgress = { entry ->''',
    "SoraApp Media Bible callback",
)
text = replace_once(
    text,
    '''            is AppScreen.MediaDetails -> MediaDetailScreen(
                selection = current.selection, extensions = extensions, manager = extensionManager,
''',
    '''            is AppScreen.MemeDetails -> MemeDetailScreen(
                selection = current.selection,
                extensions = extensions,
                manager = extensionManager,
                isSaved = repository::isSaved,
                onToggleSaved = repository::toggleSaved,
                onBack = ::pop,
            )
            is AppScreen.MediaDetails -> MediaDetailScreen(
                selection = current.selection, extensions = extensions, manager = extensionManager,
''',
    "SoraApp meme detail dispatch",
)
app.write_text(text)

# Media: Bible destination opens the real reader; meme feed removes dead actions.
media = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt")
text = media.read_text()
text = replace_once(
    text,
    '''    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,''',
    '''    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onOpenBible: () -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,''',
    "MediaScreen Bible callback signature",
)
text = replace_once(
    text,
    '            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize())',
    '            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize(), onOpen = onOpenBible)',
    "MediaScreen Bible destination",
)
old_actions = '''                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) { Text("♡ Save", fontSize = 12.sp); Text("↗ Share", fontSize = 12.sp); Text("Less like this", fontSize = 12.sp) }
'''
new_actions = '''                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Open post", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                    }
'''
text = replace_once(text, old_actions, new_actions, "MemeSurface dead actions")
media.write_text(text)

# Bible embedded Media entry: make it an actual launch surface.
bible = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/BibleScreen.kt")
text = bible.read_text()
old = '''@Composable
fun BibleHubContent(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.MenuBook, null, tint = SoraAccent, modifier = Modifier.size(44.dp))
        Text("Bible reader", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
        Text("Open Bible from Home for chapters, versions, bookmarks, and reading position.", color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
'''
new = '''@Composable
fun BibleHubContent(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.MenuBook, null, tint = SoraAccent, modifier = Modifier.size(44.dp))
        Text("Bible reader", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
        Text("Choose a book and translation, keep your place, and bookmark verses.", color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        Button(onClick = onOpen, modifier = Modifier.padding(top = 18.dp), shape = RoundedCornerShape(10.dp)) {
            Text("Open Bible")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
        }
    }
}
'''
text = replace_once(text, old, new, "BibleHubContent")
bible.write_text(text)

# Home: replace the hardcoded verse with real local Bible state only.
home = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/HomeScreen.kt")
text = home.read_text()
text = replace_once(
    text,
    "import com.night.sora.data.CachedMediaRecord\n",
    "import com.night.sora.data.BibleRepository\nimport com.night.sora.data.CachedMediaRecord\n",
    "Home BibleRepository import",
)
text = replace_once(
    text,
    '''    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
''',
    '''    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
    val bibleRepository = remember { BibleRepository(context.applicationContext) }
    val lastBibleReading = bibleRepository.lastReading()
    val bibleTranslation = bibleRepository.selectedTranslation()
''',
    "Home Bible state",
)
old_card = '''            item { HomeSectionHeader("Bible", "Featured passage", "Open", onSee = onOpenBible) }
            item {
                Surface(
                    color = Color(0xFF151513),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clickable(onClick = onOpenBible),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("John 1:5", color = SoraAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                        Text("“The light shines in the darkness, and the darkness has not overcome it.”", fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp))
                        Text("Open Bible to read in context", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
'''
new_card = '''            item {
                HomeSectionHeader(
                    "Bible",
                    if (lastBibleReading != null) "Continue your reading" else "Read in your chosen translation",
                    "Open",
                    onSee = onOpenBible,
                )
            }
            item {
                Surface(
                    color = Color(0xFF151513),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clickable(onClick = onOpenBible),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(SoraSurfaceHigh, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MenuBook, null, tint = SoraAccent)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text(
                                if (lastBibleReading != null) "${lastBibleReading.book} ${lastBibleReading.chapter}:${lastBibleReading.verse}" else "Open Bible",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (lastBibleReading != null) "Resume · ${bibleTranslation.shortLabel}" else "Choose a book · ${bibleTranslation.shortLabel}",
                                color = SoraMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = SoraMuted)
                    }
                }
            }
'''
text = replace_once(text, old_card, new_card, "Home hardcoded Bible card")
home.write_text(text)
