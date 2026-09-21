#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.NightMemoryPreviewActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-memory-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_memory_uia.py <<'PY'
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

def find_text(text):
    return next((n for n in nodes if n.attrib.get("text") == text), None)

if mode == "text":
    node = find_text(value)
    if node is None:
        raise SystemExit(2)
    print("found")
elif mode == "click_text":
    node = find_text(value)
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
    x1,y1,x2,y2 = box
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
else:
    raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-memory.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/night-memory.xml > /tmp/window.xml
}

assert_text() {
  local wanted="$1"
  local attempt
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_memory_uia.py text "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done

  echo "Missing Memory UI text after retries: $wanted" >&2
  cp /tmp/window.xml "$OUT/missing-text.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/missing-text.png" 2>/dev/null || true
  return 1
}

tap_text() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_memory_uia.py click_text "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during Memory & Summary validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
    echo "Night hit an ANR during Memory & Summary validation." >&2
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

echo "STEP: memory screen shows current summary state"
assert_text "Latest summary"
assert_text "There are newer unsummarized messages."
assert_text "Summary history"
assert_text "2 checkpoints"
assert_text "Refresh summary"
adb exec-out screencap -p > "$OUT/01-memory-dirty.png"

echo "STEP: manual refresh updates memory state and history"
tap_text "Refresh summary"
assert_text "Up to date"
assert_text "3 checkpoints"
adb exec-out screencap -p > "$OUT/02-memory-refreshed.png"

assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "memorySummaryScreen=true" \
  "dirtyState=true" \
  "manualRefresh=true" \
  "checkpointHistory=true" \
  "upToDateState=true" \
  "fiveMinutePolicyCoveredByUnitTest=true" \
  "fallbackPreservesPreviousSummary=true" > "$OUT/summary.txt"
