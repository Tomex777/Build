#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-anime-manga-live
mkdir -p "$OUT"

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
    if node_exists "$label"; then
      echo "Found '$label' after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  echo "Timed out waiting for '$label'" >&2
  shot "failure-${label//[^A-Za-z0-9]/_}"
  adb logcat -d -t 600 | grep -Ei 'com\.night\.sora|AndroidRuntime|FATAL EXCEPTION|Jikan' | tail -n 180 >&2 || true
  return 1
}

assert_absent() {
  local label="$1"
  if node_exists "$label"; then
    echo "Unexpected UI node present: $label" >&2
    shot "failure-unexpected-${label//[^A-Za-z0-9]/_}"
    return 1
  fi
}

tap_text() {
  local label="$1"
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
wait_for_node Media 12
tap_text Media
wait_for_node 'Anime & Manga' 12
shot 01-anime-initial

# When GitHub's runner cannot reach Jikan, validate the real outage UI instead
# of pretending the app failed because live catalog rows cannot exist. This is
# intentionally not considered a live-data validation by the workflow.
if [[ "${JIKAN_HEALTHY:-0}" != "1" ]]; then
  wait_for_node 'Catalog unavailable' 45
  wait_for_node Retry 10
  assert_absent Details
  shot 02-anime-outage

  wait_for_node Manga 10
  tap_text Manga
  wait_for_node 'Catalog unavailable' 45
  wait_for_node Retry 10
  assert_absent Details
  shot 03-manga-outage

  echo 'Sora Anime/Manga honest Jikan-outage emulator smoke passed; live-data gate remains pending.'
  exit 0
fi

# Live Jikan must populate the Anime surface. The Details CTA only exists once
# a real catalog row is present; skeletons do not expose fake controls.
wait_for_node Details 45
wait_for_node 'Airing now' 45
shot 02-anime-live-home

# Deliberate Anime search using live Jikan data.
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

# Naruto has an explicit Jikan adaptation relation. Wait for relationship
# resolution, then switch into the verified Manga counterpart and prove the
# detail surface changed to Chapters rather than guessing by search.
sleep 4
tap_text Manga
wait_for_node Chapters 35
shot 05-verified-manga-counterpart

# Return to Media, leave search, then validate the cold-start Manga feed.
adb shell input keyevent KEYCODE_BACK
sleep 2
wait_for_node 'Close search' 12
tap_text 'Close search'
wait_for_node Manga 12
tap_text Manga
wait_for_node Details 45
wait_for_node 'Publishing now' 45
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

echo 'Sora live Anime/Manga emulator smoke passed.'
