#!/usr/bin/env bash
set -euo pipefail

APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.example.whatsapp"
ARCHIVE="/data/user/0/$PACKAGE/cache/mihon-reader-preview.cbz"

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
mkdir -p mihon-interaction-artifacts
adb logcat -c

cat >/tmp/night_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

mode, value = sys.argv[1], sys.argv[2]
attr = "text" if mode == "text" else "content-desc"
root = ET.parse("/tmp/window.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get(attr) == value:
        nums = [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds", ""))]
        if len(nums) == 4:
            print((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
            raise SystemExit(0)
raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb exec-out cat /sdcard/window.xml > /tmp/window.xml
}

assert_alive() {
  adb shell pidof "$PACKAGE" >/dev/null
}

assert_text() {
  refresh_ui
  python3 /tmp/night_uia.py text "$1" >/dev/null
}

assert_desc() {
  refresh_ui
  python3 /tmp/night_uia.py desc "$1" >/dev/null
}

tap_text() {
  refresh_ui
  local coords x y
  coords="$(python3 /tmp/night_uia.py text "$1")"
  x="${coords% *}"
  y="${coords#* }"
  adb shell input tap "$x" "$y"
  sleep 1
}

tap_desc() {
  refresh_ui
  local coords x y
  coords="$(python3 /tmp/night_uia.py desc "$1")"
  x="${coords% *}"
  y="${coords#* }"
  adb shell input tap "$x" "$y"
  sleep 1
}

find_and_tap_text() {
  local target="$1"
  local tries="${2:-8}"
  local coords x y
  for _ in $(seq 1 "$tries"); do
    refresh_ui
    if coords="$(python3 /tmp/night_uia.py text "$target" 2>/dev/null)"; then
      x="${coords% *}"
      y="${coords#* }"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    adb shell input swipe 350 1200 350 450 260
    sleep 1
  done
  echo "Could not find UI text: $target" >&2
  return 1
}

assert_no_crash() {
  if adb logcat -d -v brief | grep -A8 -E "FATAL EXCEPTION|AndroidRuntime" | grep -q "$PACKAGE"; then
    echo "Night crashed during Mihon interaction test" >&2
    adb logcat -d > mihon-interaction-artifacts/logcat.txt
    exit 1
  fi
  assert_alive
}

# Seed a real CBZ using the preview's in-app archive creator.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MihonReaderPreviewActivity"
sleep 3
assert_alive
adb shell run-as "$PACKAGE" test -s cache/mihon-reader-preview.cbz
adb exec-out screencap -p > mihon-interaction-artifacts/01-seeded-cbz.png

# Use the real production reader activity for the remaining tests.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MihonReaderPreviewActivity" \
  --ez mihon.preview.openProductionReader true
sleep 3
assert_alive
assert_desc "Reading mode"
assert_desc "Reader settings"
adb exec-out screencap -p > mihon-interaction-artifacts/02-reader-open.png

# Hide controls and move one page through Mihon's RTL tap zone.
adb shell input tap 354 760
sleep 1
assert_text "1 / 8"
adb shell input tap 110 760
sleep 1
assert_text "2 / 8"
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-after-page.xml
grep -Eq 'progress:archive:.*value="1"' mihon-interaction-artifacts/prefs-after-page.xml
assert_no_crash

# Restart the production reader and verify the saved page is restored.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MihonReaderPreviewActivity" \
  --ez mihon.preview.openProductionReader true
sleep 3
adb shell input tap 354 760
sleep 1
assert_text "2 / 8"
adb exec-out screencap -p > mihon-interaction-artifacts/03-progress-restored.png
assert_no_crash

# Long-press a page and verify Mihon's page-action sheet appears.
adb shell input swipe 354 760 354 760 1000
sleep 1
assert_text "Set as cover"
assert_text "Copy to clipboard"
assert_text "Share"
assert_text "Save"
adb exec-out screencap -p > mihon-interaction-artifacts/04-page-actions.png
adb shell input keyevent KEYCODE_BACK
sleep 1
assert_no_crash

# Show reader chrome, inspect paged settings, and change scale type.
adb shell input tap 354 760
sleep 1
tap_desc "Reader settings"
assert_text "Reader settings"
assert_text "Scale type"
assert_text "Fit screen"
find_and_tap_text "Fit width" 3
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-after-scale.xml
grep -q 'name="imageScaleType" value="FIT_WIDTH"' mihon-interaction-artifacts/prefs-after-scale.xml
assert_no_crash

# Switch from paged RTL to Mihon's Long strip mode.
tap_desc "Reading mode"
tap_text "Long strip"
tap_text "Apply"
sleep 2
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-long-strip.xml
grep -q 'value="WEBTOON"' mihon-interaction-artifacts/prefs-long-strip.xml
assert_no_crash

# Scroll the long strip and verify no crash.
adb shell input tap 354 760
sleep 1
adb shell input swipe 350 1180 350 350 350
sleep 2
adb exec-out screencap -p > mihon-interaction-artifacts/05-long-strip.png
assert_no_crash

# Open long-strip settings and confirm mode-specific controls exist.
adb shell input tap 354 760
sleep 1
tap_desc "Reader settings"
assert_text "Long strip side padding · 0%"
assert_text "Double tap zoom"
assert_text "Tap zones"
assert_text "Invert tapping"

# Enable volume navigation from the real activity and exercise both keys.
find_and_tap_text "Volume keys" 10
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_VOLUME_DOWN
sleep 1
adb shell input keyevent KEYCODE_VOLUME_UP
sleep 1
assert_no_crash

adb exec-out screencap -p > mihon-interaction-artifacts/06-after-volume-nav.png
adb logcat -d > mihon-interaction-artifacts/logcat.txt
