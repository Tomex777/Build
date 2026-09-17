from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label} marker mismatch: {count}")
    return text.replace(old, new, 1)

repo = Path("sora-overlay/app/src/main/java/com/night/sora/data/BibleRepository.kt")
text = repo.read_text()

marker = '''    fun isBookmarked(
        verse: BibleVerse,
        translation: BibleTranslation,
        bookmarks: List<BibleBookmark>,
    ): Boolean = bookmarks.any {
        it.key == bookmarkKey(translation.id, verse.book, verse.chapter, verse.number)
    }

    private fun persistBookmarks(entries: List<BibleBookmark>) {
'''
insert = '''    fun isBookmarked(
        verse: BibleVerse,
        translation: BibleTranslation,
        bookmarks: List<BibleBookmark>,
    ): Boolean = bookmarks.any {
        it.key == bookmarkKey(translation.id, verse.book, verse.chapter, verse.number)
    }

    fun annotations(): List<BibleAnnotation> {
        val raw = prefs.getString(KEY_ANNOTATIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        BibleAnnotation(
                            key = item.getString("key"),
                            book = item.getString("book"),
                            chapter = item.getInt("chapter"),
                            verse = item.getInt("verse"),
                            text = item.optString("text"),
                            translationId = item.optString("translationId", "web"),
                            translationName = item.optString("translationName", "World English Bible"),
                            highlighted = item.optBoolean("highlighted", false),
                            note = item.optString("note"),
                            updatedAt = item.optLong("updatedAt", 0L),
                        )
                    )
                }
            }.filter { it.highlighted || it.note.isNotBlank() }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun annotationFor(
        verse: BibleVerse,
        translation: BibleTranslation,
        annotations: List<BibleAnnotation>,
    ): BibleAnnotation? = annotations.firstOrNull {
        it.key == bookmarkKey(translation.id, verse.book, verse.chapter, verse.number)
    }

    fun toggleHighlight(verse: BibleVerse, translation: BibleTranslation): List<BibleAnnotation> {
        val current = annotations().toMutableList()
        val key = bookmarkKey(translation.id, verse.book, verse.chapter, verse.number)
        val index = current.indexOfFirst { it.key == key }
        val existing = current.getOrNull(index)
        val next = (existing ?: BibleAnnotation(
            key = key,
            book = verse.book,
            chapter = verse.chapter,
            verse = verse.number,
            text = verse.text,
            translationId = translation.id,
            translationName = translation.name,
        )).copy(
            text = verse.text,
            highlighted = !(existing?.highlighted ?: false),
            updatedAt = System.currentTimeMillis(),
        )
        if (!next.highlighted && next.note.isBlank()) {
            if (index >= 0) current.removeAt(index)
        } else if (index >= 0) {
            current[index] = next
        } else {
            current.add(0, next)
        }
        persistAnnotations(current)
        return current.sortedByDescending { it.updatedAt }
    }

    fun saveNote(verse: BibleVerse, translation: BibleTranslation, note: String): List<BibleAnnotation> {
        val clean = note.trim()
        val current = annotations().toMutableList()
        val key = bookmarkKey(translation.id, verse.book, verse.chapter, verse.number)
        val index = current.indexOfFirst { it.key == key }
        val existing = current.getOrNull(index)
        val next = (existing ?: BibleAnnotation(
            key = key,
            book = verse.book,
            chapter = verse.chapter,
            verse = verse.number,
            text = verse.text,
            translationId = translation.id,
            translationName = translation.name,
        )).copy(
            text = verse.text,
            note = clean,
            updatedAt = System.currentTimeMillis(),
        )
        if (!next.highlighted && next.note.isBlank()) {
            if (index >= 0) current.removeAt(index)
        } else if (index >= 0) {
            current[index] = next
        } else {
            current.add(0, next)
        }
        persistAnnotations(current)
        return current.sortedByDescending { it.updatedAt }
    }

    private fun persistAnnotations(entries: List<BibleAnnotation>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("book", entry.book)
                    .put("chapter", entry.chapter)
                    .put("verse", entry.verse)
                    .put("text", entry.text)
                    .put("translationId", entry.translationId)
                    .put("translationName", entry.translationName)
                    .put("highlighted", entry.highlighted)
                    .put("note", entry.note)
                    .put("updatedAt", entry.updatedAt)
            )
        }
        prefs.edit().putString(KEY_ANNOTATIONS, array.toString()).apply()
    }

    private fun persistBookmarks(entries: List<BibleBookmark>) {
'''
text = replace_once(text, marker, insert, "Bible annotation repository methods")

