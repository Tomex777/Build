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

text_is_visible() {
  refresh_ui
  python3 /tmp/night_uia.py text "$1" >/dev/null 2>&1
}

read_saved_progress() {
  adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml |
    sed -n 's/.*<int name="progress:[^"]*" value="\([0-9][0-9]*\)" \/>.*/\1/p' |
    head -n 1
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

dismiss_fullscreen_education() {
  # Android 16 may place a system-owned immersive-mode education card over
  # Night's fullscreen reader. It is not part of Night and intercepts gestures.
  adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
  for _ in 1 2 3; do
    if text_is_visible "Got it"; then
      tap_text "Got it"
      sleep 1
    else
      return 0
    fi
  done
}

assert_no_crash() {
  adb logcat -d > mihon-interaction-artifacts/logcat-latest.txt || true

  # adb's own input/uiautomator helpers also log through AndroidRuntime.
  # Only treat a fatal block as Night's crash when AndroidRuntime identifies
  # Night itself as the process.
  if adb logcat -d -v brief | grep -A4 "FATAL EXCEPTION:" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during Mihon interaction test" >&2
    adb shell dumpsys activity activities > mihon-interaction-artifacts/activity-state.txt || true
    exit 1
  fi

  if ! adb shell pidof "$PACKAGE" > mihon-interaction-artifacts/pid.txt; then
    echo "Night process disappeared during Mihon interaction test" >&2
    adb logcat -d > mihon-interaction-artifacts/logcat-process-gone.txt || true
    adb shell dumpsys activity activities > mihon-interaction-artifacts/activity-state.txt || true
    exit 1
  fi
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
dismiss_fullscreen_education
adb exec-out screencap -p > mihon-interaction-artifacts/02-reader-open.png

# Hide controls and move one page through Mihon's RTL tap zone.
# The preview image is landscape, so Mihon's navigate-to-pan behavior may
# consume one or more edge taps before advancing to the next page.
echo "STEP: RTL tap-zone paging with wide-page pan"
adb shell input tap 354 760
sleep 1
page_advanced=false
for attempt in 1 2 3 4 5 6; do
  adb shell input tap 110 760
  sleep 1
  adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
    > mihon-interaction-artifacts/prefs-after-page.xml
  if grep -Eq 'progress:archive:.*value="1"' mihon-interaction-artifacts/prefs-after-page.xml; then
    page_advanced=true
    break
  fi
done
if [ "$page_advanced" != "true" ]; then
  echo "Reader never advanced after Mihon's wide-page pan taps." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-page-advance.png
  exit 1
fi
adb exec-out screencap -p > mihon-interaction-artifacts/03-page-advanced.png
assert_no_crash

# Restart the production reader and verify the saved page survives reopening.
echo "STEP: saved progress restoration"
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MihonReaderPreviewActivity" \
  --ez mihon.preview.openProductionReader true
sleep 3
dismiss_fullscreen_education
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-after-restart.xml
grep -Eq 'progress:archive:.*value="1"' mihon-interaction-artifacts/prefs-after-restart.xml
adb exec-out screencap -p > mihon-interaction-artifacts/04-progress-restored.png
assert_no_crash

# Hide reader chrome before testing the page long-press.
adb shell input tap 354 760
sleep 1

# Long-press a page and verify Mihon's page-action sheet appears.
echo "STEP: long-press page actions"
adb shell input swipe 354 760 354 760 1000
sleep 1
assert_text "Set as cover"
assert_text "Copy to clipboard"
assert_text "Share"
assert_text "Save"
adb exec-out screencap -p > mihon-interaction-artifacts/05-page-actions.png
adb shell input keyevent KEYCODE_BACK
sleep 1
assert_no_crash

# Show reader chrome, inspect paged settings, and change scale type.
echo "STEP: paged scale settings"
adb shell input tap 354 760
sleep 1
adb shell input tap 582 1500
sleep 1
assert_text "Reader settings"
assert_text "Scale type"
assert_text "Fit screen"
find_and_tap_text "Fit width" 3
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-after-scale.xml
grep -Eq '<string name="imageScaleType">FIT_WIDTH</string>' mihon-interaction-artifacts/prefs-after-scale.xml
assert_no_crash

# Switch from paged RTL to Mihon's Long strip mode.
echo "STEP: switch to Long strip"
adb logcat -c
adb shell input tap 128 1500
sleep 1
tap_text "Long strip"
tap_text "Apply"
sleep 2
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-long-strip.xml
grep -Eq '<string name="mode:[^"]+">WEBTOON</string>' mihon-interaction-artifacts/prefs-long-strip.xml
assert_no_crash

# Scroll the long strip and verify no crash.
echo "STEP: Long strip scrolling"
adb shell input tap 354 760
sleep 1
adb shell input swipe 350 1180 350 350 350
sleep 2
adb exec-out screencap -p > mihon-interaction-artifacts/06-long-strip.png
assert_no_crash

# Open long-strip settings and confirm mode-specific controls exist.
echo "STEP: Long strip settings"
adb shell input tap 354 760
sleep 1
adb shell input tap 582 1500
sleep 1
assert_text "Long strip side padding · 0%"
assert_text "Double tap zoom"
assert_text "Tap zones"
assert_text "Invert tapping"

# Enable volume navigation from the real activity and exercise both keys.
echo "STEP: volume-key navigation"
find_and_tap_text "Volume keys" 10
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-volume-keys.xml
grep -Eq '<boolean name="volumeKeys" value="true" ?/>' mihon-interaction-artifacts/prefs-volume-keys.xml

# Dismiss the settings sheet, then hide the reader chrome. Mihon only
# intercepts volume keys for page navigation while the reader menu is hidden.
# A tall Material bottom sheet can consume one Back for its internal state,
# so keep backing out until the sheet text is actually gone.
for attempt in 1 2 3; do
  if ! text_is_visible "Reader settings"; then
    break
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 1
done
if text_is_visible "Reader settings"; then
  echo "Reader settings sheet did not close before volume navigation." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-settings-dismiss.png
  exit 1
fi

# Reader chrome may be visible after the sheet closes. Toggle the center once
# so hardware keys are tested in the same menu-hidden state Mihon uses.
adb shell input tap 354 760
sleep 1

before_volume_progress="$(read_saved_progress)"
test -n "$before_volume_progress"
adb logcat -c

after_volume_down_progress="$before_volume_progress"

# Long strip consumes a volume press as a viewport scroll, not as an immediate
# page jump. Depending on page aspect ratio, more than one 3/4-screen scroll
# can be required before the first visible page (and therefore saved progress)
# changes. Exercise repeated real key events before declaring navigation dead.
for attempt in 1 2 3 4 5 6; do
  adb shell input keyevent KEYCODE_VOLUME_DOWN
  sleep 1
  assert_no_crash
  after_volume_down_progress="$(read_saved_progress)"
  test -n "$after_volume_down_progress"
  if [ "$after_volume_down_progress" != "$before_volume_progress" ]; then
    break
  fi
done

if [ "$after_volume_down_progress" = "$before_volume_progress" ]; then
  echo "Volume Down did not move Long strip far enough to update Mihon reader progress." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-volume-down.png
  exit 1
fi

adb shell input keyevent KEYCODE_VOLUME_UP
sleep 2
assert_no_crash

adb exec-out screencap -p > mihon-interaction-artifacts/07-after-volume-nav.png
adb logcat -d > mihon-interaction-artifacts/logcat.txt
