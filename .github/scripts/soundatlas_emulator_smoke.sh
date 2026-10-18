#!/usr/bin/env bash
set -euo pipefail
mkdir -p SoundAtlas/qa-fast
APK=SoundAtlas/app/build/outputs/apk/debug/app-debug.apk
adb wait-for-device
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb install -r "$APK"
adb shell am start -W -n com.night.soundatlas/.MainActivity
sleep 8
W=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x[0-9]*/\1/p'|head -1)
H=$(adb shell wm size | sed -n 's/.*x\([0-9]*\)/\1/p'|head -1)
D=$(adb shell wm density | sed -n 's/.*: \([0-9]*\)/\1/p'|head -1)
test -n "$W" && test -n "$H" && test -n "$D"
echo "Resolution $W x $H density $D"
recover_modal() {
    # Github shared runners sometimes freeze Pixel System UI independently of our app.
    timeout 22s adb shell uiautomator dump /sdcard/sound-atlas-ui.xml >/dev/null 2>&1 || true
    local xml
    xml=$(timeout 12s adb shell cat /sdcard/sound-atlas-ui.xml 2>/dev/null || true)
    if [[ "$xml" == *"Got it"* ]]; then
        adb shell input tap $((W*84/100)) $((H/4))
        sleep 3
    elif [[ "$xml" == *"System UI"* && "$xml" == *"Wait"* ]]; then
        adb shell input tap $((W*33/100)) $((H*53/100))
        sleep 6
    fi
}
capture() {
  local target="$1"
  recover_modal
  adb exec-out screencap -p > "SoundAtlas/qa-fast/$target"
  test "$(wc -c < "SoundAtlas/qa-fast/$target")" -gt 30000
  file "SoundAtlas/qa-fast/$target"
}
tap_dp() {
  adb shell input tap "$(( $1 * W / 100 ))" "$(( H - $2 * D / 160 ))"
  sleep 2
}
adb shell pidof com.night.soundatlas
capture 01-cathedral.png
tap_dp 10 65
capture 02-world-picker.png
if cmp -s SoundAtlas/qa-fast/01-cathedral.png SoundAtlas/qa-fast/02-world-picker.png; then
  echo "World picker did not visibly open"; exit 1
fi
# Ten preset worlds: pick right card in first row.
tap_dp 75 435
capture 03-world-switch.png
tap_dp 30 65
capture 04-sound-library.png
if cmp -s SoundAtlas/qa-fast/03-world-switch.png SoundAtlas/qa-fast/04-sound-library.png; then
  echo "Sound library did not visibly open"; exit 1
fi
tap_dp 26 425
capture 05-source-inspector.png
tap_dp 91 263
tap_dp 90 65
capture 06-explore.png
tap_dp 90 65
adb shell input tap $(( W - 95 * D / 160 )) $((79 * D / 160))
sleep 2
capture 07-atmosphere.png
if cmp -s SoundAtlas/qa-fast/06-explore.png SoundAtlas/qa-fast/07-atmosphere.png; then
  echo "Climate editor did not visibly open"; exit 1
fi
adb shell pidof com.night.soundatlas
adb logcat -d -s AndroidRuntime:E > SoundAtlas/qa-fast/crashes.txt
if grep -A 8 'FATAL EXCEPTION' SoundAtlas/qa-fast/crashes.txt | grep -q 'com.night.soundatlas'; then
    echo "App fatal exception in emulator"; exit 1
fi
echo "SOUND_ATLAS_REAL_DEVICE_UI_SMOKE_PASSED"
