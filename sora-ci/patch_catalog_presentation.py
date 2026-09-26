from pathlib import Path
import re

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')
MEDIA = ROOT / 'ui/screens/MediaScreen.kt'
HOME = ROOT / 'ui/screens/HomeScreen.kt'
DETAIL = ROOT / 'ui/screens/MediaDetailScreen.kt'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    return text.replace(old, new, 1)


def replace_regex(text: str, pattern: str, replacement: str, label: str) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 regex match, found {count}')
    return out


# Media ---------------------------------------------------------------------
media = MEDIA.read_text()
media = replace_once(
    media,
    'import com.night.sora.extension.InstalledExtension\n',
    'import com.night.sora.extension.InstalledExtension\nimport com.night.sora.extension.isCatalogProvider\n',
    'media policy import',
)
media = media.replace(
    '.filter { source -> ext.error == null && key in source.contentTypes }',
    '.filter { source -> ext.isCatalogProvider() && key in source.contentTypes }',
)
needle = '''    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {\n        if (selected != null) item {'''
replacement = '''    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {\n        if (selected == null) item { EmptyFeatureShell(type) }\n        if (selected != null) item {'''
count = media.count(needle)
if count != 2:
    raise SystemExit(f'feature shell insertion: expected 2 matches, found {count}')
media = media.replace(needle, replacement)

feature_shell = r'''@Composable
private fun EmptyFeatureShell(type: ContentType) {
    val kicker = when (type) {
        ContentType.ANIME -> "FEATURED ANIME"
        ContentType.MANGA -> "FEATURED MANGA"
        ContentType.MOVIE -> "FEATURED MOVIE"
        ContentType.TV -> "FEATURED SERIES"
        ContentType.MUSIC -> "FEATURED MUSIC"
        ContentType.MEME -> "FEATURED"
    }
    Box(
        Modifier.fillMaxWidth().height(420.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF262621), Color(0xFF171714), SoraBg)))
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(kicker, color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Box(Modifier.padding(top = 10.dp).width(236.dp).height(28.dp).background(SoraSurfaceHigh, RoundedCornerShape(6.dp)))
            Box(Modifier.padding(top = 9.dp).width(292.dp).height(10.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))
            Box(Modifier.padding(top = 6.dp).width(220.dp).height(10.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null, tint = SoraMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (type == ContentType.MANGA) "Read" else "Continue", color = SoraMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(color = SoraSurfaceHigh, shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = SoraMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Library", color = SoraMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

'''
media = replace_once(
    media,
    '@Composable\nprivate fun StreamFeature(',
    feature_shell + '@Composable\nprivate fun StreamFeature(',
    'feature shell helper',
)

portrait = r'''@Composable
private fun PortraitRail(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rows.isEmpty()) {
            items(6) { index ->
                Column(Modifier.width(116.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, RoundedCornerShape(7.dp)))
                    Box(Modifier.padding(top = 7.dp).width(82.dp).height(8.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                    Box(Modifier.padding(top = 5.dp).width(56.dp).height(6.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                }
            }
        } else {
            items(rows.take(12), key = { "p-${type.name}-${it.id}" }) { card ->
                Column(Modifier.width(116.dp).clickable { onOpen(selection(card, type)) }) {
                    Poster(card.artworkUrl, card.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), 7)
                    Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

'''
media = replace_regex(
    media,
    r'@Composable\nprivate fun PortraitRail\(.*?\n\}\n\n(?=@Composable\nprivate fun TopTenRail)',
    portrait,
    'portrait rail',
)

topten = r'''@Composable
private fun TopTenRail(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rows.isEmpty()) {
            items(10) { index ->
                Row(Modifier.height(178.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${index + 1}", color = Color(0xFF4E4C47), fontSize = 70.sp, lineHeight = 70.sp, fontWeight = FontWeight.Black, letterSpacing = (-5).sp)
                    Box(Modifier.width(96.dp).fillMaxHeight().background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, RoundedCornerShape(5.dp)))
                }
            }
        } else {
            items(rows.take(10).withIndex().toList(), key = { "top-${type.name}-${it.value.id}" }) { ranked ->
                Row(Modifier.height(178.dp).clickable { onOpen(selection(ranked.value, type)) }, verticalAlignment = Alignment.Bottom) {
                    Text("${ranked.index + 1}", color = Color(0xFF6F6C64), fontSize = 70.sp, lineHeight = 70.sp, fontWeight = FontWeight.Black, letterSpacing = (-5).sp)
                    Poster(ranked.value.artworkUrl, ranked.value.title, Modifier.width(96.dp).fillMaxHeight(), 5)
                }
            }
        }
    }
}

'''
media = replace_regex(
    media,
    r'@Composable\nprivate fun TopTenRail\(.*?\n\}\n\n(?=@Composable\nprivate fun ContinueLandscapeRail)',
    topten,
    'top ten rail',
)

newhot = r'''@Composable
private fun NewHotStack(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rows.isEmpty()) {
            repeat(3) { index ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SEP", color = SoraFaint, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("${14 + index * 2}", color = SoraFaint, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.size(width = 54.dp, height = 74.dp).background(SoraSurfaceHigh, RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Box(Modifier.width(64.dp).height(7.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth(.62f).height(10.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth(.42f).height(7.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                    }
                }
            }
        } else {
            rows.forEachIndexed { index, card ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(selection(card, type)) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("SEP", color = SoraMuted, fontSize = 8.sp, fontWeight = FontWeight.Black); Text("${14 + index * 2}", fontSize = 19.sp, fontWeight = FontWeight.Black) }
                    Poster(card.artworkUrl, card.title, Modifier.size(width = 54.dp, height = 74.dp), 6)
                    Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(if (index == 0) "NEW NOW" else "COMING SOON", color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(card.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1) }
                }
            }
        }
    }
}

'''
media = replace_regex(
    media,
    r'@Composable\nprivate fun NewHotStack\(.*?\n\}\n\n(?=@Composable\nprivate fun GenreRail)',
    newhot,
    'new hot stack',
)

