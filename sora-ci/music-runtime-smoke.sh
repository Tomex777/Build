#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-music-runtime
mkdir -p "$OUT"

cleanup() {
  adb shell settings put global always_finish_activities 0 >/dev/null 2>&1 || true
}
trap cleanup EXIT

adb uninstall com.night.sora >/dev/null 2>&1 || true
adb uninstall com.night.sora.ext.demo >/dev/null 2>&1 || true
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$SORA_ROOT/test-extension/build/outputs/apk/debug/test-extension-debug.apk"
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity
sleep 5

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

dump_xml() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1 || true
}

node_exists() {
  local label="$1"
  dump_xml
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
try: root=ET.parse('/tmp/window.xml').getroot()
except Exception: raise SystemExit(1)
for n in root.iter('node'):
    if (n.attrib.get('text') or '').strip() == label or (n.attrib.get('content-desc') or '').strip() == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1" timeout="${2:-25}" elapsed=0
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
  adb logcat -d -t 600 | grep -Ei 'com\.night\.sora|ExoPlayer|AndroidRuntime|FATAL|Playback' | tail -n 180 >&2 || true
  return 1
}

tap_text() {
  local label="$1"
  dump_xml
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/window.xml').getroot()
exact=[]
for n in root.iter('node'):
    text=(n.attrib.get('text') or '').strip()
    desc=(n.attrib.get('content-desc') or '').strip()
    if text != label and desc != label: continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    exact.append(((x1+x2)//2,(y1+y2)//2))
if not exact: raise SystemExit(f'No node for {label!r}')
x,y=exact[-1]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}

shot 00-home
wait_for_node Media 15
tap_text Media
wait_for_node 'Anime & Manga' 15
tap_text 'Anime & Manga'
wait_for_node Music 15
tap_text Music
wait_for_node 'Low Light' 25
shot 01-music-demo-loaded
tap_text 'Low Light'
wait_for_node 'Mini player' 25
wait_for_node Pause 25
shot 02-playing

adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'

# Background session must survive leaving Sora.
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 3
wait_for_node 'Mini player' 15
wait_for_node Pause 15
shot 03-background-resumed

# Activity recreation must reconnect to the same process-wide player.
adb shell settings put global always_finish_activities 1
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 4
wait_for_node 'Mini player' 15
wait_for_node Pause 15
shot 04-activity-recreated
adb shell settings put global always_finish_activities 0

tap_text 'Mini player'
wait_for_node Pause 15
tap_text Pause
wait_for_node Play 10
tap_text Play
wait_for_node Pause 15
tap_text Next
wait_for_node 'Wake Slowly' 20
wait_for_node Pause 20
tap_text Previous
wait_for_node 'Low Light' 20
wait_for_node Pause 20
shot 05-transport

echo 'Sora deterministic Music runtime smoke passed.'
