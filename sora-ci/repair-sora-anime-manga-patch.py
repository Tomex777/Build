from pathlib import Path

path = Path('sora-ci/patch-sora-anime-manga-flow.py')
text = path.read_text()

# replace_span preserves its end marker, so replacement bodies must not repeat
# the @Composable annotation that belongs to the next function.
needle = r"}\n\n@Composable\n''',"
count = text.count(needle)
if count < 4:
    raise SystemExit(f'expected several repeated @Composable boundaries, found {count}')
text = text.replace(needle, r"}\n\n''',")

# Same rule for the MediaScreen root Column end marker.
needle = r"\n\n    Column(modifier.fillMaxSize()) {''',"
if text.count(needle) != 1:
    raise SystemExit('MediaScreen Column boundary mismatch')
text = text.replace(needle, r"\n\n''',", 1)

# Make the source-resolution reset replacement unique by including the nearby
# sourceSearchError line. The shorter sequence appears in more than one block.
old = r"""    '''        browserError = null\n        readerError = null\n        playbackError = null\n''',
    '''        browserError = null\n        readerError = null\n        playbackError = null\n        sourceResolutionMessage = null\n''',"""
new = r"""    '''        sourceSearchError = null\n        browserError = null\n        readerError = null\n        playbackError = null\n''',
    '''        sourceSearchError = null\n        browserError = null\n        readerError = null\n        playbackError = null\n        sourceResolutionMessage = null\n''',"""
if text.count(old) != 1:
    raise SystemExit('source-resolution reset patch marker mismatch')
text = text.replace(old, new, 1)

# JikanCatalogService and ExtensionManager use different indentation depths.
# Replace the brittle shared exact-indentation block with indentation-aware
# line insertion so both implementations serialize the same factual fields.
start = "for rel in ['catalog/JikanCatalogService.kt', 'extension/ExtensionManager.kt']:"
end = "\n\n\n# ---------------------------------------------------------------------------\n# Detail screen:"
a = text.find(start)
b = text.find(end, a)
if a < 0 or b < 0:
    raise SystemExit('Jikan detail serialization block not found')
block = r'''for rel in ['catalog/JikanCatalogService.kt', 'extension/ExtensionManager.kt']:
    source = read(rel)

    def insert_after_line(source_text, line_text, new_lines):
        lines = source_text.splitlines(keepends=True)
        matches = [index for index, line in enumerate(lines) if line.strip() == line_text]
        if len(matches) != 1:
            raise SystemExit(f'{rel}: expected one {line_text!r} line, found {len(matches)}')
        index = matches[0]
        indent = lines[index][:len(lines[index]) - len(lines[index].lstrip())]
        addition = ''.join(indent + value + '\n' for value in new_lines)
        lines.insert(index + 1, addition)
        return ''.join(lines)

    source = insert_after_line(source, 'put("title", details.title)', [
        'put("alternateTitle", details.alternateTitle)',
    ])
    source = insert_after_line(source, 'put("genres", JSONArray(details.genres))', [
        'put("year", details.year ?: JSONObject.NULL)',
        'put("season", details.season)',
        'put("episodes", details.episodes ?: JSONObject.NULL)',
        'put("chapters", details.chapters ?: JSONObject.NULL)',
        'put("volumes", details.volumes ?: JSONObject.NULL)',
    ])
    write(rel, source)'''
text = text[:a] + block + text[b:]

path.write_text(text)
print(f'Repaired Anime/Manga patch script ({count} duplicated boundaries removed).')
