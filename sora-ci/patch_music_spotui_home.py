from pathlib import Path
p=Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
s=p.read_text()

def rep(old,new):
    global s
    if old not in s: raise SystemExit('missing block: '+old[:100])
    s=s.replace(old,new,1)

rep('private enum class MusicLocal(val label: String) { HOME("Home"), DISCOVER("Discover"), LIBRARY("Your Music") }',
    'private enum class MusicLocal(val label: String) { HOME("Home"), LIBRARY("Your Music") }')

rep('''    when (panel) {
        MusicLocal.HOME -> MusicHome(rows, rankedTaste, selection, playFromQueue, onOpen, onOpenExtensions, onSelectPanel)
        MusicLocal.DISCOVER -> MusicDiscover(rows, selection, playFromQueue)
        MusicLocal.LIBRARY -> MusicLibrary(libraryEntries, playFromQueue)
    }
''','''    when (panel) {
        MusicLocal.HOME -> MusicHome(rows, rankedTaste, selection, playFromQueue, onOpenExtensions, onSelectPanel)
        MusicLocal.LIBRARY -> MusicLibrary(libraryEntries, playFromQueue)
    }
''')

rep('''    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onPlay: (ExtensionMediaSelection) -> Unit, onOpen: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit, onSelectPanel: (MusicLocal) -> Unit,
''','''    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onPlay: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit, onSelectPanel: (MusicLocal) -> Unit,
''')

rep('''                        DropdownMenuItem(
                            text = { Text("Discover") },
                            leadingIcon = { Icon(Icons.Rounded.Explore, null) },
                            onClick = { optionsOpen = false; onSelectPanel(MusicLocal.DISCOVER) },
                        )
                        DropdownMenuItem(
''','''                        DropdownMenuItem(
''')

rep('''        if (rankedRows.isNotEmpty()) {
            item { MusicSectionTitle("Made for you", if (rankedTaste.isEmpty()) "Fresh picks from your music source" else "Ordered from your listening history", null) }
            item { MusicSquareRail(rankedRows.take(8), selection, onPlay) }
        }
''','''        if (rankedTaste.isEmpty() && rows.isNotEmpty()) {
            item { MusicSectionTitle("Popular right now", "Live picks from your selected music source", null) }
            item { MusicSquareRail(rows.take(8), selection, onPlay) }
        } else if (rankedRows.isNotEmpty()) {
            item { MusicSectionTitle("Made for you", "Ordered from your actual listening history", null) }
            item { MusicSquareRail(rankedRows.take(8), selection, onPlay) }
        }
''')

start=s.find('@Composable\nprivate fun MusicDiscover(')
end=s.find('@Composable\nprivate fun MusicLibrary(', start)
if start < 0 or end < 0: raise SystemExit('MusicDiscover block not found')
s=s[:start]+s[end:]

p.write_text(s)
