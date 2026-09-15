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
    "import com.night.sora.model.ExtensionMediaSelection\n",
    "import com.night.sora.model.ExtensionMediaSelection\nimport com.night.sora.model.ReaderPage\nimport com.night.sora.model.ReaderSession\n",
    "reader imports",
)

replace_once(
    "    isSaved: (ExtensionMediaSelection) -> Boolean,\n    onToggleSaved: (ExtensionMediaSelection) -> Unit,\n    onBack: () -> Unit,\n",
    "    isSaved: (ExtensionMediaSelection) -> Boolean,\n    onToggleSaved: (ExtensionMediaSelection) -> Unit,\n    onOpenReader: (ReaderSession) -> Unit,\n    onBack: () -> Unit,\n",
    "reader callback",
)

replace_once(
    "    var browserError by remember { mutableStateOf<String?>(null) }\n    var menuOpen by remember { mutableStateOf(false) }\n",
    "    var browserError by remember { mutableStateOf<String?>(null) }\n    var readerError by remember { mutableStateOf<String?>(null) }\n    var menuOpen by remember { mutableStateOf(false) }\n",
    "reader error state",
)

replace_once(
    "        sourceSearchError = null\n        browserError = null\n",
    "        sourceSearchError = null\n        browserError = null\n        readerError = null\n",
    "clear reader error on source change",
)

old_open_child = '''    fun openChild(row: DetailRow) {
        val target = consumption ?: active.takeIf { selectionCanConsume(it, extensions) } ?: return
        val ext = extensions.firstOrNull { it.packageName == target.extensionPackage } ?: return
        val method = when (active.type) {
            ContentType.ANIME, ContentType.TV -> ExtensionContract.Method.STREAMS
            ContentType.MANGA -> ExtensionContract.Method.PAGES
            else -> return
        }
        secondaryTitle = if (active.type == ContentType.MANGA) row.title else "${row.title} · Sources"
        secondaryRows = listOf(DetailRow("loading", "Loading…", ""))
        manager.call(
            ext,
            method,
            JSONObject().put("sourceId", target.sourceId).put("id", row.id).toString(),
        ) { result ->
            secondaryRows = result.getOrNull()?.let { parseSecondary(method, it) }.orEmpty()
        }
    }
'''

new_open_child = '''    fun openChild(row: DetailRow) {
        val target = consumption ?: active.takeIf { selectionCanConsume(it, extensions) } ?: return
        val ext = extensions.firstOrNull { it.packageName == target.extensionPackage } ?: return
        readerError = null

        if (active.type == ContentType.MANGA) {
            rowsLoading = true
            manager.call(
                ext,
                ExtensionContract.Method.PAGES,
                JSONObject().put("sourceId", target.sourceId).put("id", row.id).toString(),
            ) { result ->
                rowsLoading = false
                val pages = result.getOrNull()?.let(::parseReaderPages).orEmpty()
                if (pages.isNotEmpty()) {
                    onOpenReader(
                        ReaderSession(
                            title = active.title,
                            chapterTitle = row.title,
                            sourceName = consumptionSource?.name ?: ext.declaredName,
                            pages = pages,
                        )
                    )
                } else {
                    readerError = result.exceptionOrNull()?.message
                        ?: "${ext.declaredName} returned no readable pages for ${row.title}."
                }
            }
            return
        }

        val method = when (active.type) {
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
    }
'''
replace_once(old_open_child, new_open_child, "openChild reader handoff")

replace_once(
    "        browserError = null\n\n        activeDisplayExtension?.let { ext ->\n",
    "        browserError = null\n        readerError = null\n\n        activeDisplayExtension?.let { ext ->\n",
    "reset reader error on title refresh",
)

replace_once(
    '''            browserError?.let { message ->
                item(key = "browserError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            item(key = "description") {
''',
    '''            browserError?.let { message ->
                item(key = "browserError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            readerError?.let { message ->
                item(key = "readerError") {
                    Text(message, color = SoraDanger, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            item(key = "description") {
''',
    "reader error UI",
)

anchor = '''private fun parseSecondary(method: String, raw: String): List<DetailRow> = runCatching {
'''
if anchor not in text:
    raise SystemExit("parseSecondary anchor not found")

reader_parser = '''private fun parseReaderPages(raw: String): List<ReaderPage> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            val page = when (value) {
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
                    ReaderPage(url = url, headers = headers)
                }
                is String -> value.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { ReaderPage(it) }
                else -> null
            }
            if (page != null) add(page)
        }
    }
}.getOrDefault(emptyList())

'''
text = text.replace(anchor, reader_parser + anchor, 1)

path.write_text(text)
print("Reader wiring applied to", path)
