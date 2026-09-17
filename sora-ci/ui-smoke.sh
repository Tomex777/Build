#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-ui
mkdir -p "$OUT"

cleanup_smoke_settings() {
  adb shell settings put global always_finish_activities 0 >/dev/null 2>&1 || true
}
trap cleanup_smoke_settings EXIT

adb uninstall com.night.sora.ext.live >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.demo >/dev/null 2>&1 || true
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity
sleep 4

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

log_ui_state() {
  local reason="$1"
  echo "--- Sora UI diagnostics: $reason ---" >&2
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|com.night.sora|Application Not Responding' | tail -n 30 >&2 || true
  adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | tail -n 10 >&2 || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/window.xml >&2 2>/dev/null || true
  echo "--- end Sora UI diagnostics ---" >&2
}

log_jikan_state() {
  local token="$1"
  echo "--- Jikan diagnostics: $token ---" >&2
  echo "Sora catalog cache:" >&2
  adb shell run-as com.night.sora cat shared_prefs/sora_media_catalog_v1.xml >&2 2>/dev/null || true
  echo "Host reachability to Jikan:" >&2
  curl -fsS --max-time 12 'https://api.jikan.moe/v4/top/anime?filter=airing&limit=1&sfw=true' 2>&1 | head -c 700 >&2 || true
  echo >&2
  echo "Recent Sora/Jikan logcat:" >&2
  adb logcat -d -t 500 2>/dev/null | grep -Ei 'com\.night\.sora|Jikan|AndroidRuntime|FATAL EXCEPTION' | tail -n 120 >&2 || true
  echo "--- end Jikan diagnostics ---" >&2
}

ensure_sora_foreground() {
  if ! adb shell dumpsys activity activities | grep -q 'mResumedActivity.*com.night.sora'; then
    adb shell am start -W -n com.night.sora/.MainActivity >/dev/null 2>&1 || true
    sleep 2
  fi
}

tap_text_once() {
  local label="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try:
    root=ET.parse('/tmp/window.xml').getroot()
except Exception:
    raise SystemExit(2)
exact=[]
partial=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    bounds=node.attrib.get('bounds','')
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    point=((x1+x2)//2,(y1+y2)//2)
    if text == label or desc == label:
        exact.append(point)
    elif label in text or label in desc:
        partial.append(point)
points=exact or partial
if not points:
    raise SystemExit(1)
x,y=points[0]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
}

dismiss_system_dialogs() {
  if tap_text_once 'Wait' >/dev/null 2>&1; then
    sleep 2
  fi
  if tap_text_once 'OK' >/dev/null 2>&1; then
    sleep 1
  fi
  ensure_sora_foreground
}

tap_root_tab_fallback() {
  local label="$1"
  local index
  case "$label" in
    Home) index=0 ;;
    Media) index=1 ;;
    Library) index=2 ;;
    Games) index=3 ;;
    More) index=4 ;;
    *) return 1 ;;
  esac
  ensure_sora_foreground
  local size width height x y
  size=$(adb shell wm size | sed -n 's/.*Physical size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)
  read -r width height <<<"$size"
  if [[ -z "${width:-}" || -z "${height:-}" ]]; then
    return 1
  fi
  x=$(( width * (2 * index + 1) / 10 ))
  y=$(( height * 94 / 100 ))
  echo "Accessibility node '$label' unavailable; tapping root-tab fallback at $x,$y" >&2
  adb shell input tap "$x" "$y"
  sleep 2
}

tap_text() {
  local label="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    dismiss_system_dialogs
    if tap_text_once "$label"; then
      sleep 2
      return 0
    fi
    sleep 1
  done
  if tap_root_tab_fallback "$label"; then
    return 0
  fi
  echo "Could not find UI node: $label" >&2
  log_ui_state "missing node $label"
  shot "failure-${label//[^A-Za-z0-9]/_}"
  return 1
}

node_exists() {
  local label="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try:
    root=ET.parse('/tmp/window.xml').getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter('node'):
    if (node.attrib.get('text') or '').strip() == label or (node.attrib.get('content-desc') or '').strip() == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1"
  local timeout="${2:-20}"
  local elapsed=0
  while (( elapsed < timeout )); do
    dismiss_system_dialogs
    if node_exists "$label"; then
      echo "UI node '$label' available after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  echo "Timed out waiting for UI node: $label" >&2
  log_ui_state "missing node $label"
  shot "failure-wait-${label//[^A-Za-z0-9]/_}"
  return 1
}

tap_first_music_tile() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/window.xml').getroot()
choices=[]
for node in root.iter('node'):
    if node.attrib.get('clickable') != 'true':
        continue
    bounds=node.attrib.get('bounds','')
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    width=x2-x1; height=y2-y1
    # Music quick-grid cards on the Pixel 7 smoke viewport. Avoid tabs/FAB/nav.
    if 850 <= y1 <= 1500 and 350 <= width <= 600 and 90 <= height <= 240:
        choices.append((y1,x1,x2,y2))
