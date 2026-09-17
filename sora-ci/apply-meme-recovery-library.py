from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label} marker mismatch: {count}")
    return text.replace(old, new, 1)

media = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt")
text = media.read_text()

old_when = '''        when {
            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize(), onOpen = onOpenBible)
            query.isNotBlank() -> SearchResultsSurface(rows, selectedType, ::selection, onOpenDetails, onPlayMusic)
            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(
                type = selectedType, rows = rows, popularRows = popularRows, upcomingRows = upcomingRows, topRows = topRows,
                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress,
            )
            destination == MediaDestination.MOVIES_TV -> MovieTvSurface(
                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
                onResume = onResumeProgress,
            )
            destination == MediaDestination.MUSIC -> MusicSurface(
                panel = musicLocal, rows = rows, libraryEntries = libraryEntries, rankedTaste = rankedTaste,
                selection = ::selection, onPlay = onPlayMusic, onOpen = onOpenDetails,
                onOpenExtensions = onOpenExtensions, onSelectPanel = { musicLocal = it },
            )
            destination == MediaDestination.MEMES -> MemeSurface(
                rows = rows,
                selection = ::selection,
                onOpen = onOpenDetails,
                loading = memeLoading,
                error = memeError,
                onOpenExtensions = onOpenExtensions,
            )
        }
'''
new_when = '''        when {
            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize(), onOpen = onOpenBible)
            destination == MediaDestination.MEMES -> MemeSurface(
                rows = rows,
                selection = ::selection,
                onOpen = onOpenDetails,
                loading = memeLoading,
                error = memeError,
                onRetry = { load(query) },
                onOpenExtensions = onOpenExtensions,
            )
            query.isNotBlank() -> SearchResultsSurface(rows, selectedType, ::selection, onOpenDetails, onPlayMusic)
            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(
                type = selectedType, rows = rows, popularRows = popularRows, upcomingRows = upcomingRows, topRows = topRows,
                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress,
            )
            destination == MediaDestination.MOVIES_TV -> MovieTvSurface(
                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
                onResume = onResumeProgress,
            )
            destination == MediaDestination.MUSIC -> MusicSurface(
                panel = musicLocal, rows = rows, libraryEntries = libraryEntries, rankedTaste = rankedTaste,
                selection = ::selection, onPlay = onPlayMusic, onOpen = onOpenDetails,
                onOpenExtensions = onOpenExtensions, onSelectPanel = { musicLocal = it },
            )
        }
'''
text = replace_once(text, old_when, new_when, "media destination routing")

text = replace_once(
    text,
    '''    loading: Boolean,
    error: String?,
    onOpenExtensions: () -> Unit,
) {
''',
    '''    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenExtensions: () -> Unit,
) {
''',
    "MemeSurface retry signature",
)

old_empty = '''    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                Icon(
                    if (error?.contains("installed", ignoreCase = true) == true) Icons.Rounded.ExtensionOff else Icons.Rounded.CloudOff,
                    null,
                    tint = SoraMuted,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    if (error?.contains("installed", ignoreCase = true) == true) "No meme source installed" else "Meme feed unavailable",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    error ?: "No posts are available right now.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                if (error?.contains("installed", ignoreCase = true) == true) {
                    TextButton(onClick = onOpenExtensions, modifier = Modifier.padding(top = 8.dp)) { Text("Manage extensions") }
                }
            }
        }
        return
    }
'''
new_empty = '''    if (rows.isEmpty()) {
        val missingSource = error?.contains("installed", ignoreCase = true) == true
        val noSearchResults = error?.contains("matched your search", ignoreCase = true) == true
        val emptyFeed = error?.contains("No image posts", ignoreCase = true) == true
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                Icon(
                    if (missingSource) Icons.Rounded.ExtensionOff else if (noSearchResults) Icons.Rounded.SearchOff else Icons.Rounded.CloudOff,
                    null,
                    tint = SoraMuted,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    when {
                        missingSource -> "No meme source installed"
                        noSearchResults -> "No meme results"
                        emptyFeed -> "Nothing new right now"
                        else -> "Meme feed unavailable"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    error ?: "No posts are available right now.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!missingSource) {
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Retry")
                        }
                    }
                    TextButton(onClick = onOpenExtensions) {
                        Icon(Icons.Rounded.Extension, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (missingSource) "Manage extensions" else "Sources")
                    }
                }
            }
        }
        return
    }
'''
text = replace_once(text, old_empty, new_empty, "MemeSurface recovery UI")
media.write_text(text)

library = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/LibraryScreen.kt")
text = library.read_text()
text = replace_once(
    text,
    '''private enum class LibraryType(val label: String) {
    ALL("All"), ANIME("Anime"), MANGA("Manga"), MOVIE("Movies"), SERIES("Series"), MUSIC("Music")
}
''',
    '''private enum class LibraryType(val label: String) {
    ALL("All"), ANIME("Anime"), MANGA("Manga"), MOVIE("Movies"), SERIES("Series"), MUSIC("Music"), MEMES("Memes")
}
''',
    "Library meme filter enum",
)
text = replace_once(
    text,
    '''                LibraryType.SERIES -> entry.contentType == ContentType.TV
                LibraryType.MUSIC -> entry.contentType == ContentType.MUSIC
''',
    '''                LibraryType.SERIES -> entry.contentType == ContentType.TV
                LibraryType.MUSIC -> entry.contentType == ContentType.MUSIC
                LibraryType.MEMES -> entry.contentType == ContentType.MEME
''',
    "Library meme filter match",
)
text = replace_once(
    text,
    '''private fun LibraryGridItem(entry: LibraryEntry, onClick: () -> Unit) {
    val isMusic = entry.contentType == ContentType.MUSIC
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (isMusic) 1f else 2f / 3f)
''',
    '''private fun LibraryGridItem(entry: LibraryEntry, onClick: () -> Unit) {
    val squareArtwork = entry.contentType == ContentType.MUSIC || entry.contentType == ContentType.MEME
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (squareArtwork) 1f else 2f / 3f)
''',
    "Library meme square artwork",
)
text = replace_once(
    text,
    '''                    when (entry.contentType) { ContentType.TV -> "SERIES"; ContentType.MUSIC -> if (entry.kind.contains("playlist", true)) "PLAYLIST" else "MUSIC"; else -> entry.kind.uppercase() },
''',
    '''                    when (entry.contentType) { ContentType.TV -> "SERIES"; ContentType.MUSIC -> if (entry.kind.contains("playlist", true)) "PLAYLIST" else "MUSIC"; ContentType.MEME -> "MEME"; else -> entry.kind.uppercase() },
''',
    "Library meme badge",
)
library.write_text(text)
