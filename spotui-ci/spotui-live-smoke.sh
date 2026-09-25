#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/spotui-artifacts
mkdir -p "$OUT"
LIVE_LOGCAT_PID=""

capture_resolver_logs() {
  if [[ -f "$OUT/resolver-live-logcat.txt" ]]; then
    grep -Ei 'SpotuiYouTubeMusic|player start|player result|resolved|signature timestamp|potoken|newpipe|PlaybackException|ExoPlayer|HttpDataSource|googlevideo' "$OUT/resolver-live-logcat.txt" \
      | tail -n 260 > "$OUT/resolver-summary.txt" || true
  fi
}

cleanup() {
  if [[ -n "${LIVE_LOGCAT_PID:-}" ]]; then
    kill "$LIVE_LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LIVE_LOGCAT_PID" >/dev/null 2>&1 || true
  fi
  capture_resolver_logs
  adb shell pm enable com.android.launcher3 >/dev/null 2>&1 || true
}
trap cleanup EXIT

APK="$SPOTUI_ROOT/spotui-app/build/outputs/apk/debug/spotui-app-debug.apk"
SOURCE_APK="$SPOTUI_ROOT/spotui-youtube-music-extension/build/outputs/apk/debug/spotui-youtube-music-extension-debug.apk"
adb uninstall com.night.spotui >/dev/null 2>&1 || true
adb uninstall com.night.spotui.ext.youtube.music >/dev/null 2>&1 || true
adb install -r "$SOURCE_APK"
adb install -r "$APK"
adb shell dumpsys package com.night.spotui.ext.youtube.music | grep -q 'SpotuiYouTubeMusicSourceService'

adb shell am force-stop com.android.launcher3 >/dev/null 2>&1 || true
adb shell pm disable-user --user 0 com.android.launcher3 >/dev/null 2>&1 || true

adb shell am force-stop com.night.spotui
adb shell am start -W -n com.night.spotui/.MainActivity >/dev/null
sleep 7

dump_ui() {
  adb shell uiautomator dump /sdcard/spotui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/spotui.xml /tmp/spotui.xml >/dev/null 2>&1 || true
}

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  dump_ui
  cp /tmp/spotui.xml "$OUT/$name.xml" || true
}

node_exists() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/spotui.xml').getroot()
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text == label or desc == label:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

node_contains() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import sys, xml.etree.ElementTree as ET
needle=sys.argv[1].lower()
root=ET.parse('/tmp/spotui.xml').getroot()
for node in root.iter('node'):
    value=((node.attrib.get('text') or '')+' '+(node.attrib.get('content-desc') or '')).lower()
    if needle in value:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_node() {
  local label="$1"
  local timeout="$2"
  local elapsed=0
  while (( elapsed < timeout )); do
    if node_exists "$label"; then return 0; fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  shot failure-node
  capture_resolver_logs
  echo "--- SpotUI resolver summary ---" >&2
  cat "$OUT/resolver-summary.txt" >&2 2>/dev/null || true
  echo "--- end resolver summary ---" >&2
  adb logcat -d -t 800 | grep -Ei 'com\.night\.spotui|youtube|innertube|ExoPlayer|AndroidRuntime|FATAL|Exception' | tail -n 200 >&2 || true
  return 1
}

wait_for_contains() {
  local label="$1"
  local timeout="$2"
  local elapsed=0
  while (( elapsed < timeout )); do
    if node_contains "$label"; then return 0; fi
    sleep 1
    elapsed=$((elapsed+1))
  done
  shot failure-contains
  adb logcat -d -t 1000 | grep -Ei 'com\.night\.spotui|youtube|innertube|ExoPlayer|AndroidRuntime|FATAL|Exception' | tail -n 240 >&2 || true
  return 1
}

