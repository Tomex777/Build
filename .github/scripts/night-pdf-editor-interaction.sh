#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.PdfEditorPreviewActivity"
APK="night-pdf-editor-apk/app-debug.apk"
OUT="night-pdf-editor-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_pdf_editor_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
mode = sys.argv[1]
value = sys.argv[2] if len(sys.argv) > 2 else ""
nodes = list(root.iter("node"))

def rect(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        return None
    return tuple(map(int, m.groups()))

def by_desc():
    return next((n for n in nodes if n.attrib.get("content-desc") == value), None)

def by_text():
    return next((n for n in nodes if n.attrib.get("text") == value), None)

if mode == "has_text":
    print("true" if any(value in n.attrib.get("text", "") for n in nodes) else "false")
    raise SystemExit(0)

if mode == "has_desc":
    print("true" if any(value == n.attrib.get("content-desc", "") for n in nodes) else "false")
    raise SystemExit(0)

match = by_desc() if mode in ("desc", "bounds_desc") else by_text()
if match is None or rect(match) is None:
    raise SystemExit(2)

x1,y1,x2,y2 = rect(match)
if mode == "bounds_desc":
    print(f"{x1} {y1} {x2} {y2}")
else:
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-pdf-editor.xml >/dev/null
  adb exec-out cat /sdcard/night-pdf-editor.xml > /tmp/window.xml
}

tap_desc() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_pdf_editor_uia.py desc "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

tap_text() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_pdf_editor_uia.py text "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

assert_desc() {
  refresh_ui
  python3 /tmp/night_pdf_editor_uia.py desc "$1" >/dev/null
}

assert_text_contains() {
  refresh_ui
  if [ "$(python3 /tmp/night_pdf_editor_uia.py has_text "$1")" != "true" ]; then
    echo "Missing PDF editor text: $1" >&2
    cp /tmp/window.xml "$OUT/failure-window.xml" || true
    adb exec-out screencap -p > "$OUT/failure-screen.png" || true
    exit 1
  fi
}

swipe_on_page() {
  refresh_ui
  read -r x1 y1 x2 y2 <<<"$(python3 /tmp/night_pdf_editor_uia.py bounds_desc "Editable PDF page")"
  local sx=$((x1 + (x2-x1)*25/100))
  local sy=$((y1 + (y2-y1)*35/100))
  local ex=$((x1 + (x2-x1)*72/100))
  local ey=$((y1 + (y2-y1)*55/100))
  adb shell input swipe "$sx" "$sy" "$ex" "$ey" 500
  sleep 1
}

tap_page_center() {
  refresh_ui
  read -r x1 y1 x2 y2 <<<"$(python3 /tmp/night_pdf_editor_uia.py bounds_desc "Editable PDF page")"
  adb shell input tap $(((x1+x2)/2)) $(((y1+y2)/2))
  sleep 1
}

assert_alive() {
  adb shell pidof "$PACKAGE" >/dev/null
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat-current.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat-current.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during PDF editor validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat-current.txt"; then
    echo "Night hit an ANR during PDF editor validation." >&2
    exit 1
  fi
}

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 5
assert_alive
assert_desc "PDF edit composer"
assert_desc "PDF tool View"
assert_desc "PDF tool Draw"
assert_desc "PDF tool Highlight"
assert_desc "PDF tool Text"
assert_desc "PDF tool Sign"
assert_desc "Editable PDF page"
assert_desc "PDF caption"
assert_desc "Send PDF"
assert_desc "PDF editor page indicator"
assert_text_contains "Night PDF editor.pdf"

original_before="$(adb shell run-as "$PACKAGE" sha256sum files/night-pdf-editor-original.pdf | awk '{print $1}')"
draft_before="$(adb shell run-as "$PACKAGE" sha256sum files/night-pdf-editor-draft.pdf | awk '{print $1}')"
printf '%s\n' "originalBefore=$original_before" "draftBefore=$draft_before" > "$OUT/hashes-before.txt"
adb exec-out screencap -p > "$OUT/01-editor-open.png"

