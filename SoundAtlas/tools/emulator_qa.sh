#!/usr/bin/env bash
set -euo pipefail
mkdir -p SoundAtlas/qa
APK=SoundAtlas/app/build/outputs/apk/debug/app-debug.apk
adb wait-for-device
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb install -r "$APK"
adb shell am start -W -n com.night.soundatlas/.MainActivity
sleep 6
W=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x[0-9]*/\1/p' | head -1)
H=$(adb shell wm size | sed -n 's/.*x\([0-9]*\)/\1/p' | head -1)
D=$(adb shell wm density | sed -n 's/.*: \([0-9]*\)/\1/p' | head -1)
test -n "$W" && test -n "$H" && test -n "$D"
echo "Actual emulator: $W x $H, density=$D"
adb shell uiautomator dump /sdcard/atlas-hierarchy.xml >/dev/null 2>&1 || true
if adb shell cat /sdcard/atlas-hierarchy.xml 2>/dev/null | grep -q 'text="Got it"'; then
  adb shell input tap $((W*84/100)) $((H/4))
  sleep 3
fi
capture() {
  local target="$1"
  local output="SoundAtlas/qa/$target"
  for attempt in 1 2 3 4; do
    adb exec-out screencap -p > "$output"
    if test "$(wc -c < "$output")" -gt 5000 &&
       python3 SoundAtlas/tools/png_modal_guard.py "$output"; then
      echo "UI_VISIBLE_SCREENSHOT: $target"
      return 0
    fi
    echo "System dialog blocking app: waiting and retrying $target ($attempt)"
    adb shell input tap $((W * 39 / 100)) $((H * 54 / 100)) || true
    sleep 5
  done
  echo "QA_FAIL: The real Sound Atlas screen stayed obscured by system UI"
  return 1
}
adb shell pidof com.night.soundatlas
capture 01-cathedral.png
assert_state 'World Storm Cathedral. Panel NONE'
# The custom Canvas docks bottom actions: World, Sounds, Walls, Rooms, Explore.
# All taps are calculated from current physical dimensions and measured density.
tap_dp() {
  adb shell input tap "$(( $1 * W / 100 ))" "$(( H - $2 * D / 160 ))"
  sleep 2
}
assert_state() {
  local expected="$1"
  adb shell uiautomator dump /sdcard/atlas-hierarchy.xml >/dev/null
  adb exec-out cat /sdcard/atlas-hierarchy.xml > SoundAtlas/qa/hierarchy-current.xml
  if ! grep -Fq "$expected" SoundAtlas/qa/hierarchy-current.xml; then
    echo "UI_STATE_MISMATCH: expected '$expected'"; exit 1
  fi
  echo "ATLAS_UI_STATE_VERIFIED: $expected"
}
tap_dp 10 65
capture 02-world-library.png
assert_state 'Panel LIBRARY'
# First-row right card, within world-library sheet (370dp tall).
tap_dp 75 435
capture 03-selected-world.png
assert_state 'World Ancient Forest. Panel NONE'
tap_dp 30 65
capture 04-sound-library.png
assert_state 'Panel SOUNDS'
# First sound card in the two-column panel, ~height of sound sheet.
tap_dp 26 425
capture 05-sound-inspector.png
assert_state 'Panel INSPECT'
# Close inspector and enter Explore mode.
tap_dp 91 263
tap_dp 90 65
capture 06-explore.png
assert_state 'Mode EXPLORE'
# Enter atmospheric editor from the header after returning to edit.
tap_dp 90 65
adb shell input tap $(( W - 95 * D / 160 )) $(( 79 * D / 160 ))
sleep 2
capture 07-atmosphere.png
assert_state 'Panel ATMOSPHERE'
adb shell pidof com.night.soundatlas
adb logcat -d -s AndroidRuntime:E > SoundAtlas/qa/crashes.txt
if grep -A 8 'FATAL EXCEPTION' SoundAtlas/qa/crashes.txt | grep -q 'com.night.soundatlas'; then
  echo "Sound Atlas runtime crash detected"
  exit 1
fi
echo "SOUND_ATLAS_EMULATOR_QA_PASSED"
