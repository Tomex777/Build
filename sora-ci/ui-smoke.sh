#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-ui
mkdir -p "$OUT"

adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$SORA_ROOT/live-extension/build/outputs/apk/debug/live-extension-debug.apk"
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity
sleep 5

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
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/failure-$attempt.xml" >/dev/null 2>&1 || true
  shot "failure-${label//[^A-Za-z0-9]/_}"
  return 1
}

shot 01-home-top
adb shell input swipe 540 1750 540 600 650
sleep 2
shot 02-home-scrolled

tap_text Media
shot 03-media-anime

tap_text 'Anime & Manga'
shot 04-media-switch-sheet

tap_text 'Movies & TV'
shot 05-movies-tv

tap_text 'Movies & TV'
tap_text Music
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
