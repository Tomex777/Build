from pathlib import Path

path = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaDetailScreen.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    "    var childRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }\n",
    "    var childRows by remember { mutableStateOf<List<DetailRow>>(emptyList()) }\n    var movieStreams by remember { mutableStateOf<List<PlaybackStream>>(emptyList()) }\n",
    "movie stream state",
)

old_loader = '''    fun loadConsumptionRows(target: ExtensionMediaSelection?) {
        childRows = emptyList()
        if (target == null) {
            rowsLoading = false
            return
        }
        val ext = extensions.firstOrNull { it.packageName == target.extensionPackage }
        if (ext == null) {
            rowsLoading = false
            return
        }
        val method = childMethod(target.type)
        rowsLoading = true
        manager.call(
            ext,
            method,
            JSONObject().put("sourceId", target.sourceId).put("id", target.id).toString(),
        ) { result ->
            childRows = result.getOrNull()?.let { parseRows(target.type, it) }.orEmpty()
            rowsLoading = false
        }
    }
'''

new_loader = '''    fun loadConsumptionRows(target: ExtensionMediaSelection?) {
        childRows = emptyList()
        movieStreams = emptyList()
        if (target == null) {
            rowsLoading = false
            return
        }
        val ext = extensions.firstOrNull { it.packageName == target.extensionPackage }
        if (ext == null) {
            rowsLoading = false
            return
        }
        val method = childMethod(target.type)
        rowsLoading = true
        manager.call(
            ext,
            method,
            JSONObject().put("sourceId", target.sourceId).put("id", target.id).toString(),
        ) { result ->
            if (target.type == ContentType.MOVIE) {
                movieStreams = result.getOrNull()?.let(::parsePlaybackStreams).orEmpty()
                childRows = movieStreams.mapIndexed { index, stream ->
                    DetailRow(
                        id = "movie-stream-$index",
                        title = stream.label.ifBlank { "Stream ${index + 1}" },
                        subtitle = stream.mimeType.orEmpty(),
                    )
                }
                if (movieStreams.isEmpty() && result.isFailure) {
                    playbackError = result.exceptionOrNull()?.message ?: "This source could not load movie streams."
                }
            } else {
                childRows = result.getOrNull()?.let { parseRows(target.type, it) }.orEmpty()
            }
            rowsLoading = false
        }
    }
'''
replace_once(old_loader, new_loader, "movie stream loader")

anchor = '''    fun openChild(row: DetailRow) {
'''
if anchor not in text:
    raise SystemExit("openChild anchor not found")

movie_function = '''    fun openMovie(row: DetailRow? = null) {
        if (active.type != ContentType.MOVIE || movieStreams.isEmpty()) return
        val requested = row?.id?.removePrefix("movie-stream-")?.toIntOrNull() ?: 0
        val index = requested.coerceIn(0, movieStreams.lastIndex)
        onOpenPlayer(
            PlaybackSession(
                title = active.title,
                episodeTitle = active.title,
                sourceName = consumptionSource?.name ?: consumptionExtension?.declaredName ?: "Movie source",
                streams = movieStreams,
                initialStream = index,
            )
        )
    }

'''
text = text.replace(anchor, movie_function + anchor, 1)

old_click = '''                            onClick = if (active.type == ContentType.ANIME || active.type == ContentType.TV || active.type == ContentType.MANGA) ({ openChild(row) }) else null,
'''
new_click = '''                            onClick = when (active.type) {
                                ContentType.ANIME, ContentType.TV, ContentType.MANGA -> ({ openChild(row) })
                                ContentType.MOVIE -> ({ openMovie(row) })
                                else -> null
                            },
'''
replace_once(old_click, new_click, "movie row playback")

old_fab = '''        if (secondaryRows.isEmpty() && visibleRows.isNotEmpty() && (active.type == ContentType.ANIME || active.type == ContentType.MANGA || active.type == ContentType.TV)) {
            ExtendedFloatingActionButton(
                onClick = { visibleRows.firstOrNull()?.let(::openChild) },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
                containerColor = SoraAccent,
                contentColor = SoraAccentInk,
                icon = { Icon(if (active.type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null) },
                text = { Text(if (active.type == ContentType.MANGA) "Start" else "Play") },
            )
        }
'''
new_fab = '''        if (secondaryRows.isEmpty() && visibleRows.isNotEmpty() && (active.type == ContentType.ANIME || active.type == ContentType.MANGA || active.type == ContentType.TV || active.type == ContentType.MOVIE)) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (active.type == ContentType.MOVIE) openMovie(visibleRows.firstOrNull())
                    else visibleRows.firstOrNull()?.let(::openChild)
                },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
                containerColor = SoraAccent,
                contentColor = SoraAccentInk,
                icon = { Icon(if (active.type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null) },
                text = { Text(if (active.type == ContentType.MANGA) "Start" else "Play") },
            )
        }
'''
replace_once(old_fab, new_fab, "movie play fab")

path.write_text(text)
print("Movie playback wiring applied to", path)