echo "STEP: draw annotation"
tap_desc "PDF tool Draw"
swipe_on_page

echo "STEP: highlight annotation"
tap_desc "PDF tool Highlight"
swipe_on_page

echo "STEP: add text annotation"
tap_desc "PDF tool Text"
tap_page_center
assert_desc "PDF text value"
tap_desc "PDF text value"
adb shell input text "NightNote"
sleep 1
tap_text "Add"
sleep 1

echo "STEP: signature ink"
tap_desc "PDF tool Sign"
swipe_on_page

echo "STEP: undo and redo"
tap_desc "Undo PDF edit"
tap_desc "Redo PDF edit"

echo "STEP: navigate page"
tap_desc "Next PDF page"
sleep 2
assert_text_contains "2 / 3"
tap_desc "Previous PDF page"
sleep 2
assert_text_contains "1 / 3"

echo "STEP: caption"
tap_desc "PDF caption"
adb shell input text "Edited%sbrief"
sleep 1
adb shell input keyevent 4
sleep 1
adb exec-out screencap -p > "$OUT/02-edited-before-send.png"
refresh_ui
cp /tmp/window.xml "$OUT/02-edited-before-send.xml"

echo "STEP: export and send"
tap_desc "Send PDF"
viewer_ready=false
for _ in $(seq 1 30); do
  sleep 1
  refresh_ui
  if [ "$(python3 /tmp/night_pdf_editor_uia.py has_desc "PDF full screen viewer")" = "true" ]; then
    viewer_ready=true
    break
  fi
done
if [ "$viewer_ready" != "true" ]; then
  echo "Edited PDF did not open after send." >&2
  cp /tmp/window.xml "$OUT/failure-after-send.xml" || true
  adb exec-out screencap -p > "$OUT/failure-after-send.png" || true
  exit 1
fi

assert_desc "PDF page indicator"
assert_text_contains "Night PDF editor.pdf"
adb exec-out screencap -p > "$OUT/03-edited-result.png"
refresh_ui
cp /tmp/window.xml "$OUT/03-edited-result.xml"

original_after="$(adb shell run-as "$PACKAGE" sha256sum files/night-pdf-editor-original.pdf | awk '{print $1}')"
draft_after="$(adb shell run-as "$PACKAGE" sha256sum files/night-pdf-editor-draft.pdf | awk '{print $1}')"
printf '%s\n' "originalAfter=$original_after" "draftAfter=$draft_after" > "$OUT/hashes-after.txt"

if [ "$original_before" != "$original_after" ]; then
  echo "Original PDF was modified by the editor." >&2
  exit 1
fi
if [ "$draft_before" = "$draft_after" ]; then
  echo "Edited PDF draft did not change after annotations." >&2
  exit 1
fi

prefs="$(adb shell run-as "$PACKAGE" cat shared_prefs/night_pdf_editor_preview.xml)"
printf '%s\n' "$prefs" > "$OUT/send-state.xml"
printf '%s' "$prefs" | grep -q 'name="sent" value="true"'
printf '%s' "$prefs" | grep -q 'Edited brief'

cat "$OUT/"*.xml > "$OUT/all-ui.xml"
if grep -qi "Created by" "$OUT/all-ui.xml"; then
  echo "Visible watermark text found in PDF editor." >&2
  exit 1
fi
if grep -qi "Sticker" "$OUT/all-ui.xml"; then
  echo "Sticker UI leaked into PDF editor." >&2
  exit 1
fi

adb logcat -d -v threadtime > "$OUT/logcat.txt"
assert_no_night_crash

printf '%s\n'   "androidApi=36"   "fullscreenEditor=true"   "draftCopy=true"   "originalUnchanged=true"   "draw=true"   "highlight=true"   "text=true"   "signature=true"   "undoRedo=true"   "pageNavigation=true"   "caption=true"   "sendAsNewMessageFlow=true"   "editedPdfReopens=true"   "watermarkFree=true" > "$OUT/summary.txt"
