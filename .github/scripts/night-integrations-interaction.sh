#!/usr/bin/env bash
set -euo pipefail

APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.NightIntegrationsPreviewActivity"
OUT="night-integrations-artifacts"

mkdir -p "$OUT"
adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c

cat >/tmp/night_integrations_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

mode, value = sys.argv[1], sys.argv[2]
attr = "content-desc" if mode.startswith("desc") else "text"
root = ET.parse("/tmp/window.xml").getroot()

for node in root.iter("node"):
    actual = node.attrib.get(attr, "")
    matched = actual == value
    if matched:
        nums = [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds", ""))]
        if mode.endswith("_click") and len(nums) == 4:
            print((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
        raise SystemExit(0)
raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-integrations.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/night-integrations.xml > /tmp/window.xml
}

assert_text() {
  local wanted="$1"
  for _ in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_integrations_uia.py text "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Missing Integrations UI text: $wanted" >&2
  cp /tmp/window.xml "$OUT/failure-window.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/failure.png" || true
  exit 1
}

tap_desc() {
  local wanted="$1"
  local coords
  for _ in $(seq 1 10); do
    refresh_ui
    if coords="$(python3 /tmp/night_integrations_uia.py desc_click "$wanted" 2>/dev/null)"; then
      adb shell input tap "${coords% *}" "${coords#* }"
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo "Missing Integrations UI control: $wanted" >&2
  exit 1
}

assert_no_crash() {
  adb logcat -d > "$OUT/logcat-latest.txt" || true
  if adb logcat -d -v brief | grep -A4 "FATAL EXCEPTION:" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during unified Integrations validation." >&2
    exit 1
  fi
  adb shell pidof "$PACKAGE" >/dev/null
}

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 3

echo "STEP: unified integration list"
assert_text "Integrations"
assert_text "AnimePahe"
assert_text "EXT"
assert_text "Music"
assert_text "GitHub"
assert_text "MCP"
assert_text "Connected • 12 tools"
assert_text "Encrypted token saved"
adb exec-out screencap -p > "$OUT/01-integrations.png"
assert_no_crash

echo "STEP: MCP add dialog remains available from shared shell"
tap_desc "Add MCP server"
assert_text "Add MCP server"
assert_text "Server name"
assert_text "Streamable HTTP endpoint"
adb exec-out screencap -p > "$OUT/02-add-mcp.png"
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "STEP: MCP edit action remains backend-specific"
tap_desc "Edit MCP server"
assert_text "Edit MCP server"
assert_text "GitHub"
adb exec-out screencap -p > "$OUT/03-edit-mcp.png"
assert_no_crash

cat > "$OUT/summary.txt" <<'EOF'
integrationsScreen=true
extensionMarker=true
mcpMarker=true
mixedList=true
mcpAddDialog=true
mcpEditDialog=true
EOF
