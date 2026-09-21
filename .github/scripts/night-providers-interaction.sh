#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.NightProvidersPreviewActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-providers-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_providers_uia.py <<'PY'
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

def find_attr(attr, value):
    return next((n for n in nodes if n.attrib.get(attr) == value), None)

if mode == "text":
    if find_attr("text", value) is None:
        raise SystemExit(2)
    print("found")
elif mode == "click_desc":
    node = find_attr("content-desc", value)
    if node is None:
        raise SystemExit(2)
    parents = {child: parent for parent in root.iter() for child in parent}
    current = node
    while current is not None and current.attrib.get("clickable") != "true":
        current = parents.get(current)
    target = current if current is not None else node
    box = bounds(target)
    if box is None:
        raise SystemExit(2)
    x1, y1, x2, y2 = box
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
else:
    raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-providers.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/night-providers.xml > /tmp/window.xml
}

assert_text() {
  local wanted="$1"
  local attempt
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_providers_uia.py text "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  echo "Missing Provider UI text after retries: $wanted" >&2
  cp /tmp/window.xml "$OUT/missing-text.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/missing-text.png" 2>/dev/null || true
  return 1
}

tap_desc() {
  local wanted="$1"
  local attempt
  for attempt in $(seq 1 10); do
    refresh_ui
    if coords="$(python3 /tmp/night_providers_uia.py click_desc "$wanted" 2>/dev/null)"; then
      read -r x y <<<"$coords"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 0.5
  done
  echo "Missing Provider UI control after retries: $wanted" >&2
  return 1
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during Provider Admin validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
    echo "Night hit an ANR during Provider Admin validation." >&2
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

echo "STEP: provider admin fits phone width"
assert_text "AI & providers"
assert_text "Groq"
assert_text "Groq primary"
assert_text "Llama 3.3 70B"
adb exec-out screencap -p > "$OUT/01-provider-phone-width.png"

echo "STEP: model actions use compact overflow menu"
tap_desc "Model actions"
assert_text "Disable model"
assert_text "Edit model"
assert_text "Delete model"
adb exec-out screencap -p > "$OUT/02-model-actions-menu.png"

assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "phoneWidth=709x1536@240dpi" \
  "providerScreen=true" \
  "compactModelActions=true" \
  "overflowMenu=true" > "$OUT/summary.txt"
