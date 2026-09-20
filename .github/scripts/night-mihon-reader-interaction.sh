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

read_media_volume() {
  local raw volume audio_dump current_line

  # Android 11's media_session shell command can emit its [V] diagnostics on
  # stderr, so capture both streams before parsing the reported music volume.
  raw="$(adb shell cmd media_session volume --stream 3 --get 2>&1 || true)"
  volume="$(printf '%s\n' "$raw" | sed -n 's/.*volume is \([0-9][0-9]*\).*/\1/p' | tail -n 1)"

  # Keep a second, framework-level path so the consumption assertion is not
  # tied to shell-command output formatting.
  if [ -z "$volume" ]; then
    audio_dump="$(adb shell dumpsys audio 2>&1 || true)"
    current_line="$(
      printf '%s\n' "$audio_dump" |
        awk '
          /- STREAM_MUSIC:/ { in_music = 1; next }
          in_music && /Current:/ { print; exit }
        '
    )"
    volume="$(
      printf '%s\n' "$current_line" |
        sed -n 's/.*(speaker):[[:space:]]*\([0-9][0-9]*\).*/\1/p'
    )"
    raw="$raw
$current_line"
  fi

  printf '%s\n' "$raw" > mihon-interaction-artifacts/media-volume-latest.txt
  if [ -z "$volume" ]; then
    echo "Could not read Android media volume for volume-key consumption test." >&2
    printf '%s\n' "$raw" >&2
    exit 1
  fi
  printf '%s\n' "$volume"
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

settings_sheet_visible() {
  local marker
  for marker in \
    "Reader settings" \
    "Long strip side padding · 0%" \
    "Double tap zoom" \
    "Tap zones" \
    "Invert tapping" \
    "Keep screen on" \
    "Fullscreen" \
    "Show page number" \
    "Volume keys" \
    "Invert volume keys" \
    "Disable zoom out" \
    "Background color"; do
    if text_is_visible "$marker"; then
      return 0
    fi
  done
  return 1
}

dismiss_reader_settings() {
  local context_label="${1:-reader navigation}"
  local attempt

  # This helper is called immediately after interacting with the real settings
  # sheet, so close it deterministically once before probing. The sheet can
  # scroll far enough that any single label disappears from UiAutomator.
  adb shell input keyevent KEYCODE_BACK
  sleep 1

  for attempt in 1 2; do
    if ! settings_sheet_visible; then
      return 0
    fi
    adb shell input keyevent KEYCODE_BACK
    sleep 1
  done

  if ! settings_sheet_visible; then
    return 0
  fi

  echo "Reader settings sheet did not close before $context_label." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-settings-dismiss.png
  return 1
}

