from pathlib import Path

path = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaDetailScreen.kt')
text = path.read_text()

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    text = text.replace(old, new, 1)

replace_once(
'''                    saved = isSaved(active),
                    hasConsumptionSource = consumption != null,
                    browserEnabled = webViewAvailable && !browserBusy,
''',
'''                    saved = isSaved(active),
                    browserEnabled = webViewAvailable && !browserBusy,
''',
'action row source state',
)
replace_once(
'''                    onLibrary = { onToggleSaved(active) },
                    onSource = { sourcePickerOpen = true },
                    onAdaptation = { counterpart?.let { active = it } },
''',
'''                    onLibrary = { onToggleSaved(active) },
                    onAdaptation = { counterpart?.let { active = it } },
''',
'action row source callback',
)
replace_once(
'''                        descending = descending,
                        onSort = { descending = !descending },
                        onSource = { sourcePickerOpen = true },
''',
'''                        descending = descending,
                        onSort = { descending = !descending },
''',
'items header source callback',
)
replace_once(
'''            actions = {
                if (sourceOptions.isNotEmpty()) {
                    IconButton(onClick = { sourcePickerOpen = true }) { Icon(Icons.Rounded.Source, "Change source") }
                }
                Box {
''',
'''            actions = {
                Box {
''',
'toolbar source button',
)
replace_once(
'''private fun DetailActionRow(
    saved: Boolean,
    hasConsumptionSource: Boolean,
    browserEnabled: Boolean,
''',
'''private fun DetailActionRow(
    saved: Boolean,
    browserEnabled: Boolean,
''',
'action signature source state',
)
replace_once(
'''    counterpart: ExtensionMediaSelection?,
    onLibrary: () -> Unit,
    onSource: () -> Unit,
    onAdaptation: () -> Unit,
''',
'''    counterpart: ExtensionMediaSelection?,
    onLibrary: () -> Unit,
    onAdaptation: () -> Unit,
''',
'action signature source callback',
)
replace_once(
'''        DetailActionButton(
            title = if (hasConsumptionSource) "Source" else "Choose source",
            icon = Icons.Rounded.Public,
            highlighted = hasConsumptionSource,
            onClick = onSource,
        )
''',
'',
'action source button',
)
replace_once(
'''private fun DetailItemsHeader(type: ContentType, count: Int, descending: Boolean, onSort: () -> Unit, onSource: () -> Unit) {
''',
'''private fun DetailItemsHeader(type: ContentType, count: Int, descending: Boolean, onSort: () -> Unit) {
''',
'items header signature',
)
replace_once(
'''        IconButton(onClick = onSource) { Icon(Icons.Rounded.Source, "Choose source") }
        IconButton(onClick = onSort) { Icon(if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, "Change sort order") }
''',
'''        IconButton(onClick = onSort) { Icon(if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, "Change sort order") }
''',
'items header source button',
)

path.write_text(text)
