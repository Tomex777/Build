#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
APK="night-screenshot-apk/app-debug.apk"
OUT="night-screenshots"

mkdir -p "$OUT"
: > "$OUT/_capture-warnings.txt"

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

dismiss_quickstep() {
  adb shell uiautomator dump /sdcard/night-shot.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/night-shot.xml > /tmp/night-shot.xml 2>/dev/null || true
  if grep -qi "Quickstep isn't responding" /tmp/night-shot.xml 2>/dev/null; then
    python3 - <<'PY' >/tmp/quickstep-tap.txt || true
import re
import xml.etree.ElementTree as ET
try:
    root = ET.parse("/tmp/night-shot.xml").getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter("node"):
    if node.attrib.get("text") == "Close app" or node.attrib.get("resource-id") == "android:id/aerr_close":
        nums = [int(x) for x in re.findall(r"\\d+", node.attrib.get("bounds", ""))]
        if len(nums) == 4:
            print((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
            raise SystemExit(0)
raise SystemExit(1)
PY
    if [ -s /tmp/quickstep-tap.txt ]; then
      read -r x y </tmp/quickstep-tap.txt
      adb shell input tap "$x" "$y" || true
      sleep 2
    fi
  fi
}

capture() {
  local name="$1"
  local activity="$2"
  shift 2

  adb shell am force-stop "$PACKAGE"
  if ! adb shell am start -W -n "$PACKAGE/$activity" "$@" >"/tmp/am-start-$name.txt" 2>&1; then
    echo "Could not launch $name ($activity)" >> "$OUT/_capture-warnings.txt"
    return 1
  fi

  sleep 3
  dismiss_quickstep
  sleep 1

  if ! adb exec-out screencap -p > "$OUT/$name.png"; then
    echo "Could not screenshot $name ($activity)" >> "$OUT/_capture-warnings.txt"
    rm -f "$OUT/$name.png"
    return 1
  fi

  adb shell uiautomator dump "/sdcard/$name.xml" >/dev/null 2>&1 || true
  adb exec-out cat "/sdcard/$name.xml" > "$OUT/$name.xml" 2>/dev/null || true
  return 0
}

capture_try() {
  local name="$1"
  local activity="$2"
  shift 2
  capture "$name" "$activity" "$@" || true
}

capture_try "01-main-chats" ".MainTabsPreviewActivity" --es tab chats
capture_try "02-main-library" ".MainTabsPreviewActivity" --es tab library
capture_try "03-main-you" ".MainTabsPreviewActivity" --es tab you
capture_try "04-main-settings" ".MainTabsPreviewActivity" --es tab settings

capture_try "05-chat-default" ".ChatPreviewActivity"
capture_try "06-chat-attachments" ".ChatPreviewActivity" --ez attachments true
capture_try "07-chat-emoji" ".ChatPreviewActivity" --ez emoji true
capture_try "08-chat-menu" ".ChatPreviewActivity" --ez menu true
capture_try "09-chat-media" ".ChatPreviewActivity" --es mode media
capture_try "10-chat-audio" ".ChatPreviewActivity" --es mode audio
capture_try "11-chat-voice" ".ChatPreviewActivity" --es mode voice
capture_try "12-chat-documents" ".ChatPreviewActivity" --es mode docs
capture_try "13-chat-rich" ".ChatPreviewActivity" --es mode rich
capture_try "14-rich-page-one" ".RichBubblePreviewActivity" --ei page 1
capture_try "15-rich-page-two" ".RichBubblePreviewActivity" --ei page 2
capture_try "16-chat-approved-rich" ".ChatPreviewActivity" --es mode approved-rich
capture_try "17-chat-extension" ".ChatPreviewActivity" --es mode extension
capture_try "18-chat-extension-config" ".ChatPreviewActivity" --es mode extension-config
capture_try "19-chat-browser" ".ChatPreviewActivity" --es mode browser
capture_try "20-chat-message-blocks" ".ChatPreviewActivity" --es mode blocks
capture_try "21-chat-utility" ".ChatPreviewActivity" --es mode utility

capture_try "22-providers" ".NightProvidersPreviewActivity"
capture_try "23-memory-summary" ".NightMemoryPreviewActivity"
capture_try "24-extensions" ".NightExtensionsPreviewActivity"
capture_try "25-integrations" ".NightIntegrationsPreviewActivity"
capture_try "26-mcp-servers" ".NightMcpServersPreviewActivity"
capture_try "27-live-voice" ".NightLiveVoicePreviewActivity"

capture_try "28-media-viewer" ".MediaViewerPreviewActivity"
capture_try "29-media-editor" ".MediaEditorPreviewActivity"
capture_try "30-pdf-viewer" ".PdfViewerPreviewActivity"
capture_try "31-pdf-editor" ".PdfEditorPreviewActivity"
capture_try "32-mihon-reader" ".MihonReaderPreviewActivity"

png_count="$(find "$OUT" -maxdepth 1 -name '*.png' | wc -l | tr -d ' ')"
{
  echo "Night Android screenshot pack"
  echo "branch=night-groq-key-pool-ci"
  echo "commit=${GITHUB_SHA:-unknown}"
  echo "device=Android 16 / API 36 / 709x1536 @ 240 dpi"
  echo "requestedScreens=32"
  echo "pngCount=$png_count"
} > "$OUT/README.txt"

if [ "$png_count" -eq 0 ]; then
  echo "No Night screenshots were captured." >&2
  exit 1
fi
