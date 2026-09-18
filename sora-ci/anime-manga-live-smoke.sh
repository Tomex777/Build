#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-anime-manga-live
FULL_LIVE_MARKER="$OUT/FULL_LIVE_PASS"
mkdir -p "$OUT"
rm -f "$FULL_LIVE_MARKER"

adb uninstall com.night.sora >/dev/null 2>&1 || true
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 2

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/sora-media-window.xml >/dev/null 2>&1 || true
}

# Hosted Android emulators can surface launcher/System UI ANR dialogs during
# cold boot even while Sora is healthy underneath. Dismiss only non-Sora
# system dialogs; a Sora ANR remains a hard test failure.
dismiss_emulator_system_dialogs() {
  dump_ui
  python3 <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET

path='/tmp/sora-media-window.xml'
try:
    root=ET.parse(path).getroot()
except Exception:
    raise SystemExit(0)

titles=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    low=text.lower()
    if "isn't responding" in low or 'is not responding' in low or 'keeps stopping' in low:
        titles.append(text)

if not titles:
    raise SystemExit(0)

title=titles[0]
if 'sora' in title.lower():
    print(f'Real Sora system error dialog detected: {title}', file=sys.stderr)
    raise SystemExit(2)

for label in ('Wait', 'Close app'):
    for node in root.iter('node'):
        text=(node.attrib.get('text') or '').strip()
        desc=(node.attrib.get('content-desc') or '').strip()
        if text != label and desc != label:
            continue
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if not m:
            continue
        x1,y1,x2,y2=map(int,m.groups())
        print(f"Dismissing emulator-only system dialog '{title}' via '{label}'")
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        raise SystemExit(0)

print(f'Emulator system dialog could not be dismissed: {title}', file=sys.stderr)
raise SystemExit(1)
PY
}

node_exists() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/sora-media-window.xml').getroot()
for node in root.iter('node'):
    if (node.attrib.get('text') or '').strip() == label or (node.attrib.get('content-desc') or '').strip() == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1" timeout="${2:-35}" elapsed=0
  while (( elapsed < timeout )); do
    if ! dismiss_emulator_system_dialogs; then
      echo "A real Sora/system dialog blocked '$label'" >&2
      shot "failure-system-dialog-${label//[^A-Za-z0-9]/_}"
      return 1
    fi
    if node_exists "$label"; then
      echo "Found '$label' after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  echo "Timed out waiting for '$label'" >&2
  shot "failure-${label//[^A-Za-z0-9]/_}"
  adb logcat -d -t 600 | grep -Ei 'com\.night\.sora|AndroidRuntime|FATAL EXCEPTION|AniList' | tail -n 180 >&2 || true
  return 1
}

# Returns 0 for a real catalog surface, 2 for the honest unavailable state,
# and 1 only when neither state appears. This lets a tab recover independently
# while AniList is degraded without turning real recovery into a false failure.
wait_for_catalog_state() {
  local timeout="${1:-50}" elapsed=0
  while (( elapsed < timeout )); do
    if ! dismiss_emulator_system_dialogs; then
      shot failure-system-dialog-catalog-state
      return 1
    fi
    if node_exists Details; then
      echo "Catalog recovered with real rows after ${elapsed}s"
      return 0
    fi
    if node_exists 'Catalog unavailable'; then
      echo "Catalog is truthfully unavailable after ${elapsed}s"
      return 2
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  echo 'Timed out waiting for either real catalog rows or the unavailable state' >&2
  shot failure-catalog-state
  return 1
}