hide_reader_chrome() {
  local context_label="${1:-reader navigation}"
  local attempt

  # Mihon only routes volume keys to the viewer when its chrome is hidden.
  # Do not assume one center tap has a particular starting state: inspect the
  # actual top-bar title and keep toggling until the chrome is definitely gone.
  for attempt in 1 2 3; do
    if ! text_is_visible "Mihon Interaction Test"; then
      return 0
    fi
    adb shell input tap 354 760
    sleep 1
  done

  echo "Reader chrome remained visible before $context_label." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-reader-chrome-visible.png
  return 1
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
dismiss_reader_settings "volume navigation"

# Explicitly verify Mihon's menu-hidden state before injecting hardware keys.
hide_reader_chrome "volume navigation"

# Put the media stream in the middle of its range. If Night fails to consume
# either hardware key, Android will visibly move this value and the test fails.
adb shell cmd media_session volume --stream 3 --set 7 >/dev/null 2>&1
baseline_media_volume="$(read_media_volume)"
if [ "$baseline_media_volume" != "7" ]; then
  echo "Could not establish media-volume baseline at 7 (got $baseline_media_volume)." >&2
  exit 1
fi

before_volume_progress="$(read_saved_progress)"
test -n "$before_volume_progress"
adb logcat -c

# Normal Mihon mapping: Volume Down moves forward.
adb shell input keyevent KEYCODE_VOLUME_DOWN
sleep 2
assert_no_crash
after_volume_down_progress="$(read_saved_progress)"
after_volume_down_media="$(read_media_volume)"
test -n "$after_volume_down_progress"

if [ "$after_volume_down_progress" -le "$before_volume_progress" ]; then
  echo "Volume Down did not move forward in Long strip." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-volume-down.png
  exit 1
fi
if [ "$after_volume_down_media" != "$baseline_media_volume" ]; then
  echo "Volume Down changed Android media volume instead of being consumed by Night." >&2
  exit 1
fi

# Normal Mihon mapping: Volume Up moves backward.
adb shell input keyevent KEYCODE_VOLUME_UP
sleep 2
assert_no_crash
after_volume_up_progress="$(read_saved_progress)"
after_volume_up_media="$(read_media_volume)"
test -n "$after_volume_up_progress"

if [ "$after_volume_up_progress" -ge "$after_volume_down_progress" ]; then
  echo "Volume Up did not move backward in Long strip." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-volume-up.png
  exit 1
fi
if [ "$after_volume_up_media" != "$baseline_media_volume" ]; then
  echo "Volume Up changed Android media volume instead of being consumed by Night." >&2
  exit 1
fi

# Make sure there is room to navigate backward before testing inversion.
seed_progress="$after_volume_up_progress"
for attempt in 1 2 3; do
  if [ "$seed_progress" -gt 0 ]; then
    break
  fi
  adb shell input keyevent KEYCODE_VOLUME_DOWN
  sleep 2
  assert_no_crash
  seed_progress="$(read_saved_progress)"
done
if [ "$seed_progress" -le 0 ]; then
  echo "Could not move away from the first page before inverted-volume test." >&2
  exit 1
fi

# Turn on Mihon's inverted-volume option from the real settings sheet.
adb shell input tap 354 760
sleep 1
adb shell input tap 582 1500
sleep 1
assert_text "Reader settings"
find_and_tap_text "Invert volume keys" 10
adb shell run-as "$PACKAGE" cat shared_prefs/night_mihon_reader.xml \
  > mihon-interaction-artifacts/prefs-invert-volume-keys.xml
grep -Eq '<boolean name="invertVolumeKeys" value="true" ?/>' mihon-interaction-artifacts/prefs-invert-volume-keys.xml

dismiss_reader_settings "inverted-volume navigation"

# Reopen the production reader after inversion is persisted. Long strip restores
# the saved page with scrollToPositionWithOffset(page, 0), giving this assertion
# a known page boundary instead of an arbitrary in-page offset.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MihonReaderPreviewActivity" \
  --ez mihon.preview.openProductionReader true
sleep 3
assert_alive
assert_no_crash
hide_reader_chrome "inverted-volume navigation"

before_inverted_progress="$(read_saved_progress)"
before_inverted_media="$(read_media_volume)"
test -n "$before_inverted_progress"
if [ "$before_inverted_progress" -le 0 ]; then
  echo "Inverted-volume restart did not restore a page with backward room." >&2
  exit 1
fi
adb exec-out screencap -p > mihon-interaction-artifacts/07-inverted-start.png

# Inverted mapping: Volume Down moves backward.
adb shell input keyevent KEYCODE_VOLUME_DOWN
sleep 2
assert_no_crash
after_inverted_down_progress="$(read_saved_progress)"
after_inverted_down_media="$(read_media_volume)"

cat > mihon-interaction-artifacts/volume-navigation-partial.txt <<EOF
baselineMediaVolume=$baseline_media_volume
normalStartPage=$before_volume_progress
normalAfterDownPage=$after_volume_down_progress
normalAfterUpPage=$after_volume_up_progress
invertedStartPage=$before_inverted_progress
invertedAfterDownPage=$after_inverted_down_progress
invertedMediaBefore=$before_inverted_media
invertedMediaAfterDown=$after_inverted_down_media
EOF

if [ "$after_inverted_down_progress" -ge "$before_inverted_progress" ]; then
  echo "Inverted Volume Down did not move backward in Long strip." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-inverted-volume-down.png
  exit 1
fi
if [ "$after_inverted_down_media" != "$before_inverted_media" ]; then
  echo "Inverted Volume Down leaked to Android media volume." >&2
  exit 1
fi

# Inverted mapping: Volume Up moves forward.
adb shell input keyevent KEYCODE_VOLUME_UP
sleep 2
assert_no_crash
after_inverted_up_progress="$(read_saved_progress)"
after_inverted_up_media="$(read_media_volume)"

if [ "$after_inverted_up_progress" -le "$after_inverted_down_progress" ]; then
  echo "Inverted Volume Up did not move forward in Long strip." >&2
  adb exec-out screencap -p > mihon-interaction-artifacts/failure-inverted-volume-up.png
  exit 1
fi
if [ "$after_inverted_up_media" != "$before_inverted_media" ]; then
  echo "Inverted Volume Up leaked to Android media volume." >&2
  exit 1
fi

cat > mihon-interaction-artifacts/volume-navigation.txt <<EOF
volumeKeys=true
baselineMediaVolume=$baseline_media_volume
normalStartPage=$before_volume_progress
normalAfterDownPage=$after_volume_down_progress
normalAfterUpPage=$after_volume_up_progress
invertVolumeKeys=true
invertedStartPage=$before_inverted_progress
invertedAfterDownPage=$after_inverted_down_progress
invertedAfterUpPage=$after_inverted_up_progress
finalMediaVolume=$after_inverted_up_media
EOF

adb exec-out screencap -p > mihon-interaction-artifacts/07-after-volume-nav.png
adb logcat -d > mihon-interaction-artifacts/logcat.txt
assert_no_crash
