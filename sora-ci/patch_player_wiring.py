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
    "import com.night.sora.model.ExtensionMediaSelection\nimport com.night.sora.model.ReaderPage\n",
    "import com.night.sora.model.ExtensionMediaSelection\nimport com.night.sora.model.PlaybackSession\nimport com.night.sora.model.PlaybackStream\nimport com.night.sora.model.ReaderPage\n",
    "playback imports",
)

replace_once(
    "    onToggleSaved: (ExtensionMediaSelection) -> Unit,\n    onOpenReader: (ReaderSession) -> Unit,\n    onBack: () -> Unit,\n",
    "    onToggleSaved: (ExtensionMediaSelection) -> Unit,\n    onOpenReader: (ReaderSession) -> Unit,\n    onOpenPlayer: (PlaybackSession) -> Unit,\n    onBack: () -> Unit,\n",
    "player callback",
)

replace_once(
    "    var readerError by remember { mutableStateOf<String?>(null) }\n    var menuOpen by remember { mutableStateOf(false) }\n",
    "    var readerError by remember { mutableStateOf<String?>(null) }\n    var playbackError by remember { mutableStateOf<String?>(null) }\n    var menuOpen by remember { mutableStateOf(false) }\n",
    "playback error state",
)

replace_once(
    "        browserError = null\n        readerError = null\n        searchSourceSelection",
    "        browserError = null\n        readerError = null\n        playbackError = null\n        searchSourceSelection",
    "clear playback error on source change",
)

old_stream_branch = '''        val method = when (active.type) {
            ContentType.ANIME, ContentType.TV -> ExtensionContract.Method.STREAMS
            else -> return
        }
        secondaryTitle = "${row.title} · Sources"
        secondaryRows = listOf(DetailRow("loading", "Loading…", ""))
        manager.call(
            ext,
            method,
            JSONObject().put("sourceId", target.sourceId).put("id", row.id).toString(),
        ) { result ->
            secondaryRows = result.getOrNull()?.let { parseSecondary(method, it) }.orEmpty()
        }
'''

new_stream_branch = '''        if (active.type == ContentType.ANIME || active.type == ContentType.TV) {
            rowsLoading = true
            manager.call(
                ext,
                ExtensionContract.Method.STREAMS,
                JSONObject().put("sourceId", target.sourceId).put("id", row.id).toString(),
            ) { result ->
                rowsLoading = false
                val streams = result.getOrNull()?.let(::parsePlaybackStreams).orEmpty()
                if (streams.isNotEmpty()) {
                    onOpenPlayer(
                        PlaybackSession(
                            title = active.title,
                            episodeTitle = row.title,
                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            streams = streams,
                        )
                    )
                } else {
                    playbackError = result.exceptionOrNull()?.message
                        ?: "${ext.declaredName} returned no playable streams for ${row.title}."
                }
            }
        }
'''
replace_once(old_stream_branch, new_stream_branch, "anime/tv stream player handoff")

replace_once(
    "        browserError = null\n        readerError = null\n\n        activeDisplayExtension?.let { ext ->\n",
    "        browserError = null\n        readerError = null\n        playbackError = null\n\n        activeDisplayExtension?.let { ext ->\n",
    "reset playback error on title refresh",
)

replace_once(
    '''            readerError?.let { message ->
                item(key = "readerError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            item(key = "description") {
''',
    '''            readerError?.let { message ->
                item(key = "readerError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            playbackError?.let { message ->
                item(key = "playbackError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            item(key = "description") {
''',
    "playback error UI",
)

anchor = '''private fun parseReaderPages(raw: String): List<ReaderPage> = runCatching {
'''
if anchor not in text:
    raise SystemExit("reader parser anchor not found")

player_parser = '''private fun parsePlaybackStreams(raw: String): List<PlaybackStream> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            val stream = when (value) {
                is JSONObject -> {
                    val url = value.optString("url").trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                    val headers = buildMap {
                        value.optJSONObject("headers")?.let { obj ->
                            obj.keys().forEach { key ->
                                obj.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                            }
                        }
                        value.optString("referer").takeIf(String::isNotBlank)?.let { putIfAbsent("Referer", it) }
                        value.optString("userAgent").takeIf(String::isNotBlank)?.let { putIfAbsent("User-Agent", it) }
                    }
                    val label = value.optString("label").ifBlank {
                        value.optString("quality").ifBlank { value.optString("name").ifBlank { "Stream ${i + 1}" } }
                    }
                    val mime = value.optString("mimeType").ifBlank { value.optString("contentType") }.takeIf(String::isNotBlank)
                    PlaybackStream(label = label, url = url, headers = headers, mimeType = mime)
                }
                is String -> value.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?.let { PlaybackStream(label = "Stream ${i + 1}", url = it) }
                else -> null
            }
            if (stream != null) add(stream)
        }
    }.distinctBy { it.url }
}.getOrDefault(emptyList())

'''
text = text.replace(anchor, player_parser + anchor, 1)

path.write_text(text)
print("Player wiring applied to", path)
