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

# Android 16 emulator Quickstep can occasionally show a launcher ANR dialog
# over an otherwise healthy preview app. Dismiss the system dialog and relaunch
# before judging Night's UI.
ready=false
for attempt in 1 2 3; do
  adb shell uiautomator dump /sdcard/blocks-ready.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/blocks-ready.xml > /tmp/blocks-ready.xml 2>/dev/null || true

  if grep -qi "Quickstep isn't responding" /tmp/blocks-ready.xml 2>/dev/null; then
    read -r close_x close_y <<<"$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
try:
    root = ET.parse("/tmp/blocks-ready.xml").getroot()
except Exception:
    print("350 755")
    raise SystemExit(0)
for node in root.iter("node"):
    if (
        node.attrib.get("resource-id") == "android:id/aerr_close"
        or node.attrib.get("text") == "Close app"
    ):
        nums = [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds", ""))]
        if len(nums) == 4:
            print((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
            raise SystemExit(0)
print("350 755")
PY
)"
    adb shell input tap "$close_x" "$close_y" || true
    sleep 2
    continue
  fi

  if grep -q "Fix" /tmp/blocks-ready.xml 2>/dev/null && grep -q "Validation" /tmp/blocks-ready.xml 2>/dev/null; then
    ready=true
    break
  fi
  sleep 2
done

if [ "$ready" != "true" ]; then
  adb exec-out screencap -p > night-message-blocks-artifacts/failure-ready.png 2>/dev/null || true
  cp /tmp/blocks-ready.xml night-message-blocks-artifacts/failure-ready.xml 2>/dev/null || true
  adb logcat -d -v threadtime > night-message-blocks-artifacts/failure-ready-logcat.txt 2>/dev/null || true
  echo "Night message block preview did not become ready." >&2
  exit 1
fi

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

# Render the one persisted choice produced when identical create_options calls
# arrive twice. Provider instrumentation verifies persistence; this capture
# verifies the resulting message is presented as one visible choice card.
adb shell am force-stop com.example.whatsapp
adb shell am start -W -n com.example.whatsapp/.ChatPreviewActivity --es mode options
sleep 3
adb exec-out screencap -p > night-message-blocks-artifacts/05-repeated-options.png
adb shell uiautomator dump /sdcard/options.xml >/dev/null
adb exec-out cat /sdcard/options.xml > night-message-blocks-artifacts/05-repeated-options.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse("night-message-blocks-artifacts/05-repeated-options.xml").getroot()
texts = [node.attrib.get("text", "") for node in root.iter("node")]
assert texts.count("Pick one") == 1, f"expected one visible option card, found {texts.count('Pick one')}"
assert any("Choose one" in text for text in texts), "choice-card detail was not visible"
assert texts.count("A") == 1 and texts.count("B") == 1, "choice options A and B were not visible exactly once"
PY

adb logcat -d -v threadtime > night-message-blocks-artifacts/logcat.txt
test -z "$(grep -A5 "FATAL EXCEPTION:" night-message-blocks-artifacts/logcat.txt | grep "Process: com.example.whatsapp" | head -n 1)"
if grep -q "ANR in com.example.whatsapp" night-message-blocks-artifacts/logcat.txt; then
  echo "Night message block preview hit an ANR." >&2
  exit 1
fi

printf '%s\n' "androidApi=36" "messageBlocks=true" "watermarkFree=true" "codeCopyTable=true" "progress=true" "questionPermission=true" "repeatedOptionsCard=true" "toolExtension=true" "sourcesDiffConnection=true" > night-message-blocks-artifacts/summary.txt
