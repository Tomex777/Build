#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/spotui-artifacts
mkdir -p "$OUT"
APP_APK="${LYRA_RELEASE_APP_APK:?LYRA_RELEASE_APP_APK is required}"
SOURCE_APK="${LYRA_RELEASE_SOURCE_APK:?LYRA_RELEASE_SOURCE_APK is required}"
LABEL="${LYRA_RELEASE_LABEL:-release}"

test -s "$APP_APK"
test -s "$SOURCE_APK"

adb uninstall com.night.spotui >/dev/null 2>&1 || true
adb uninstall com.night.spotui.ext.youtube.music >/dev/null 2>&1 || true
adb install -r "$SOURCE_APK"
adb install -r "$APP_APK"
adb shell dumpsys package com.night.spotui.ext.youtube.music | grep -q 'SpotuiYouTubeMusicSourceService'

dump_ui() {
  rm -f /tmp/lyra-release.xml
  adb shell rm -f /sdcard/lyra-release.xml >/dev/null 2>&1 || true
  timeout 8s adb shell uiautomator dump /sdcard/lyra-release.xml >/dev/null 2>&1
  timeout 5s adb pull /sdcard/lyra-release.xml /tmp/lyra-release.xml >/dev/null 2>&1
}

wait_node() {
  local label="$1" timeout_s="$2"
  for _ in $(seq 1 "$timeout_s"); do
    if dump_ui >/dev/null 2>&1 && python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
needle=sys.argv[1]
root=ET.parse('/tmp/lyra-release.xml').getroot()
for n in root.iter('node'):
    if (n.attrib.get('text') or '').strip()==needle or (n.attrib.get('content-desc') or '').strip()==needle:
        raise SystemExit(0)
raise SystemExit(1)
PY
    then return 0; fi
    sleep 1
  done
  return 1
}

tap_node() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
needle=sys.argv[1]
root=ET.parse('/tmp/lyra-release.xml').getroot()
for n in root.iter('node'):
    if (n.attrib.get('text') or '').strip()!=needle and (n.attrib.get('content-desc') or '').strip()!=needle:
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
    raise SystemExit(0)
raise SystemExit('node not found: '+needle)
PY
  sleep 2
}

adb shell am start -W -n com.night.spotui/.MainActivity | tee "$OUT/release-$LABEL-start.txt"
wait_node Home 25
wait_node Search 10
wait_node Library 10
tap_node Search
wait_node Search 10
tap_node Library
wait_node 'YOUR MUSIC' 15

adb exec-out screencap -p > "$OUT/release-$LABEL-library.png"

adb shell am force-stop com.night.spotui
adb shell am start -W -n com.night.spotui/.MainActivity | tee "$OUT/release-$LABEL-restart.txt"
wait_node Home 25
adb exec-out screencap -p > "$OUT/release-$LABEL-restart.png"

touch "$OUT/RELEASE_${LABEL^^}_PASS"
echo "Lyra release smoke passed: $LABEL"
