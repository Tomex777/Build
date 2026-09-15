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

tap_text() {
  local label="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
    if python3 - "$label" <<'PY'
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
    then
      sleep 2
      return 0
    fi
    sleep 1
  done
  echo "Could not find UI node: $label" >&2
  shot "failure-${label//[^A-Za-z0-9]/_}"
  return 1
}

# Core must render and populate Anime/Manga with no external APK installed.
shot 00-core-only-home
tap_text Media
sleep 8
shot 01-core-only-anime-jikan

tap_text Manga
sleep 8
shot 02-core-only-manga-jikan

for file in "$OUT/01-core-only-anime-jikan.xml" "$OUT/02-core-only-manga-jikan.xml"; do
  if grep -Eqi 'No .* source installed|Manage extensions|source unavailable' "$file"; then
    echo "Extension leaked into user-facing media UI: $file" >&2
    exit 1
  fi
done

# Built-in Jikan must be registered inside Core itself.
adb shell dumpsys package com.night.sora | grep -q 'JikanCatalogService'

# Verify the remaining external provider can arrive later without changing shell ownership.
adb shell input keyevent KEYCODE_HOME
sleep 1
adb install -r "$SORA_ROOT/live-extension/build/outputs/apk/debug/live-extension-debug.apk"
adb shell am start -W -n com.night.sora/.MainActivity
sleep 5

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
