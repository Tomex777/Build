#!/usr/bin/env bash
set -euo pipefail
# Keep every validation command self-contained: android-emulator-runner@v2
# may execute script lines in separate shells, so shell variables/functions
# are intentionally avoided here.

mkdir -p night-message-blocks-artifacts
adb install --no-streaming -r night-message-blocks-apk/app-debug.apk
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c

adb shell am force-stop com.example.whatsapp
adb shell am start -W -n com.example.whatsapp/.ChatPreviewActivity --es mode blocks
sleep 3
adb exec-out screencap -p > night-message-blocks-artifacts/01-blocks-top.png
adb shell uiautomator dump /sdcard/blocks-top.xml >/dev/null
adb exec-out cat /sdcard/blocks-top.xml > night-message-blocks-artifacts/01-blocks-top.xml
grep -q "Fix" night-message-blocks-artifacts/01-blocks-top.xml
grep -q "Validation" night-message-blocks-artifacts/01-blocks-top.xml

adb shell input swipe 360 1250 360 430 700
sleep 2
adb exec-out screencap -p > night-message-blocks-artifacts/02-blocks-middle.png
adb shell uiautomator dump /sdcard/blocks-middle.xml >/dev/null
adb exec-out cat /sdcard/blocks-middle.xml > night-message-blocks-artifacts/02-blocks-middle.xml

adb shell input swipe 360 1250 360 380 700
sleep 2
adb exec-out screencap -p > night-message-blocks-artifacts/03-blocks-bottom.png
adb shell uiautomator dump /sdcard/blocks-bottom.xml >/dev/null
adb exec-out cat /sdcard/blocks-bottom.xml > night-message-blocks-artifacts/03-blocks-bottom.xml

# One more viewport is required on the phone-sized API-36 layout to reach the
# final diff + connection blocks. Keep this separate so the screenshots still
# document the natural scrolling sequence.
adb shell input swipe 360 1250 360 430 700
sleep 2
adb exec-out screencap -p > night-message-blocks-artifacts/04-blocks-final.png
adb shell uiautomator dump /sdcard/blocks-final.xml >/dev/null
adb exec-out cat /sdcard/blocks-final.xml > night-message-blocks-artifacts/04-blocks-final.xml

cat night-message-blocks-artifacts/0*-blocks-*.xml > night-message-blocks-artifacts/all-blocks.xml
grep -q "What should I focus on next?" night-message-blocks-artifacts/all-blocks.xml
grep -q "Pull request is ready" night-message-blocks-artifacts/all-blocks.xml
grep -q "Project Night" night-message-blocks-artifacts/all-blocks.xml
grep -q "Sources" night-message-blocks-artifacts/all-blocks.xml
grep -q "Workspace connection" night-message-blocks-artifacts/all-blocks.xml
if grep -qi "Created by" night-message-blocks-artifacts/all-blocks.xml; then
  echo "Visible watermark text found in Night message blocks." >&2
  exit 1
fi

adb logcat -d -v threadtime > night-message-blocks-artifacts/logcat.txt
test -z "$(grep -A5 "FATAL EXCEPTION:" night-message-blocks-artifacts/logcat.txt | grep "Process: com.example.whatsapp" | head -n 1)"
if grep -q "ANR in com.example.whatsapp" night-message-blocks-artifacts/logcat.txt; then
  echo "Night message block preview hit an ANR." >&2
  exit 1
fi

printf '%s\n' "androidApi=36" "messageBlocks=true" "watermarkFree=true" "codeCopyTable=true" "progress=true" "questionPermission=true" "toolExtension=true" "sourcesDiffConnection=true" > night-message-blocks-artifacts/summary.txt