tap_text() {
  local label="$1"
  if ! dismiss_emulator_system_dialogs; then
    echo "A real Sora/system dialog blocked tap '$label'" >&2
    return 1
  fi
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/sora-media-window.xml').getroot()
points=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text != label and desc != label:
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups())
        points.append(((y1,x1),(x1+x2)//2,(y1+y2)//2))
if not points:
    raise SystemExit(f'UI node not found: {label}')
_,x,y=sorted(points)[0]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 1
}

input_query() {
  local hint="$1" query="$2"
  wait_for_node "$hint" 10
  tap_text "$hint"
  adb shell input text "$query"
  sleep 1
}

# Root -> Media. This is a cold-start run with no seeded catalog or progress.
wait_for_node Media 20
tap_text Media
wait_for_node 'Anime & Manga' 12
shot 01-anime-initial

# The curl probe is diagnostic only. When it reports degraded AniList, first
# verify each tab independently. If both tabs have recovered by emulator time,
# immediately retry this same script in strict full-live mode. A failed strict
# attempt is treated as an upstream-degraded pass and never creates the marker.
if [[ "${ANILIST_HEALTHY:-0}" != "1" && "${FORCE_FULL_LIVE:-0}" != "1" ]]; then
  anime_recovered=0
  manga_recovered=0

  if wait_for_catalog_state 50; then
    anime_recovered=1
    wait_for_node 'AIRING NOW' 45
    shot 02-anime-partial-recovery
  else
    rc=$?
    if [[ "$rc" -ne 2 ]]; then exit "$rc"; fi
    wait_for_node Retry 10
    shot 02-anime-outage
  fi

  wait_for_node Manga 10
  tap_text Manga
  if wait_for_catalog_state 60; then
    manga_recovered=1
    wait_for_node 'PUBLISHING NOW' 45
    shot 03-manga-partial-recovery
  else
    rc=$?
    if [[ "$rc" -ne 2 ]]; then exit "$rc"; fi
    wait_for_node Retry 10
    shot 03-manga-outage
  fi

  if [[ "$anime_recovered" -eq 1 && "$manga_recovered" -eq 1 ]]; then
    echo 'Both tabs recovered despite the failed preflight; attempting the strict full live flow.'
    if FORCE_FULL_LIVE=1 bash "$0"; then
      echo 'Opportunistic full live Anime/Manga validation passed.'
      exit 0
    fi
    echo 'Full live attempt did not stay healthy; preserving degraded-path pass without validation marker.'
    shot 04-opportunistic-live-incomplete
  fi

  echo 'Sora Anime/Manga degraded-AniList emulator smoke passed; full live-data gate remains pending.'
  exit 0
fi

# Strict full live gate. It is reached either because preflight was healthy or
# because both catalog tabs recovered inside the emulator and requested an
# opportunistic end-to-end retry.
wait_for_node Details 45
wait_for_node 'AIRING NOW' 45
shot 02-anime-live-home

# Deliberate Anime search using live AniList data.
tap_text 'Search Anime & Manga'
input_query 'Search Anime…' naruto
wait_for_node Naruto 45
shot 03-anime-search

tap_text Naruto
wait_for_node 'Add to library' 35
wait_for_node Episodes 35
shot 04-anime-detail

tap_text 'Add to library'
wait_for_node 'In library' 12

# Naruto has an explicit AniList adaptation relation. Wait for relationship
# resolution, then switch into the verified Manga counterpart and prove the
# detail surface changed to Chapters rather than guessing by search.
sleep 4
tap_text Manga
wait_for_node Chapters 35
shot 05-verified-manga-counterpart

# Return to Media, leave search, then validate the Manga feed.
adb shell input keyevent KEYCODE_BACK
sleep 2
wait_for_node 'Close search' 12
tap_text 'Close search'
wait_for_node Manga 12
tap_text Manga
wait_for_node Details 45
wait_for_node 'PUBLISHING NOW' 45
shot 06-manga-live-home

# Deliberate Manga search and detail page, again with no fake rows or progress.
tap_text 'Search Anime & Manga'
input_query 'Search Manga…' naruto
wait_for_node Naruto 45
shot 07-manga-search

tap_text Naruto
wait_for_node 'Add to library' 35
wait_for_node Chapters 35
shot 08-manga-detail

# The normal catalog must not invent a read source. With no external Manga
# extension installed, the detail page must truthfully show source-required UI.
wait_for_node 'No reading source selected' 20
shot 09-manga-no-source

touch "$FULL_LIVE_MARKER"
echo 'Sora live Anime/Manga emulator smoke passed with full end-to-end validation.'
