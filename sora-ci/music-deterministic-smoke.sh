#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-music-deterministic
mkdir -p "$OUT"

cleanup() {
  adb shell settings put global always_finish_activities 0 >/dev/null 2>&1 || true
}
trap cleanup EXIT

adb uninstall com.night.sora >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.demo >/dev/null 2>&1 || true
adb install -r "$SORA_ROOT/test-extension/build/outputs/apk/debug/test-extension-debug.apk"
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity
sleep 5

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/sora-music-window.xml >/dev/null 2>&1 || true
}

node_exists() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try:
    root=ET.parse('/tmp/sora-music-window.xml').getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text == label or desc == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1"
  local timeout="${2:-25}"
  local elapsed=0
  while (( elapsed < timeout )); do
    if node_exists "$label"; then
      echo "Found '$label' after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  echo "Timed out waiting for '$label'" >&2
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|com.night.sora' | tail -n 30 >&2 || true
  adb logcat -d -t 500 | grep -Ei 'com\.night\.sora|MusicPlayback|ExoPlayer|AndroidRuntime|FATAL EXCEPTION' | tail -n 160 >&2 || true
  shot "failure-${label//[^A-Za-z0-9]/_}"
  return 1
}

tap_text() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/sora-music-window.xml').getroot()
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
    p=((x1+x2)//2,(y1+y2)//2)
    if text == label or desc == label:
        exact.append(p)
    elif label in text or label in desc:
        partial.append(p)
points=exact or partial
if not points:
    raise SystemExit(f'UI node not found: {label}')
x,y=points[0]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}

tap_media_tab() {
  if node_exists Media; then
    tap_text Media
    return
  fi
  local size width height
  size=$(adb shell wm size | sed -n 's/.*Physical size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)
  read -r width height <<<"$size"
  adb shell input tap $(( width * 3 / 10 )) $(( height * 94 / 100 ))
  sleep 2
}

shot 00-home

tap_media_tab
wait_for_node 'Anime & Manga' 10
tap_text 'Anime & Manga'
wait_for_node Music 10
tap_text Music
wait_for_node 'Low Light' 20
shot 01-music-demo-catalog

tap_text 'Low Light'
wait_for_node 'Mini player' 20
wait_for_node Pause 20
shot 02-playing

# Background Media3 service/session must stay alive.
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 3
wait_for_node Play 12
shot 03-background-paused

tap_text Play
wait_for_node Pause 15
shot 04-background-resumed

# Full player and queue transport.
tap_text 'Mini player'
wait_for_node 'Low Light' 10
wait_for_node Next 10
shot 05-now-playing

tap_text Next
wait_for_node 'Wake Slowly' 20
wait_for_node Pause 20
shot 06-next-track

tap_text Previous
wait_for_node 'Low Light' 20
wait_for_node Pause 20
shot 07-previous-track

# Activity recreation must reconnect to the same process-wide player.
adb shell settings put global always_finish_activities 1
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 4
wait_for_node Pause 15
wait_for_node 'Low Light' 15
shot 08-after-activity-recreation
adb shell settings put global always_finish_activities 0

echo 'Sora deterministic Music smoke passed.'
