from pathlib import Path

path = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
text = path.read_text()

notice_old = '''        feedFailures > 0 -> "Some discovery sections could not refresh. Saved catalog data is shown where available."'''
notice_new = '''        feedFailures > 0 && selected != null -> "Some discovery sections couldn't refresh. Showing the catalog data currently available."'''

if notice_old in text:
    text = text.replace(notice_old, notice_new, 1)
elif notice_new not in text:
    raise SystemExit('outage notice branch was not found')

error_old = '''                    .onFailure { error ->
                        primaryError = error.message ?: "Could not refresh the catalog."
                    }'''
error_new = '''                    .onFailure {
                        primaryError = "Jikan did not return live ${requestType.label} data. Please retry in a moment."
                    }'''

if error_old in text:
    text = text.replace(error_old, error_new, 1)
elif error_new not in text:
    raise SystemExit('Anime/Manga primary error branch was not found')

path.write_text(text)
print('Polished Anime/Manga outage messaging without exposing raw provider errors.')
