#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.NightMcpServersPreviewActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-mcp-servers-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_mcp_uia.py <<'PY'
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
    x1,y1,x2,y2 = box
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
else:
    raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-mcp.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/night-mcp.xml > /tmp/window.xml
}

assert_text() {
  local wanted="$1"
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_mcp_uia.py text "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  echo "Missing MCP UI text: $wanted" >&2
  cp /tmp/window.xml "$OUT/missing-text.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/missing-text.png" 2>/dev/null || true
  return 1
}

tap_desc() {
  local wanted="$1"
  for attempt in $(seq 1 10); do
    refresh_ui
    if coords="$(python3 /tmp/night_mcp_uia.py click_desc "$wanted" 2>/dev/null)"; then
      read -r x y <<<"$coords"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 0.5
  done
  echo "Missing MCP control: $wanted" >&2
  return 1
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during MCP UI validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
    echo "Night hit an ANR during MCP UI validation." >&2
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

echo "STEP: MCP server states fit phone width"
assert_text "MCP servers"
assert_text "GitHub MCP"
assert_text "Connected • 12 tools"
assert_text "Encrypted token saved"
assert_text "Files MCP"
assert_text "Disconnected • Connection refused"
assert_text "Local tools"
assert_text "Disabled"
adb exec-out screencap -p > "$OUT/01-mcp-phone-width.png"

echo "STEP: add server dialog is usable"
tap_desc "Add MCP server"
assert_text "Add MCP server"
assert_text "Server name"
assert_text "Streamable HTTP endpoint"
assert_text "Bearer token (optional)"
assert_text "Connect automatically"
adb exec-out screencap -p > "$OUT/02-mcp-add-dialog.png"

assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "phoneWidth=709x1536@240dpi" \
  "mcpServerSettings=true" \
  "connectedState=true" \
  "errorState=true" \
  "disabledState=true" \
  "encryptedTokenIndicator=true" \
  "addDialog=true" > "$OUT/summary.txt"
