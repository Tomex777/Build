from pathlib import Path

path = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
text = path.read_text()

old = '''        feedFailures > 0 -> "Some discovery sections could not refresh. Saved catalog data is shown where available."'''
new = '''        feedFailures > 0 && selected != null -> "Some discovery sections could not refresh. Saved catalog data is shown where available."'''

if text.count(old) != 1:
    raise SystemExit(f'expected one outage notice branch, found {text.count(old)}')

path.write_text(text.replace(old, new, 1))
print('Polished full-outage Anime/Manga state to avoid redundant saved-data notice.')
