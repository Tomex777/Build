from pathlib import Path

manager = Path('sora-overlay/app/src/main/java/com/night/sora/extension/ExtensionManager.kt')
text = manager.read_text()
old_import = 'import com.night.sora.model.ContentType\n'
new_import = 'import com.night.sora.model.ContentType\nimport com.night.sora.model.ExtensionMediaSelection\n'
if text.count(old_import) != 1:
    raise SystemExit('ExtensionManager import marker mismatch')
text = text.replace(old_import, new_import, 1)

marker = '''    fun call(extension: InstalledExtension, method: String, payloadJson: String, callback: (Result<String>) -> Unit) {
        if (isBuiltInJikan(extension)) {
            callBuiltInJikan(method, payloadJson, callback)
        } else {
            ExtensionClient(context, extension.component).call(method, payloadJson, callback)
        }
    }
'''
addition = marker + '''
    fun findBuiltInJikanCounterpart(
        selection: ExtensionMediaSelection,
        callback: (Result<ExtensionMediaSelection?>) -> Unit,
    ): Boolean {
        val isJikanSelection = selection.extensionPackage == context.packageName &&
            selection.sourceId in setOf(JikanCatalogClient.ANIME_SOURCE, JikanCatalogClient.MANGA_SOURCE) &&
            (selection.type == ContentType.ANIME || selection.type == ContentType.MANGA)
        if (!isJikanSelection) return false

        jikanClient.counterpart(selection.type, selection.id) { result ->
            callback(
                result.map { item ->
                    item?.let {
                        ExtensionMediaSelection(
                            id = it.id,
                            sourceId = JikanCatalogClient.sourceFor(it.type),
                            extensionPackage = context.packageName,
                            type = it.type,
                            title = it.title,
                            subtitle = it.subtitle,
                            artworkUrl = it.artworkUrl,
                        )
                    }
                }
            )
        }
        return true
    }
'''
if text.count(marker) != 1:
    raise SystemExit('ExtensionManager call marker mismatch')
text = text.replace(marker, addition, 1)
manager.write_text(text)

detail = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaDetailScreen.kt')
text = detail.read_text()
start = '''private fun findCounterpart(active: ExtensionMediaSelection, extensions: List<InstalledExtension>, manager: ExtensionManager, callback: (ExtensionMediaSelection?) -> Unit) {
    val opposite = if (active.type == ContentType.ANIME) ContentType.MANGA else if (active.type == ContentType.MANGA) ContentType.ANIME else return callback(null)
'''
replacement = '''private fun findCounterpart(active: ExtensionMediaSelection, extensions: List<InstalledExtension>, manager: ExtensionManager, callback: (ExtensionMediaSelection?) -> Unit) {
    val opposite = if (active.type == ContentType.ANIME) ContentType.MANGA else if (active.type == ContentType.MANGA) ContentType.ANIME else return callback(null)

    if (manager.findBuiltInJikanCounterpart(active) { result -> callback(result.getOrNull()) }) return
'''
if text.count(start) != 1:
    raise SystemExit('findCounterpart start marker mismatch')
text = text.replace(start, replacement, 1)

guessed = '''                    (best ?: arr.optJSONObject(0))?.let {
                        ExtensionMediaSelection(it.optString("id"), source.id, ext.packageName, opposite, it.optString("title"), it.optString("subtitle"), detailArtwork(it))
                    }
'''
exact = '''                    best?.let {
                        ExtensionMediaSelection(it.optString("id"), source.id, ext.packageName, opposite, it.optString("title"), it.optString("subtitle"), detailArtwork(it))
                    }
'''
if text.count(guessed) != 1:
    raise SystemExit('counterpart guess marker mismatch')
text = text.replace(guessed, exact, 1)
detail.write_text(text)
