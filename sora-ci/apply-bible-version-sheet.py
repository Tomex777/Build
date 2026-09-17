from pathlib import Path

path = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/BibleScreen.kt")
text = path.read_text()
old = '''    val visibleTranslations = remember(query, translations) {
        val needle = query.trim()
        if (needle.isBlank()) translations else translations.filter { translation ->
            translation.shortLabel.contains(needle, ignoreCase = true) ||
                translation.name.contains(needle, ignoreCase = true) ||
                translation.language.contains(needle, ignoreCase = true)
        }
    }
'''
new = '''    val visibleTranslations = remember(query, translations, selected.id) {
        val needle = query.trim()
        translations
            .filter { translation ->
                needle.isBlank() ||
                    translation.shortLabel.contains(needle, ignoreCase = true) ||
                    translation.name.contains(needle, ignoreCase = true) ||
                    translation.language.contains(needle, ignoreCase = true)
            }
            .sortedWith(
                compareBy<BibleTranslation>(
                    { if (it.id == selected.id) 0 else 1 },
                    { if (it.language.contains("English", ignoreCase = true)) 0 else 1 },
                    { it.name.lowercase() },
                )
            )
    }
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"version sorting marker mismatch: {count}")
path.write_text(text.replace(old, new, 1))
