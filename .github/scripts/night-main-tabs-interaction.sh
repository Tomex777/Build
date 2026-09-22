#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.MainTabsPreviewActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-main-tabs-artifacts"
mkdir -p "$OUT"

cat >/tmp/night_main_tabs_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
nodes = list(root.iter("node"))

def bounds(node):
    nums = [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds", ""))]
    return tuple(nums) if len(nums) == 4 else None

mode = sys.argv[1]
value = sys.argv[2]

if mode == "desc":
    node = next((n for n in nodes if n.attrib.get("content-desc") == value), None)
elif mode == "text":
    node = next((n for n in nodes if n.attrib.get("text") == value), None)
else:
    raise SystemExit(2)

if node is None:
    raise SystemExit(2)
box = bounds(node)
if box is None:
    raise SystemExit(2)
print(*box)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-main-tabs.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/night-main-tabs.xml >/tmp/window.xml
}

launch_tab() {
  local tab="$1"
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$ACTIVITY" --es tab "$tab" >/dev/null
  sleep 3
  refresh_ui
}

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c

echo "STEP: three-tab shell and FAB clearance"
launch_tab chats

python3 /tmp/night_main_tabs_uia.py desc "Chats" >/dev/null
python3 /tmp/night_main_tabs_uia.py desc "Library" >/dev/null
python3 /tmp/night_main_tabs_uia.py desc "You" >/dev/null

if grep -q 'content-desc="Calls"' /tmp/window.xml ||
   grep -q 'content-desc="Communities"' /tmp/window.xml; then
  echo "Night exposed a fourth bottom-navigation tab." >&2
  exit 1
fi

read -r fx1 fy1 fx2 fy2 <<<"$(python3 /tmp/night_main_tabs_uia.py desc "New chat FAB")"
read -r tx1 ty1 tx2 ty2 <<<"$(python3 /tmp/night_main_tabs_uia.py desc "You")"

if [ "$fy2" -ge "$ty1" ]; then
  echo "New chat FAB overlaps the bottom navigation: FAB bottom=$fy2, nav top=$ty1" >&2
  adb exec-out screencap -p >"$OUT/failure-fab-overlap.png"
  exit 1
fi

# Edge-to-edge is enabled, but ordinary content must start below Android's
# status/notification bar instead of being obscured by it.
read -r hx1 hy1 hx2 hy2 <<<"$(python3 /tmp/night_main_tabs_uia.py text "Night")"
if [ "$hy1" -lt 32 ]; then
  echo "Night header overlaps the Android status bar: header top=$hy1" >&2
  adb exec-out screencap -p >"$OUT/failure-status-overlap.png"
  exit 1
fi

adb exec-out screencap -p >"$OUT/01-chats-three-tabs.png"

echo "STEP: Library tab"
launch_tab library
python3 /tmp/night_main_tabs_uia.py desc "Chats" >/dev/null
python3 /tmp/night_main_tabs_uia.py desc "Library" >/dev/null
python3 /tmp/night_main_tabs_uia.py desc "You" >/dev/null
adb exec-out screencap -p >"$OUT/02-library.png"

echo "STEP: You tab"
launch_tab you
python3 /tmp/night_main_tabs_uia.py desc "Chats" >/dev/null
python3 /tmp/night_main_tabs_uia.py desc "Library" >/dev/null
python3 /tmp/night_main_tabs_uia.py desc "You" >/dev/null
adb exec-out screencap -p >"$OUT/03-you.png"

adb logcat -d -v threadtime >"$OUT/logcat.txt"
if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
  echo "Night crashed during main tab regression." >&2
  exit 1
fi

printf '%s\n' \
  "androidApi=36" \
  "bottomTabs=Chats,Library,You" \
  "fabClearOfBottomNav=true" \
  "statusBarInset=true" >"$OUT/summary.txt"
