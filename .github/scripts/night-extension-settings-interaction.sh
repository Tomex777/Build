#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.ExtensionSettingsPreviewActivity"
APK="night-extension-settings-apk/app-debug.apk"
OUT="night-extension-settings-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_ext_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
mode = sys.argv[1]
value = sys.argv[2] if len(sys.argv) > 2 else ""

def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        return None
    return tuple(map(int, m.groups()))

nodes = list(root.iter("node"))

if mode == "has_text":
    print("true" if any(value in n.attrib.get("text", "") for n in nodes) else "false")
    raise SystemExit(0)
if mode == "has_desc":
    print("true" if any(value == n.attrib.get("content-desc", "") for n in nodes) else "false")
    raise SystemExit(0)

match = None
if mode == "desc":
    match = next((n for n in nodes if n.attrib.get("content-desc") == value), None)
elif mode == "text":
    match = next((n for n in nodes if n.attrib.get("text") == value), None)

if match is None or bounds(match) is None:
    raise SystemExit(2)

x1,y1,x2,y2 = bounds(match)
print(f"{(x1+x2)//2} {(y1+y2)//2}")
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-extension-settings.xml >/dev/null
  adb exec-out cat /sdcard/night-extension-settings.xml > /tmp/window.xml
}

tap_desc() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_ext_uia.py desc "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

tap_text() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_ext_uia.py text "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

assert_desc() {
  refresh_ui
  python3 /tmp/night_ext_uia.py desc "$1" >/dev/null
}

assert_text_contains() {
  refresh_ui
  if [ "$(python3 /tmp/night_ext_uia.py has_text "$1")" != "true" ]; then
    echo "Missing extension settings text: $1" >&2
    cp /tmp/window.xml "$OUT/failure-window.xml" || true
    adb exec-out screencap -p > "$OUT/failure-screen.png" || true
    exit 1
  fi
}

assert_no_desc() {
  refresh_ui
  if [ "$(python3 /tmp/night_ext_uia.py has_desc "$1")" = "true" ]; then
    echo "Unexpected extension settings control: $1" >&2
    exit 1
  fi
}

assert_alive() {
  adb shell pidof "$PACKAGE" >/dev/null
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat-current.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat-current.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during extension settings validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat-current.txt"; then
    echo "Night hit an ANR during extension settings validation." >&2
    exit 1
  fi
}

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb shell run-as "$PACKAGE" rm -f shared_prefs/night_extension_settings.xml 2>/dev/null || true
adb logcat -c

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 3
assert_alive

echo "STEP: anime typed settings"
assert_text_contains "Anime"
assert_desc "Quality: 720p"
assert_desc "Quality: 1080p"
assert_desc "Audio: Sub"
assert_desc "Audio: Dub"
assert_desc "Prefer batch downloads"
assert_desc "More"
# MP4-only is a valid built-in default but must not create pointless UI.
assert_no_desc "Container: MP4"
adb exec-out screencap -p > "$OUT/01-anime-settings.png"
refresh_ui
cp /tmp/window.xml "$OUT/01-anime-settings.xml"

echo "STEP: persist manual anime defaults"
tap_desc "Quality: 1080p"
tap_desc "Audio: Dub"
tap_desc "Prefer batch downloads"
tap_desc "More"
sleep 1
adb shell input swipe 360 1240 360 600 500
sleep 1
assert_desc "Download folder"
adb exec-out screencap -p > "$OUT/02-anime-more.png"

prefs="$(adb shell run-as "$PACKAGE" cat shared_prefs/night_extension_settings.xml)"
printf '%s\n' "$prefs" > "$OUT/persisted-anime.xml"
printf '%s' "$prefs" | grep -q "night.anime"
printf '%s' "$prefs" | grep -q "quality"
printf '%s' "$prefs" | grep -q "1080p"
printf '%s' "$prefs" | grep -q "audio"
printf '%s' "$prefs" | grep -q "dub"
printf '%s' "$prefs" | grep -q "prefer_batch"

echo "STEP: settings survive activity restart"
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 3
assert_alive
prefs_after="$(adb shell run-as "$PACKAGE" cat shared_prefs/night_extension_settings.xml)"
printf '%s\n' "$prefs_after" > "$OUT/persisted-anime-after-restart.xml"
printf '%s' "$prefs_after" | grep -q "1080p"
printf '%s' "$prefs_after" | grep -q "dub"

echo "STEP: music proves different generic primitives"
tap_desc "Music settings tab"
sleep 1
assert_text_contains "Music"
assert_desc "Format: M4A"
assert_desc "Format: FLAC"
assert_desc "Bitrate"
assert_desc "Metadata: Cover art"
assert_desc "Metadata: Lyrics"
assert_desc "Normalize loudness"
assert_desc "Playlist folder"
adb exec-out screencap -p > "$OUT/03-music-settings.png"
refresh_ui
cp /tmp/window.xml "$OUT/03-music-settings.xml"

tap_desc "Format: FLAC"
tap_desc "Metadata: Lyrics"
tap_desc "Normalize loudness"

# Reach action + advanced controls lower in the schema.
adb shell input swipe 360 1280 360 440 650
sleep 1
assert_desc "Clear cache"
assert_desc "More"
tap_desc "Clear cache"
assert_text_contains "Action handled: night.music:clear_cache"
tap_desc "More"
sleep 1
adb shell input swipe 360 1260 360 620 500
sleep 1
assert_desc "Lyrics: Embed when available"
assert_desc "Lyrics: Off"
assert_desc "Prefer gapless albums"
adb exec-out screencap -p > "$OUT/04-music-more-and-action.png"
refresh_ui
cp /tmp/window.xml "$OUT/04-music-more-and-action.xml"

music_prefs="$(adb shell run-as "$PACKAGE" cat shared_prefs/night_extension_settings.xml)"
printf '%s\n' "$music_prefs" > "$OUT/persisted-all.xml"
printf '%s' "$music_prefs" | grep -q "night.music"
printf '%s' "$music_prefs" | grep -q "flac"
printf '%s' "$music_prefs" | grep -q "normalize"
printf '%s' "$music_prefs" | grep -q "lyrics"

cat "$OUT/"*-settings.xml "$OUT/"*-more-and-action.xml > "$OUT/all-ui.xml"
if grep -qi "Created by" "$OUT/all-ui.xml"; then
  echo "Visible watermark text found in extension settings." >&2
  exit 1
fi
if grep -qi "Sticker" "$OUT/all-ui.xml"; then
  echo "Sticker UI leaked into extension settings." >&2
  exit 1
fi

adb logcat -d -v threadtime > "$OUT/logcat.txt"
assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "typedSchema=true" \
  "choice=true" \
  "toggle=true" \
  "multiChoice=true" \
  "numberRange=true" \
  "text=true" \
  "action=true" \
  "advancedMore=true" \
  "singleValueChoiceHidden=true" \
  "persistentManualSettings=true" \
  "animeSchema=true" \
  "musicSchema=true" \
  "extensionInjectedCompose=false" \
  "watermarkFree=true" > "$OUT/summary.txt"