tap_text() {
  local label="$1"
  dump_ui
  python3 - "$label" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
label=sys.argv[1]
root=ET.parse('/tmp/spotui.xml').getroot()
matches=[]
for node in root.iter('node'):
    text=(node.attrib.get('text') or '').strip()
    desc=(node.attrib.get('content-desc') or '').strip()
    if text != label and desc != label: continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    matches.append(((x1+x2)//2,(y1+y2)//2))
if not matches: raise SystemExit("No UI node for "+label)
x,y=matches[-1]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 2
}

tap_search_field() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
candidates=[]
for node in root.iter('node'):
    cls=node.attrib.get('class','')
    text=(node.attrib.get('text') or '').strip()
    if cls != 'android.widget.EditText' and text != 'What do you want to listen to?':
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    candidates.append(((x1+x2)//2,(y1+y2)//2))
if not candidates: raise SystemExit('Search field not found')
x,y=candidates[0]
subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
PY
  sleep 1
}

replace_search_text() {
  local value="$1"
  tap_search_field
  # Move to the end and clear enough characters for every smoke query.
  adb shell input keyevent KEYCODE_MOVE_END >/dev/null 2>&1 || true
  for _ in $(seq 1 48); do
    adb shell input keyevent KEYCODE_DEL >/dev/null 2>&1 || true
  done
  adb shell input text "$value"
  adb shell input keyevent KEYCODE_ENTER
}

assert_no_raw_timeout() {
  dump_ui
  if grep -Eqi 'Timed out awaiting|30000 ms|TimeoutCancellationException' /tmp/spotui.xml; then
    shot failure-raw-timeout
    echo "Raw timeout text leaked into SpotUI UI." >&2
    return 1
  fi
}

tap_first_adele_result() {
  dump_ui
  python3 <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('/tmp/spotui.xml').getroot()
parents={child: parent for parent in root.iter() for child in parent}
for node in root.iter('node'):
    value=((node.attrib.get('text') or '')+' '+(node.attrib.get('content-desc') or '')).lower()
    if 'adele' not in value: continue
    if node.attrib.get('class') == 'android.widget.EditText': continue
    m0=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if m0 and int(m0.group(2)) < 500: continue
    current=node
    while current is not None:
        if current.attrib.get('clickable') == 'true':
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', current.attrib.get('bounds',''))
            if m:
                x1,y1,x2,y2=map(int,m.groups())
                subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
                raise SystemExit(0)
        current=parents.get(current)
raise SystemExit('No clickable Adele result found')
PY
  sleep 2
}

wait_for_node SpotUI 20
wait_for_node Search 20
wait_for_node Library 20
shot 00-home

tap_text Search
wait_for_node Search 15

# Regression proof: explicit searches must remain reusable. Autocomplete used to
# consume source workers, which made the first search succeed and later searches
# hang until the raw 30 second IPC timeout surfaced.
replace_search_text Adele
wait_for_contains Adele 35
assert_no_raw_timeout
shot 01-search-adele

replace_search_text Coldplay
wait_for_contains Coldplay 35
assert_no_raw_timeout
shot 02-search-coldplay

replace_search_text Adele
wait_for_node 'Play Easy On Me' 35
assert_no_raw_timeout
shot 03-search-adele-again

adb shell input keyevent KEYCODE_BACK || true
sleep 2
adb logcat -c || true
adb logcat -v threadtime > "$OUT/resolver-live-logcat.txt" 2>&1 &
LIVE_LOGCAT_PID=$!
tap_text 'Play Easy On Me'
wait_for_node 'Mini player' 15

PLAYBACK_OUTCOME=""
for _ in $(seq 1 50); do
  if node_exists Pause >/dev/null 2>&1; then
    PLAYBACK_OUTCOME="playing"
    break
  fi
  if node_contains 'Source needs browser session' >/dev/null 2>&1; then
    PLAYBACK_OUTCOME="challenged"
    break
  fi
  sleep 1
done

if [[ "$PLAYBACK_OUTCOME" == "playing" ]]; then
  shot 04-playing
  adb shell dumpsys activity services com.night.spotui | grep -q 'SpotPlaybackService'
  adb shell dumpsys media_session | grep -q 'com.night.spotui'

  # Open the player and verify the production-facing layout no longer exposes
  # source/codec diagnostics. The lyrics entry should be the primary card.
  tap_text 'Mini player'
  wait_for_node 'NOW PLAYING' 12
  wait_for_node 'Lyrics' 12
  if node_exists 'SOURCE'; then
    shot failure-source-card
    echo "Legacy SOURCE diagnostic card is still visible." >&2
    exit 1
  fi
  shot 05-now-playing

  # Regression proof for the user's Hear Me Calling -> Fast symptom: moving to
  # the next queue item must update the selected track immediately rather than
  # leaving/reopening the old item while a new stream resolves.
  wait_for_node 'Now playing Easy On Me' 8
  tap_text 'Next'
  sleep 2
  if node_exists 'Now playing Easy On Me'; then
    shot failure-stale-next-track
    echo "Next kept the old Now Playing title." >&2
    exit 1
  fi
  wait_for_contains 'Now playing' 8
  shot 06-next-transition

  adb shell am start -W -a android.settings.SETTINGS >/dev/null
  sleep 3
  adb shell dumpsys activity services com.night.spotui | grep -q 'SpotPlaybackService'
  adb shell dumpsys media_session | grep -q 'com.night.spotui'
  shot 07-background
  touch "$OUT/FULL_ANONYMOUS_PLAYBACK_PASS"
  echo "SpotUI core + YouTube Music extension live playback, repeated search, and queue transition passed anonymously."
elif [[ "$PLAYBACK_OUTCOME" == "challenged" ]]; then
  shot 04-youtube-challenge
  tap_text 'Source needs browser session · Open'
  wait_for_node 'Close source browser' 20
  shot 05-sign-in-flow
  capture_resolver_logs
  grep -Eqi 'LOGIN_REQUIRED|sign in to confirm|not a bot' "$OUT/resolver-summary.txt"
  touch "$OUT/SOURCE_BROWSER_SESSION_REQUIRED"
  echo "The source hit a YouTube challenge; SpotUI crossed the extension boundary and opened the source-owned browser-session fallback correctly."
else
  capture_resolver_logs
  echo "Neither playback nor the explicit YouTube sign-in fallback appeared." >&2
  cat "$OUT/resolver-summary.txt" >&2 2>/dev/null || true
  exit 1
fi

echo "SpotUI core + extension smoke passed."
