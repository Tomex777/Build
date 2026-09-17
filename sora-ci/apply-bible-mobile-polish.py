from pathlib import Path

p = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/BibleScreen.kt")
text = p.read_text()

def once(old: str, new: str, label: str):
    global text
    if text.count(old) != 1:
        raise SystemExit(f"{label} marker mismatch: {text.count(old)}")
    text = text.replace(old, new, 1)

once(
    "import androidx.compose.foundation.background\n",
    "import androidx.activity.compose.BackHandler\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.rememberScrollState\n",
    "imports",
)

once(
'''    fun changeTranslation(next: BibleTranslation) {
        selectedTranslation = next
        repository.setSelectedTranslation(next)
    }

    when (val current = route) {''',
'''    fun changeTranslation(next: BibleTranslation) {
        selectedTranslation = next
        repository.setSelectedTranslation(next)
    }

    BackHandler(enabled = route != BibleRoute.Hub) {
        route = when (val current = route) {
            BibleRoute.Search, BibleRoute.Bookmarks -> BibleRoute.Hub
            is BibleRoute.Chapters -> BibleRoute.Hub
            is BibleRoute.Reader -> BibleRoute.Chapters(current.book)
            BibleRoute.Hub -> BibleRoute.Hub
        }
    }

    when (val current = route) {''',
    "internal back navigation",
)

once(
'''            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BibleFilterChip("All", testament == null) { testament = null }
                    BibleFilterChip("Old Testament", testament == Testament.OLD) { testament = Testament.OLD }
                    BibleFilterChip("New Testament", testament == Testament.NEW) { testament = Testament.NEW }
                }
            }
''',
'''            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BibleFilterChip("All", testament == null) { testament = null }
                    BibleFilterChip("Old Testament", testament == Testament.OLD) { testament = Testament.OLD }
                    BibleFilterChip("New Testament", testament == Testament.NEW) { testament = Testament.NEW }
                }
            }
''',
    "testament chip scrolling",
)

p.write_text(text)