text = replace_once(
    text,
    '''        private const val KEY_LAST_READING = "last_reading"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_TRANSLATION = "translation"
''',
    '''        private const val KEY_LAST_READING = "last_reading"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_ANNOTATIONS = "annotations"
        private const val KEY_TRANSLATION = "translation"
''',
    "Bible annotation key",
)

text = replace_once(
    text,
    '''data class BibleBookmark(
    val key: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val translationId: String,
    val translationName: String,
    val savedAt: Long,
)

val SoraBibleBooks: List<BibleBook> = listOf(
''',
    '''data class BibleBookmark(
    val key: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val translationId: String,
    val translationName: String,
    val savedAt: Long,
)

data class BibleAnnotation(
    val key: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val translationId: String,
    val translationName: String,
    val highlighted: Boolean = false,
    val note: String = "",
    val updatedAt: Long = 0L,
)

val SoraBibleBooks: List<BibleBook> = listOf(
''',
    "BibleAnnotation model",
)
repo.write_text(text)

screen = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/BibleScreen.kt")
text = screen.read_text()

text = replace_once(
    text,
    '''    var selectedTranslation by remember { mutableStateOf(repository.selectedTranslation()) }
    var bookmarks by remember { mutableStateOf(repository.bookmarks()) }
    val lastReading = remember(route) { repository.lastReading() }
''',
    '''    var selectedTranslation by remember { mutableStateOf(repository.selectedTranslation()) }
    var bookmarks by remember { mutableStateOf(repository.bookmarks()) }
    var annotations by remember { mutableStateOf(repository.annotations()) }
    val lastReading = remember(route) { repository.lastReading() }
''',
    "BibleScreen annotation state",
)

text = replace_once(
    text,
    '''            selectedTranslation = selectedTranslation,
            translations = translations,
            bookmarks = bookmarks,
            onTranslation = ::changeTranslation,
            onBookmarksChanged = { bookmarks = it },
            onBack = { route = BibleRoute.Chapters(current.book) },
''',
    '''            selectedTranslation = selectedTranslation,
            translations = translations,
            bookmarks = bookmarks,
            annotations = annotations,
            onTranslation = ::changeTranslation,
            onBookmarksChanged = { bookmarks = it },
            onAnnotationsChanged = { annotations = it },
            onBack = { route = BibleRoute.Chapters(current.book) },
''',
    "BibleReader annotation args",
)

text = replace_once(
    text,
    '''    selectedTranslation: BibleTranslation,
    translations: List<BibleTranslation>,
    bookmarks: List<BibleBookmark>,
    onTranslation: (BibleTranslation) -> Unit,
    onBookmarksChanged: (List<BibleBookmark>) -> Unit,
    onBack: () -> Unit,
''',
    '''    selectedTranslation: BibleTranslation,
    translations: List<BibleTranslation>,
    bookmarks: List<BibleBookmark>,
    annotations: List<BibleAnnotation>,
    onTranslation: (BibleTranslation) -> Unit,
    onBookmarksChanged: (List<BibleBookmark>) -> Unit,
    onAnnotationsChanged: (List<BibleAnnotation>) -> Unit,
    onBack: () -> Unit,
''',
    "BibleReader annotation signature",
)

