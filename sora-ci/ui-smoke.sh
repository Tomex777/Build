#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-ui
mkdir -p "$OUT"

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
  # Fresh GitHub emulators occasionally show a Pixel Launcher/system ANR over Sora.
  # It is unrelated to Sora; choose Wait/OK and bring Sora back to the foreground.
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

# Core must render and populate Anime/Manga with no external APK installed.
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

# Built-in Jikan remains registered inside Core itself for contract parity/diagnostics.
adb shell dumpsys package com.night.sora | grep -q 'JikanCatalogService'

# Verify the remaining external provider can arrive later without changing shell ownership.
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

tap_text Library
shot 07-library

tap_text Games
shot 08-games

tap_text More
shot 09-more

tap_text 'Open Sora AI'
shot 10-ai-quick-sheet

tap_text 'Full chat'
shot 11-ai-full

adb shell dumpsys activity activities | grep -q 'com.night.sora'
