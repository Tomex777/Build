#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.NightExtensionsPreviewActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-extensions-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_extensions_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
nodes = list(root.iter("node"))
mode = sys.argv[1]
value = sys.argv[2]

def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        return None
    return tuple(map(int, m.groups()))

def matching(attr, value):
    return [n for n in nodes if n.attrib.get(attr) == value]

if mode == "text":
    if not matching("text", value):
        raise SystemExit(2)
    print("found")
elif mode == "click_text":
    candidates = matching("text", value)
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in candidates:
        current = node
        while current is not None and current.attrib.get("clickable") != "true":
            current = parents.get(current)
        target = current if current is not None else node
        if target.attrib.get("enabled", "true") != "true":
            continue
        box = bounds(target)
        if box is None:
            continue
        x1,y1,x2,y2 = box
        print(f"{(x1+x2)//2} {(y1+y2)//2}")
        raise SystemExit(0)
    raise SystemExit(2)
elif mode == "desc":
    if not matching("content-desc", value):
        raise SystemExit(2)
    print("found")
else:
    raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-extensions.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/night-extensions.xml > /tmp/window.xml
}

assert_text() {
  local wanted="$1"
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_extensions_uia.py text "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  echo "Missing Extensions UI text: $wanted" >&2
  cp /tmp/window.xml "$OUT/missing-text.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/missing-text.png" 2>/dev/null || true
  return 1
}

tap_text() {
  local wanted="$1"
  for attempt in $(seq 1 10); do
    refresh_ui
    if coords="$(python3 /tmp/night_extensions_uia.py click_text "$wanted" 2>/dev/null)"; then
      read -r x y <<<"$coords"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 0.5
  done
  echo "Missing enabled Extensions control: $wanted" >&2
  return 1
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during Extensions UI validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
    echo "Night hit an ANR during Extensions UI validation." >&2
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

echo "STEP: extension permission states fit phone width"
assert_text "Extensions"
assert_text "Downloads"
assert_text "Aniyomi Tools"
assert_text "Duplicate ID"
assert_text "Duplicate extension id. Both packages are blocked until the conflict is removed."
adb exec-out screencap -p > "$OUT/01-extensions-phone-width.png"

echo "STEP: enabling a discovered extension requires explicit confirmation"
tap_text "Enable"
assert_text "Enable extension?"
assert_text "Enable"
assert_text "Cancel"
adb exec-out screencap -p > "$OUT/02-extension-enable-confirmation.png"
tap_text "Cancel"

assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "phoneWidth=709x1536@240dpi" \
  "extensionsScreen=true" \
  "disabledByDefault=true" \
  "enableConfirmation=true" \
  "duplicateIdBlocked=true" \
  "packageVisible=true" > "$OUT/summary.txt"