if not choices:
    raise SystemExit('No playable Music tile found in expected viewport')
y1,x1,x2,y2=sorted(choices)[0]
x=(x1+x2)//2; y=(y1+y2)//2
print(f'Tapping first Music tile at {x},{y}')
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}

cache_contains() {
  local token="$1"
  adb shell run-as com.night.sora cat shared_prefs/sora_media_catalog_v1.xml 2>/dev/null | grep -q "$token"
}

wait_for_cache() {
  local token="$1"
  local timeout="${2:-35}"
  local elapsed=0
  while (( elapsed < timeout )); do
    dismiss_system_dialogs
    if cache_contains "$token"; then
      echo "Catalog cache contains $token after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  echo "Timed out after ${timeout}s waiting for catalog cache token: $token" >&2
  log_jikan_state "$token"
  return 1
}

jikan_host_reachable() {
  curl -fsS --max-time 12 --retry 2 --retry-delay 1 --retry-all-errors \
    'https://api.jikan.moe/v4/top/anime?filter=airing&limit=1&sfw=true' >/dev/null 2>&1
}

dismiss_system_dialogs

shot 00-core-only-home
tap_text Media
if ! wait_for_cache 'jikan.anime' 35; then
  if jikan_host_reachable; then
    echo 'Built-in Jikan did not populate the Anime cache while Jikan was reachable.' >&2
    log_ui_state 'missing jikan.anime cache'
    shot 01-failure-anime-jikan
    exit 1
  fi
  echo 'Jikan is externally unavailable; keeping the UI smoke focused on Sora rendering and navigation.' >&2
fi
dismiss_system_dialogs
shot 01-core-only-anime-jikan

tap_text Manga
if ! wait_for_cache 'jikan.manga' 35; then
  if jikan_host_reachable; then
    echo 'Built-in Jikan did not populate the Manga cache while Jikan was reachable.' >&2
    log_ui_state 'missing jikan.manga cache'
    shot 02-failure-manga-jikan
    exit 1
  fi
  echo 'Jikan is externally unavailable; keeping the UI smoke focused on Sora rendering and navigation.' >&2
fi
dismiss_system_dialogs
shot 02-core-only-manga-jikan

for file in "$OUT/01-core-only-anime-jikan.xml" "$OUT/02-core-only-manga-jikan.xml"; do
  if grep -Eqi 'No .* source installed|Manage extensions|source unavailable' "$file"; then
    echo "Extension leaked into user-facing media UI: $file" >&2
    exit 1
  fi
done

adb shell dumpsys package com.night.sora | grep -q 'JikanCatalogService'

adb shell input keyevent KEYCODE_HOME
sleep 1
adb install -r "$SORA_ROOT/live-extension/build/outputs/apk/debug/live-extension-debug.apk"
adb shell am start -W -n com.night.sora/.MainActivity
sleep 5
dismiss_system_dialogs

tap_text Anime
shot 03-anime-after-external-provider

tap_text 'Anime & Manga'
shot 04-media-switch-sheet

tap_text 'Movies & TV'
sleep 5
shot 05-movies-tv

tap_text 'Movies & TV'
tap_text Music
sleep 5
shot 06-music-home

tap_first_music_tile
wait_for_node 'Mini player' 20
wait_for_node Pause 20
shot 06a-music-playing

# Background playback must survive leaving the activity and expose a Media3 session.
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 3
dismiss_system_dialogs
wait_for_node Play 10
shot 06aa-music-background-paused
tap_text Play
wait_for_node Pause 15
shot 06ab-music-background-resumed

# Force Android to destroy the activity when it leaves the foreground. The
# process-wide Media3 service/player must survive and the recreated activity
# must reconnect to the same now-playing state.
adb shell settings put global always_finish_activities 1
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 4
dismiss_system_dialogs
wait_for_node Pause 15
shot 06ac-music-after-activity-recreation
adb shell settings put global always_finish_activities 0

tap_text 'Mini player'
wait_for_node Pause 10
shot 06b-music-full-player

tap_text Pause
wait_for_node Play 8
tap_text Play
wait_for_node Pause 15
tap_text Next
wait_for_node Pause 20
sleep 2
tap_text Previous
wait_for_node Pause 20
shot 06c-music-transport
adb shell input keyevent KEYCODE_BACK
sleep 2

tap_text Library
shot 07-library

tap_text Games
shot 08-games

tap_text More
shot 09-more

tap_text Downloads
shot 10-downloads
adb shell input keyevent KEYCODE_BACK
sleep 2

tap_text Statistics
shot 11-statistics
adb shell input keyevent KEYCODE_BACK
sleep 2

tap_text 'Data & storage'
shot 12-data-storage
adb shell input keyevent KEYCODE_BACK
sleep 2

tap_text 'Open Sora AI'
shot 13-ai-quick-sheet

tap_text 'Full chat'
shot 14-ai-full

adb shell dumpsys activity activities | grep -q 'com.night.sora'
