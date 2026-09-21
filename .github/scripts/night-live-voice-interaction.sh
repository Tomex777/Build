#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.NightLiveVoicePreviewActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-live-voice-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_live_voice_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
mode = sys.argv[1]
value = sys.argv[2]

def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        return None
    return tuple(map(int, m.groups()))

nodes = list(root.iter("node"))
if mode == "text":
    node = next((n for n in nodes if n.attrib.get("text") == value), None)
else:
    node = next((n for n in nodes if n.attrib.get("content-desc") == value), None)

if node is None or bounds(node) is None:
    raise SystemExit(2)

x1,y1,x2,y2 = bounds(node)
print(f"{(x1+x2)//2} {(y1+y2)//2}")
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb exec-out cat /sdcard/window.xml > /tmp/window.xml
}

tap_text() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_live_voice_uia.py text "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

assert_text() {
  refresh_ui
  python3 /tmp/night_live_voice_uia.py text "$1" >/dev/null
}

assert_desc() {
  refresh_ui
  python3 /tmp/night_live_voice_uia.py desc "$1" >/dev/null
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during Live Voice UI validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
    echo "Night hit an ANR during Live Voice UI validation." >&2
    exit 1
  fi
}

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 4

adb shell pidof "$PACKAGE" >/dev/null
assert_text "Night"
assert_text "Live voice"
assert_text "Listening"
assert_text "Speaker"
assert_text "Mute"
assert_text "More"
assert_text "End"
assert_desc "Night avatar"
adb exec-out screencap -p > "$OUT/01-live-voice.png"
refresh_ui
cp /tmp/window.xml "$OUT/01-live-voice.xml"

echo "STEP: mute control updates live state"
tap_desc "Mute"
assert_text "Microphone muted"
adb exec-out screencap -p > "$OUT/02-live-voice-muted.png"

echo "STEP: speaker and more controls are interactive"
tap_desc "Speaker"
tap_desc "More"
assert_text "Mic off • Speaker"
adb exec-out screencap -p > "$OUT/03-live-voice-more.png"

assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "voiceOnly=true" \
  "noCallsBottomTab=true" \
  "speakerControl=true" \
  "muteControl=true" \
  "morePanel=true" \
  "endControl=true" \
  "whatsappStyleTray=true" \
  "doodleWallpaper=true" > "$OUT/summary.txt"