text = replace_once(
    text,
    '''    var error by remember(book, chapter, selectedTranslation.id) { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
''',
    '''    var error by remember(book, chapter, selectedTranslation.id) { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var selectedVerse by remember(book, chapter, selectedTranslation.id) { mutableStateOf<BibleVerse?>(null) }
    var noteVerse by remember(book, chapter, selectedTranslation.id) { mutableStateOf<BibleVerse?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
''',
    "BibleReader verse action state",
)

old_rows = '''                    items(current.verses, key = { "${it.chapter}:${it.number}" }) { verse ->
                        val saved = repository.isBookmarked(verse, selectedTranslation, bookmarks)
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                verse.number.toString(),
                                color = SoraAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(28.dp).padding(top = 5.dp),
                            )
                            Text(
                                verse.text,
                                fontSize = 17.sp,
                                lineHeight = 28.sp,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onBookmarksChanged(repository.toggleBookmark(verse, selectedTranslation)) },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                    if (saved) "Remove bookmark" else "Bookmark verse",
                                    tint = if (saved) SoraAccent else SoraMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
'''
new_rows = '''                    items(current.verses, key = { "${it.chapter}:${it.number}" }) { verse ->
                        val saved = repository.isBookmarked(verse, selectedTranslation, bookmarks)
                        val annotation = repository.annotationFor(verse, selectedTranslation, annotations)
                        Surface(
                            color = if (annotation?.highlighted == true) SoraAccent.copy(alpha = .10f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedVerse = verse }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    verse.number.toString(),
                                    color = SoraAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(28.dp).padding(top = 5.dp),
                                )
                                Text(
                                    verse.text,
                                    fontSize = 17.sp,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!annotation?.note.isNullOrBlank()) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        "Verse has a note",
                                        tint = SoraAccent,
                                        modifier = Modifier.padding(top = 10.dp, end = 3.dp).size(15.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onBookmarksChanged(repository.toggleBookmark(verse, selectedTranslation)) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        if (saved) "Remove bookmark" else "Bookmark verse",
                                        tint = if (saved) SoraAccent else SoraMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
'''
text = replace_once(text, old_rows, new_rows, "BibleReader annotated verse rows")

end_marker = '''        }
    }
}

@Composable
private fun BibleSearch(
'''
end_insert = '''        }
    }

    selectedVerse?.let { verse ->
        val annotation = repository.annotationFor(verse, selectedTranslation, annotations)
        val saved = repository.isBookmarked(verse, selectedTranslation, bookmarks)
        ModalBottomSheet(
            onDismissRequest = { selectedVerse = null },
            containerColor = SoraSurface,
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                Text("${verse.book} ${verse.chapter}:${verse.number}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(verse.text, color = SoraMuted, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAnnotationsChanged(repository.toggleHighlight(verse, selectedTranslation)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (annotation?.highlighted == true) "Unhighlight" else "Highlight") }
                    Button(
                        onClick = {
                            noteDraft = annotation?.note.orEmpty()
                            noteVerse = verse
                            selectedVerse = null
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (annotation?.note.isNullOrBlank()) "Add note" else "Edit note") }
                }
                TextButton(
                    onClick = { onBookmarksChanged(repository.toggleBookmark(verse, selectedTranslation)) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saved) "Remove bookmark" else "Bookmark verse")
                }
            }
        }
    }

    noteVerse?.let { verse ->
        val existing = repository.annotationFor(verse, selectedTranslation, annotations)
        AlertDialog(
            onDismissRequest = { noteVerse = null },
            title = { Text("${verse.book} ${verse.chapter}:${verse.number}") },
            text = {
                Column {
                    Text(verse.text, color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("Note") },
                        minLines = 3,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAnnotationsChanged(repository.saveNote(verse, selectedTranslation, noteDraft))
                    noteVerse = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (!existing?.note.isNullOrBlank()) {
                        TextButton(onClick = {
                            onAnnotationsChanged(repository.saveNote(verse, selectedTranslation, ""))
                            noteVerse = null
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { noteVerse = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun BibleSearch(
'''
text = replace_once(text, end_marker, end_insert, "BibleReader verse action sheet")

screen.write_text(text)
