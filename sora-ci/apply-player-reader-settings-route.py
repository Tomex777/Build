from pathlib import Path

more = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MoreScreen.kt')
text = more.read_text()
old = '''    onStatistics: () -> Unit,
    onDataStorage: () -> Unit,
) {'''
new = '''    onStatistics: () -> Unit,
    onDataStorage: () -> Unit,
    onPlayerReader: () -> Unit,
) {'''
if text.count(old) != 1:
    raise SystemExit('MoreScreen signature marker mismatch')
text = text.replace(old, new, 1)
old = '        item { MoreRow("Player & reader", "Playback, subtitles and reading behavior", Icons.Rounded.Tune) }\n'
new = '        item { MoreRow("Player & reader", "Default watch and read sources", Icons.Rounded.Tune, onClick = onPlayerReader) }\n'
if text.count(old) != 1:
    raise SystemExit('Player & reader row marker mismatch')
text = text.replace(old, new, 1)
more.write_text(text)

app = Path('sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt')
text = app.read_text()
old = '''    data object DataStorage : AppScreen
    data class ExtensionDetail(val extension: InstalledExtension) : AppScreen
'''
new = '''    data object DataStorage : AppScreen
    data object PlayerReaderSettings : AppScreen
    data class ExtensionDetail(val extension: InstalledExtension) : AppScreen
'''
if text.count(old) != 1:
    raise SystemExit('AppScreen marker mismatch')
text = text.replace(old, new, 1)
old = '''                    onStatistics = { push(AppScreen.Statistics) },
                    onDataStorage = { push(AppScreen.DataStorage) },
                )'''
new = '''                    onStatistics = { push(AppScreen.Statistics) },
                    onDataStorage = { push(AppScreen.DataStorage) },
                    onPlayerReader = { push(AppScreen.PlayerReaderSettings) },
                )'''
if text.count(old) != 1:
    raise SystemExit('MoreScreen route marker mismatch')
text = text.replace(old, new, 1)
old = '''            AppScreen.DataStorage -> DataStorageScreen(
                downloads = repository.downloads,
                onClearCatalogCache = mediaCatalogCache::clear,
                onBack = ::pop,
            )
            is AppScreen.ExtensionDetail -> ExtensionDetailScreen(current.extension, onBack = ::pop)
'''
new = '''            AppScreen.DataStorage -> DataStorageScreen(
                downloads = repository.downloads,
                onClearCatalogCache = mediaCatalogCache::clear,
                onBack = ::pop,
            )
            AppScreen.PlayerReaderSettings -> PlayerReaderSettingsScreen(
                extensions = extensions,
                onBack = ::pop,
            )
            is AppScreen.ExtensionDetail -> ExtensionDetailScreen(current.extension, onBack = ::pop)
'''
if text.count(old) != 1:
    raise SystemExit('Settings screen render marker mismatch')
text = text.replace(old, new, 1)
app.write_text(text)
