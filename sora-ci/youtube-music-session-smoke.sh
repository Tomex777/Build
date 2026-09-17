#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-youtube-session-smoke
mkdir -p "$OUT"

capture_logs() {
  adb logcat -d -t 1800 2>/dev/null \
    | grep -Ei 'com\.night\.sora|SoraYouTubeMusic|browserSession|storeSession|WebView|AndroidRuntime|FATAL EXCEPTION' \
    | tail -n 500 > "$OUT/session-logcat.txt" || true
}
trap capture_logs EXIT

adb uninstall com.night.sora.ext.youtube.music >/dev/null 2>&1 || true
adb uninstall com.night.sora >/dev/null 2>&1 || true
adb install -r "$SORA_YT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$SORA_YT_ROOT/youtube-music-extension/build/outputs/apk/debug/youtube-music-extension-debug.apk"

adb shell am force-stop com.android.launcher3 >/dev/null 2>&1 || true
adb shell pm disable-user --user 0 com.android.launcher3 >/dev/null 2>&1 || true
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

node_exists() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/window.xml').getroot()
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text == label or desc == label or label in text or label in desc:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1" timeout="${2:-30}" elapsed=0
  while (( elapsed < timeout )); do
    if node_exists "$label" >/dev/null 2>&1; then
      echo "UI node '$label' available after ${elapsed}s"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  echo "Timed out waiting for '$label'" >&2
  dump_ui
  cat /tmp/window.xml >&2 || true
  capture_logs
  return 1
}

tap_text() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/window.xml').getroot()
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
  sleep 2
}

# Ensure the external source is installed and discoverable before touching UI.
adb shell dumpsys package com.night.sora.ext.youtube.music | grep -q 'YouTubeMusicExtensionService'
shot 00-home

wait_for_node More 20
tap_text More
wait_for_node Extensions 20
tap_text Extensions
wait_for_node 'Sora YouTube Music' 30
shot 01-extensions

tap_text 'Sora YouTube Music'
wait_for_node 'Open YouTube Music' 20
shot 02-youtube-extension-detail

tap_text 'Open YouTube Music'
wait_for_node 'Close web view' 30
shot 03-youtube-login-webview

# browserSession IPC succeeded only if Core could render the source-owned WebView.
# The page may be a Google sign-in/challenge or may already redirect to Music,
# but it must remain inside Sora and must not crash the extension process.
dump_ui
if ! grep -Eq 'accounts\.google\.com|music\.youtube\.com|YouTube Music sign in|Close web view' /tmp/window.xml; then
  echo 'YouTube Music source browser did not expose its expected login/session page.' >&2
  cat /tmp/window.xml >&2 || true
  exit 1
fi
if adb logcat -d | grep -Eq 'FATAL EXCEPTION.*(com\.night\.sora|SoraYouTubeMusic)'; then
  echo 'Sora or YouTube Music extension crashed while opening the source browser.' >&2
  capture_logs
  exit 1
fi

tap_text 'Close web view'
wait_for_node 'Open YouTube Music' 20
shot 04-returned-to-extension
capture_logs
