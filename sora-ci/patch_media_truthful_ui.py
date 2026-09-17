from pathlib import Path

path = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
text = path.read_text()


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    text = text.replace(old, new, 1)

# Saved-library rails are not playback progress. Do not fabricate a 58% bar.
replace_once(
    '''                Box(Modifier.fillMaxWidth().height(107.dp).clip(RoundedCornerShape(7.dp)).background(SoraSurface)) {\n                    if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)\n                    LinearProgressIndicator(progress = { .58f }, modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter), color = SoraAccent, trackColor = Color(0xFF555248))\n                }''',
    '''                Box(Modifier.fillMaxWidth().height(107.dp).clip(RoundedCornerShape(7.dp)).background(SoraSurface)) {\n                    if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)\n                }''',
    'fake library progress',
)

# Upcoming catalog items do not currently carry release dates, so avoid made-up
# month/day values. Keep a compact catalog-order marker instead.
replace_once(
    '''                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) {\n                        Text("SEP", color = SoraFaint, fontSize = 8.sp, fontWeight = FontWeight.Black)\n                        Text("${14 + index * 2}", color = SoraFaint, fontSize = 19.sp, fontWeight = FontWeight.Black)\n                    }''',
    '''                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) {\n                        Text("SOON", color = SoraFaint, fontSize = 8.sp, fontWeight = FontWeight.Black)\n                        Text("${index + 1}", color = SoraFaint, fontSize = 19.sp, fontWeight = FontWeight.Black)\n                    }''',
    'upcoming skeleton dates',
)
replace_once(
    '''                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("SEP", color = SoraMuted, fontSize = 8.sp, fontWeight = FontWeight.Black); Text("${14 + index * 2}", fontSize = 19.sp, fontWeight = FontWeight.Black) }''',
    '''                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("SOON", color = SoraMuted, fontSize = 8.sp, fontWeight = FontWeight.Black); Text("${index + 1}", fontSize = 19.sp, fontWeight = FontWeight.Black) }''',
    'upcoming live dates',
)

# Genre chips have no genre-filter/navigation contract yet. Remove the dead
# affordance until there is a real genre feed instead of presenting fake buttons.
for block, label in [
    ('''        item {\n            MediaSectionTitle("Browse by mood", "Explore a genre from Search")\n            GenreRail(if (type == ContentType.ANIME) listOf("Dark", "Funny", "Psychological", "Adventure", "Romance", "Slice of life") else listOf("Drama", "Psychological", "Action", "Romance", "Mystery", "Slice of life"))\n        }\n''', 'Anime/Manga dead genre rail'),
    ('''        item { MediaSectionTitle("Browse by mood", "Explore a genre from Search"); GenreRail(listOf("Thriller", "Drama", "Comedy", "Sci-fi", "Crime", "Documentary")) }\n''', 'Movies/TV dead genre rail'),
]:
    replace_once(block, '', label)

path.write_text(text)
print('Removed fake media progress/dates and dead genre affordances.')
