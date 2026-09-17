#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-youtube-music-smoke
mkdir -p "$OUT"

capture_logs() {
  adb logcat -d -t 2500 2>/dev/null \
    | grep -Ei 'com\.night\.sora|SoraYouTubeMusic|youtube|innertube|googlevideo|ExoPlayer|PlaybackException|HttpDataSource|AndroidRuntime|FATAL EXCEPTION' \
    | tail -n 500 > "$OUT/youtube-music-logcat.txt" || true
}
trap capture_logs EXIT

adb uninstall com.night.sora.ext.live >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.demo >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.youtube.music >/dev/null 2>&1 || true
adb uninstall com.night.sora >/dev/null 2>&1 || true

adb install -r "$SORA_YT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$SORA_YT_ROOT/youtube-music-extension/build/outputs/apk/debug/youtube-music-extension-debug.apk"
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 6

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
}

ensure_sora_foreground() {
  if ! adb shell dumpsys activity activities | grep -q 'mResumedActivity.*com.night.sora'; then
    adb shell am start -W -n com.night.sora/.MainActivity >/dev/null 2>&1 || true
    sleep 2
  fi
}

tap_text_once() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try:
    root=ET.parse('/tmp/window.xml').getroot()
except Exception:
    raise SystemExit(2)
exact=[]; partial=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    point=((x1+x2)//2,(y1+y2)//2)
    if text == label or desc == label: exact.append(point)
    elif label in text or label in desc: partial.append(point)
points=exact or partial
if not points: raise SystemExit(1)
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

log_state() {
  local reason="$1"
  echo "--- YouTube Music smoke diagnostics: $reason ---" >&2
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|com.night.sora|Application Not Responding' | tail -n 30 >&2 || true
  adb shell dumpsys activity services com.night.sora | grep -E 'MusicPlaybackService|ServiceRecord' >&2 || true
  adb shell dumpsys media_session | grep -A8 -B3 'com.night.sora' >&2 || true
  capture_logs
  tail -n 220 "$OUT/youtube-music-logcat.txt" >&2 2>/dev/null || true
  dump_ui
  cat /tmp/window.xml >&2 2>/dev/null || true
  echo "--- end diagnostics ---" >&2
}

node_exists() {
  local label="$1"
  dump_ui
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
  local timeout="${2:-30}"
  local elapsed=0
  while (( elapsed < timeout )); do
    dismiss_system_dialogs
    if node_exists "$label" >/dev/null 2>&1; then
      echo "UI node '$label' available after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  log_state "timed out waiting for $label"
  shot "failure-${label//[^A-Za-z0-9]/_}"
  return 1
}

tap_root_media() {
  local size width height
  size=$(adb shell wm size | sed -n 's/.*Physical size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)
  read -r width height <<<"$size"
  adb shell input tap $(( width * 3 / 10 )) $(( height * 94 / 100 ))
  sleep 2
}

tap_text() {
  local label="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    dismiss_system_dialogs
    if tap_text_once "$label" >/dev/null 2>&1; then
      sleep 2
      return 0
    fi
    sleep 1
  done
  if [[ "$label" == "Media" ]]; then
    ensure_sora_foreground
    tap_root_media
    return 0
  fi
  log_state "could not tap $label"
  return 1
}

tap_first_music_tile() {
  dismiss_system_dialogs
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/window.xml').getroot()
choices=[]
for node in root.iter('node'):
    if node.attrib.get('clickable') != 'true': continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    width=x2-x1; height=y2-y1
    if 760 <= y1 <= 1550 and 320 <= width <= 650 and 80 <= height <= 260:
        choices.append((y1,x1,x2,y2))
if not choices:
    raise SystemExit('No playable Music tile found')
y1,x1,x2,y2=sorted(choices)[0]
subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
PY
  sleep 2
}

# Confirm the external service is installed and discoverable.
adb shell dumpsys package com.night.sora.ext.youtube.music | grep -q 'YouTubeMusicExtensionService'
dismiss_system_dialogs
shot 00-home

tap_text Media
sleep 2
dismiss_system_dialogs
# Open the media switcher from whichever Anime/Manga label is visible, then choose Music.
if tap_text_once 'Anime & Manga' >/dev/null 2>&1; then
  sleep 1
else
  tap_text Anime
  sleep 1
  tap_text 'Anime & Manga'
fi
tap_text Music

# YouTube Music browse is network-backed; allow a little longer than the normal UI smoke.
sleep 10
dismiss_system_dialogs
shot 01-youtube-music-home

tap_first_music_tile
wait_for_node 'Mini player' 35
wait_for_node Pause 35
shot 02-youtube-music-playing

# Prove Core is playing through the shared Media3 session and survives backgrounding.
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
shot 03-background

adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 3
dismiss_system_dialogs
wait_for_node Play 12
shot 04-system-paused

tap_text Play
wait_for_node Pause 20
shot 05-system-resumed

capture_logs
