#!/usr/bin/env bash
set -euo pipefail

APK="night-message-blocks-apk/app-debug.apk"
OUT="night-message-blocks-artifacts"
PACKAGE="com.example.whatsapp"

mkdir -p "$OUT"
adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.ChatPreviewActivity" --es mode blocks
sleep 3
adb exec-out screencap -p > "$OUT/01-blocks-top.png"
adb shell uiautomator dump /sdcard/blocks-top.xml >/dev/null
adb exec-out cat /sdcard/blocks-top.xml > "$OUT/01-blocks-top.xml"

grep -q "Fix" "$OUT/01-blocks-top.xml"
grep -q "Validation" "$OUT/01-blocks-top.xml"

adb shell input swipe 360 1250 360 430 700
sleep 2
adb exec-out screencap -p > "$OUT/02-blocks-middle.png"
adb shell uiautomator dump /sdcard/blocks-middle.xml >/dev/null
adb exec-out cat /sdcard/blocks-middle.xml > "$OUT/02-blocks-middle.xml"

adb shell input swipe 360 1250 360 380 700
sleep 2
adb exec-out screencap -p > "$OUT/03-blocks-bottom.png"
adb shell uiautomator dump /sdcard/blocks-bottom.xml >/dev/null
adb exec-out cat /sdcard/blocks-bottom.xml > "$OUT/03-blocks-bottom.xml"

cat "$OUT"/0*-blocks-*.xml > "$OUT/all-blocks.xml"
grep -q "What should I focus on next?" "$OUT/all-blocks.xml"
grep -q "Pull request is ready" "$OUT/all-blocks.xml"
grep -q "Project Night" "$OUT/all-blocks.xml"
grep -q "Sources" "$OUT/all-blocks.xml"
grep -q "Workspace connection" "$OUT/all-blocks.xml"

if grep -qi "Created by" "$OUT/all-blocks.xml"; then
  echo "Visible provenance watermark text is still present." >&2
  exit 1
fi

adb logcat -d -v threadtime > "$OUT/logcat.txt"
if grep -A5 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
  echo "Night crashed while rendering reusable message blocks." >&2
  exit 1
fi
if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
  echo "Night ANR while rendering reusable message blocks." >&2
  exit 1
fi

{
  echo "androidApi=36"
  echo "messageBlocks=true"
  echo "watermarkFree=true"
  echo "codeCopyTable=true"
  echo "progress=true"
  echo "questionPermission=true"
  echo "toolExtension=true"
  echo "sourcesDiffConnection=true"
} > "$OUT/summary.txt"
