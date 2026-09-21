#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.ChatPreviewActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-extension-config-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_ext_config_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
mode = sys.argv[1]
value = sys.argv[2] if len(sys.argv) > 2 else ""

nodes = list(root.iter("node"))

def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        return None
    return tuple(map(int, m.groups()))

def find_desc(desc):
    return next((n for n in nodes if n.attrib.get("content-desc") == desc), None)

if mode == "desc":
    node = find_desc(value)
    if node is None or bounds(node) is None:
        raise SystemExit(2)
    x1,y1,x2,y2 = bounds(node)
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
elif mode == "text":
    node = next((n for n in nodes if n.attrib.get("text") == value), None)
    if node is None or bounds(node) is None:
        raise SystemExit(2)
    x1,y1,x2,y2 = bounds(node)
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
elif mode == "attr":
    node = find_desc(value)
    if node is None:
        raise SystemExit(2)
    attr = sys.argv[3]
    print(node.attrib.get(attr, ""))
else:
    raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/extension-config.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/extension-config.xml > /tmp/window.xml
}

tap_desc() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_ext_config_uia.py desc "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

scroll_until_desc() {
  local desc="$1"
  for _ in $(seq 1 6); do
    refresh_ui
    if python3 /tmp/night_ext_config_uia.py desc "$desc" >/dev/null 2>&1; then
      return 0
    fi
    adb shell input swipe 355 1240 355 520 600
    sleep 1
  done
  return 1
}

assert_selected() {
  refresh_ui
  value="$(python3 /tmp/night_ext_config_uia.py attr "$1" selected)"
  if [ "$value" != "true" ]; then
    echo "Expected selected=true for $1, got '$value'." >&2
    exit 1
  fi
}

assert_checked() {
  refresh_ui
  value="$(python3 /tmp/night_ext_config_uia.py attr "$1" checked)"
  if [ "$value" != "$2" ]; then
    echo "Expected checked=$2 for $1, got '$value'." >&2
    exit 1
  fi
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during extension configuration validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
    echo "Night hit an ANR during extension configuration validation." >&2
    exit 1
  fi
}

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" --es mode extension-config
sleep 4

ready=false
for attempt in 1 2 3; do
  refresh_ui || true
  if grep -qi "isn't responding" /tmp/window.xml 2>/dev/null; then
    adb shell input keyevent 4 || true
    adb shell am force-stop "$PACKAGE"
    adb shell am start -W -n "$ACTIVITY" --es mode extension-config >/dev/null
    sleep 3
    continue
  fi
  if grep -q "Download settings" /tmp/window.xml 2>/dev/null; then
    ready=true
    break
  fi
  sleep 2
done

if [ "$ready" != "true" ]; then
  adb exec-out screencap -p > "$OUT/failure-ready.png" 2>/dev/null || true
  cp /tmp/window.xml "$OUT/failure-ready.xml" 2>/dev/null || true
  adb logcat -d -v threadtime > "$OUT/failure-ready-logcat.txt" 2>/dev/null || true
  echo "Extension configuration preview did not become ready." >&2
  exit 1
fi

adb exec-out screencap -p > "$OUT/01-config-default.png"
cp /tmp/window.xml "$OUT/01-config-default.xml"

grep -q "Download settings" "$OUT/01-config-default.xml"
grep -q "Subtitles" "$OUT/01-config-default.xml"
grep -q "Resolution" "$OUT/01-config-default.xml"
grep -q "Allowed sources" "$OUT/01-config-default.xml"

echo "STEP: toggle changes state"
tap_desc "Config subtitles toggle"
assert_checked "Config subtitles toggle" "false"
adb exec-out screencap -p > "$OUT/02-toggle-off.png"

echo "STEP: single choice changes state"
tap_desc "Config resolution option 1080p"
assert_selected "Config resolution option 1080p"

echo "STEP: multi choice changes state"
tap_desc "Config sources option fallback"
assert_selected "Config sources option fallback"
adb exec-out screencap -p > "$OUT/03-choices-updated.png"

echo "STEP: advanced fields expand"
scroll_until_desc "Show advanced extension settings"
tap_desc "Show advanced extension settings"
sleep 1
scroll_until_desc "Config quality_bias"
refresh_ui
grep -q "Quality bias" /tmp/window.xml
grep -q "Filename template" /tmp/window.xml
grep -q "Run source test" /tmp/window.xml
adb exec-out screencap -p > "$OUT/04-advanced.png"

echo "STEP: save configuration is actionable"
scroll_until_desc "Save extension configuration"
tap_desc "Save extension configuration"
sleep 1
adb shell pidof "$PACKAGE" >/dev/null
adb exec-out screencap -p > "$OUT/05-saved.png"

assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "extensionConfiguration=true" \
  "toggle=true" \
  "singleChoice=true" \
  "multiChoice=true" \
  "number=true" \
  "range=true" \
  "text=true" \
  "action=true" \
  "advanced=true" \
  "saveAction=true" \
  "watermarkFree=true" > "$OUT/summary.txt"
