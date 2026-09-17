from pathlib import Path

MEDIA = Path('sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaScreen.kt')
SMOKE = Path('sora-ci/ui-smoke.sh')


def replace_once_or_verify(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one old match, found {count}')
    return text.replace(old, new, 1)


media = MEDIA.read_text()
media = replace_once_or_verify(
    media,
    '''    Box(\n        Modifier.fillMaxWidth().height(420.dp)\n            .background(Brush.verticalGradient(listOf(Color(0xFF262621), Color(0xFF171714), SoraBg)))\n    ) {''',
    '''    Box(\n        Modifier.fillMaxWidth().height(260.dp)\n            .background(Brush.verticalGradient(listOf(Color(0xFF262621), Color(0xFF171714), SoraBg)))\n    ) {''',
    'compact empty feature shell',
)
MEDIA.write_text(media)

smoke = SMOKE.read_text()
reachability = '''\njikan_host_reachable() {\n  curl -fsS --max-time 12 --retry 2 --retry-delay 1 --retry-all-errors \\\n    'https://api.jikan.moe/v4/top/anime?filter=airing&limit=1&sfw=true' >/dev/null 2>&1\n}\n'''
if 'jikan_host_reachable() {' not in smoke:
    marker = '\ndismiss_system_dialogs\n\n# Core must render and populate Anime/Manga with no external APK installed.\n'
    if marker not in smoke:
        raise SystemExit('smoke reachability insertion marker not found')
    smoke = smoke.replace(marker, reachability + marker, 1)

anime_old = '''if ! wait_for_cache 'jikan.anime' 35; then\n  echo 'Built-in Jikan did not populate the Anime cache.' >&2\n  log_ui_state 'missing jikan.anime cache'\n  shot 01-failure-anime-jikan\n  exit 1\nfi'''
anime_new = '''if ! wait_for_cache 'jikan.anime' 35; then\n  if jikan_host_reachable; then\n    echo 'Built-in Jikan did not populate the Anime cache while Jikan was reachable.' >&2\n    log_ui_state 'missing jikan.anime cache'\n    shot 01-failure-anime-jikan\n    exit 1\n  fi\n  echo 'Jikan is externally unavailable; keeping the UI smoke focused on Sora rendering and navigation.' >&2\nfi'''
smoke = replace_once_or_verify(smoke, anime_old, anime_new, 'anime outage handling')

manga_old = '''if ! wait_for_cache 'jikan.manga' 35; then\n  echo 'Built-in Jikan did not populate the Manga cache.' >&2\n  log_ui_state 'missing jikan.manga cache'\n  shot 02-failure-manga-jikan\n  exit 1\nfi'''
manga_new = '''if ! wait_for_cache 'jikan.manga' 35; then\n  if jikan_host_reachable; then\n    echo 'Built-in Jikan did not populate the Manga cache while Jikan was reachable.' >&2\n    log_ui_state 'missing jikan.manga cache'\n    shot 02-failure-manga-jikan\n    exit 1\n  fi\n  echo 'Jikan is externally unavailable; keeping the UI smoke focused on Sora rendering and navigation.' >&2\nfi'''
smoke = replace_once_or_verify(smoke, manga_old, manga_new, 'manga outage handling')
SMOKE.write_text(smoke)

print('Applied Sora runtime hardening: compact offline catalog hero + external-outage-aware Jikan smoke.')