music_quick = r'''@Composable
private fun MusicQuickGrid(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rows.isEmpty()) {
            repeat(3) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) { columnIndex ->
                        Row(Modifier.weight(1f).height(58.dp).background(SoraSurfaceHigh, RoundedCornerShape(7.dp)), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(58.dp).background(if ((rowIndex + columnIndex) % 2 == 0) SoraSurface else Color(0xFF242420), RoundedCornerShape(7.dp)))
                            Box(Modifier.padding(horizontal = 9.dp).weight(1f).height(8.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        } else {
            rows.chunked(2).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { card ->
                        Row(Modifier.weight(1f).height(58.dp).background(SoraSurfaceHigh, RoundedCornerShape(7.dp)).clickable { onPlay(selection(card, ContentType.MUSIC)) }, verticalAlignment = Alignment.CenterVertically) {
                            Poster(card.artworkUrl, card.title, Modifier.size(58.dp), 7)
                            Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp).weight(1f))
                        }
                    }
                    if (chunk.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

'''
media = replace_regex(
    media,
    r'@Composable\nprivate fun MusicQuickGrid\(.*?\n\}\n\n(?=@Composable\nprivate fun MusicSectionTitle)',
    music_quick,
    'music quick grid',
)

music_square = r'''@Composable
private fun MusicSquareRail(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.isEmpty()) {
            items(6) { index ->
                Column(Modifier.width(146.dp)) {
                    Box(Modifier.size(146.dp).background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, RoundedCornerShape(6.dp)))
                    Box(Modifier.padding(top = 7.dp).width(90.dp).height(8.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                    Box(Modifier.padding(top = 5.dp).width(58.dp).height(6.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                }
            }
        } else {
            items(rows.take(10), key = { "sq-${it.id}" }) { card ->
                Column(Modifier.width(146.dp).clickable { onPlay(selection(card, ContentType.MUSIC)) }) {
                    Poster(card.artworkUrl, card.title, Modifier.size(146.dp), 6)
                    Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 7.dp))
                    Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

'''
media = replace_regex(
    media,
    r'@Composable\nprivate fun MusicSquareRail\(.*?\n\n(?=@Composable\nprivate fun ArtistRail)',
    music_square,
    'music square rail',
)

artist = r'''@Composable
private fun ArtistRail(rows: List<BrowseCard>) {
    val artists = rows.mapNotNull { it.subtitle.substringBefore(" · ").takeIf(String::isNotBlank) }.distinct().take(8)
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (artists.isEmpty()) {
            items(6) { index ->
                Column(Modifier.width(94.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(86.dp).background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, CircleShape))
                    Box(Modifier.padding(top = 7.dp).width(54.dp).height(7.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                }
            }
        } else {
            items(artists) { artist ->
                Column(Modifier.width(94.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(86.dp).background(SoraSurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Text(artist.take(1), fontSize = 28.sp, fontWeight = FontWeight.Black) }
                    Text(artist, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

'''
media = replace_regex(
    media,
    r'@Composable\nprivate fun ArtistRail\(.*?\n\n(?=@Composable\nprivate fun MusicTrackList)',
    artist,
    'artist rail',
)

tracklist = r'''@Composable
private fun MusicTrackList(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit, numbered: Boolean = false) {
    Column {
        if (rows.isEmpty()) {
            repeat(5) { index ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (numbered) Text("${index + 1}", color = SoraFaint, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Box(Modifier.size(48.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Box(Modifier.fillMaxWidth(.54f).height(8.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth(.34f).height(6.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                    }
                }
            }
        } else {
            rows.forEachIndexed { index, card ->
                Row(Modifier.fillMaxWidth().clickable { onPlay(selection(card, ContentType.MUSIC)) }.padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (numbered) Text("${index + 1}", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Poster(card.artworkUrl, card.title, Modifier.size(48.dp), 5)
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(card.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1) }
                    Icon(Icons.Rounded.MoreVert, null, tint = SoraMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

'''
media = replace_regex(
    media,
    r'@Composable\nprivate fun MusicTrackList\(.*?\n\n(?=@Composable\nprivate fun MusicShortcut)',
    tracklist,
    'music track list',
)
MEDIA.write_text(media)

# Home ----------------------------------------------------------------------
home = HOME.read_text()
home = replace_once(
    home,
    'import com.night.sora.extension.InstalledExtension\n',
    'import com.night.sora.extension.InstalledExtension\nimport com.night.sora.extension.isCatalogProvider\n',
    'home policy import',
)
home = home.replace(
    '.filter { source -> ext.error == null && key in source.contentTypes }',
    '.filter { source -> ext.isCatalogProvider() && key in source.contentTypes }',
)
HOME.write_text(home)

# Detail --------------------------------------------------------------------
detail = DETAIL.read_text()
detail = replace_once(
    detail,
    'import com.night.sora.extension.InstalledExtension\n',
    'import com.night.sora.extension.InstalledExtension\nimport com.night.sora.extension.isCatalogProvider\n',
    'detail policy import',
)
detail = detail.replace(
    '.filter { source -> ext.error == null && key in source.contentTypes }',
    '.filter { source -> ext.isCatalogProvider() && key in source.contentTypes }',
)
DETAIL.write_text(detail)

print('Patched catalog provider policy and cold-state presentation.')
