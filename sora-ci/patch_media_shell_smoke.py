from pathlib import Path

media_path = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
media = media_path.read_text()

def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)

media = once(
    media,
'''            selectedType = selectedType,
            onSwitch = { switchOpen = true },
''',
'''            selectedType = selectedType,
            searchEnabled = destination != MediaDestination.BIBLE,
            onSwitch = { switchOpen = true },
''',
    'top bar search enabled call',
)
media = once(
    media,
'''    selectedType: ContentType,
    onSwitch: () -> Unit,
''',
'''    selectedType: ContentType,
    searchEnabled: Boolean,
    onSwitch: () -> Unit,
''',
    'top bar signature',
)
media = once(media, '        if (searchOpen) {\n', '        if (searchOpen && searchEnabled) {\n', 'search branch guard')
media = once(
    media,
'''            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, "Search $title") }
''',
'''            Spacer(Modifier.weight(1f))
            if (searchEnabled) {
                IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, "Search $title") }
            }
''',
    'search action guard',
)
media_path.write_text(media)

smoke_path = Path('sora-ci/ui-smoke.sh')
smoke = smoke_path.read_text()
start = smoke.index('tap_first_music_tile\n')
end_marker = 'adb shell input keyevent KEYCODE_BACK\nsleep 2\n\n'
end = smoke.index(end_marker, start) + len(end_marker)
replacement = '''# Music playback/transport has a dedicated deterministic Pixel 7 smoke.\n# The broad UI smoke only verifies that the Music surface renders and navigation continues.\nwait_for_node 'Music options' 15\nshot 06a-music-surface\n\n'''
smoke = smoke[:start] + replacement + smoke[end:]
smoke_path.write_text(smoke)
