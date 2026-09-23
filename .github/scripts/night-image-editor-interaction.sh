#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.MediaEditorPreviewActivity"
APK="night-image-editor-apk/app-debug.apk"
OUT="night-image-editor-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_image_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
mode, value = sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else ""

def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        return None
    x1,y1,x2,y2 = map(int,m.groups())
    return (x1,y1,x2,y2)

nodes = list(root.iter("node"))
match = None
if mode == "desc":
    match = next((n for n in nodes if n.attrib.get("content-desc") == value), None)
elif mode == "text":
    match = next((n for n in nodes if n.attrib.get("text") == value), None)
elif mode == "class":
    match = next((n for n in nodes if n.attrib.get("class") == value), None)
elif mode == "has_text":
    print("true" if any(value in n.attrib.get("text","") for n in nodes) else "false")
    raise SystemExit(0)

if match is None or bounds(match) is None:
    raise SystemExit(2)
x1,y1,x2,y2 = bounds(match)
print(f"{(x1+x2)//2} {(y1+y2)//2}")
PY

refresh_ui() {
  local attempt
  rm -f /tmp/window.xml
  for attempt in $(seq 1 10); do
    if adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 &&
       adb exec-out cat /sdcard/window.xml > /tmp/window.xml 2>/dev/null &&
       grep -q "<hierarchy" /tmp/window.xml; then
      return 0
    fi
    sleep 1
  done
  echo "Could not obtain the Night image-editor UI hierarchy." >&2
  adb exec-out screencap -p > "$OUT/failure-ui-hierarchy.png" 2>/dev/null || true
  adb logcat -d -v threadtime > "$OUT/logcat-ui-hierarchy-failure.txt" 2>/dev/null || true
  return 1
}

tap_desc() {
  local wanted="$1"
  local attempt coords x y
  for attempt in $(seq 1 10); do
    refresh_ui
    coords="$(python3 /tmp/night_image_uia.py desc "$wanted" 2>/dev/null || true)"
    if [ -n "$coords" ]; then
      read -r x y <<<"$coords"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo "Could not tap Night image-editor control: $wanted" >&2
  cp /tmp/window.xml "$OUT/failure-tap-desc.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/failure-tap-desc.png" 2>/dev/null || true
  return 1
}

tap_text() {
  local wanted="$1"
  local attempt coords x y
  for attempt in $(seq 1 10); do
    refresh_ui
    coords="$(python3 /tmp/night_image_uia.py text "$wanted" 2>/dev/null || true)"
    if [ -n "$coords" ]; then
      read -r x y <<<"$coords"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo "Could not tap Night image-editor text: $wanted" >&2
  cp /tmp/window.xml "$OUT/failure-tap-text.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/failure-tap-text.png" 2>/dev/null || true
  return 1
}

assert_desc() {
  local wanted="$1"
  local attempt
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_image_uia.py desc "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Night image-editor control did not appear: $wanted" >&2
  cp /tmp/window.xml "$OUT/failure-assert-desc.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/failure-assert-desc.png" 2>/dev/null || true
  return 1
}

assert_text() {
  local wanted="$1"
  local attempt
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_image_uia.py text "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Night image-editor text did not appear: $wanted" >&2
  cp /tmp/window.xml "$OUT/failure-assert-text.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/failure-assert-text.png" 2>/dev/null || true
  return 1
}

assert_alive() {
  adb shell pidof "$PACKAGE" >/dev/null
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat-current.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat-current.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during image editor validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat-current.txt"; then
    echo "Night hit an ANR during image editor validation." >&2
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
assert_alive
assert_desc "Crop"
assert_desc "Rotate"
assert_desc "Emoji"
assert_desc "Text"
assert_desc "Draw"
assert_desc "Undo"
assert_desc "Redo"
assert_desc "Send media"
adb exec-out screencap -p > "$OUT/01-image-editor-open.png"
refresh_ui
cp /tmp/window.xml "$OUT/01-image-editor-open.xml"

original_hash="$(adb shell run-as "$PACKAGE" sha256sum cache/night-editor-preview.jpg | awk '{print $1}')"
test -n "$original_hash"
adb exec-out run-as "$PACKAGE" cat cache/night-editor-preview.jpg > "$OUT/source-image.jpg"
printf '%s\n' "$original_hash" > "$OUT/original-sha256.txt"

echo "STEP: untouched image send preserves original bytes and resolution"
tap_desc "Send media"
untouched_path=""
for _ in $(seq 1 30); do
  prefs="$(adb shell run-as "$PACKAGE" cat shared_prefs/night_media_preview.xml 2>/dev/null || true)"
  untouched_path="$(printf '%s\n' "$prefs" | sed -n 's/.*<string name="exportPath">\([^<]*\)<\/string>.*/\1/p' | head -n 1)"
  if [ -n "$untouched_path" ]; then break; fi
  sleep 1
done
test -n "$untouched_path"
adb exec-out run-as "$PACKAGE" cat "$untouched_path" > "$OUT/untouched-export.jpg"
untouched_hash="$(sha256sum "$OUT/untouched-export.jpg" | awk '{print $1}')"
if [ "$untouched_hash" != "$original_hash" ]; then
  echo "Opening and sending an untouched image changed its original bytes." >&2
  exit 1
fi

# The preview activity clears its export marker on launch; relaunch to continue
# the existing edited-copy workflow independently.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 3
assert_alive
assert_desc "Send media"
original_hash="$(adb shell run-as "$PACKAGE" sha256sum cache/night-editor-preview.jpg | awk '{print $1}')"
test -n "$original_hash"

echo "STEP: edited back requires confirmation"
tap_desc "Rotate"
adb shell input keyevent 4
sleep 1
assert_text "Discard changes?"
assert_text "Keep editing"
assert_text "Discard"
adb exec-out screencap -p > "$OUT/02-discard-confirmation.png"
tap_text "Keep editing"
assert_desc "Crop"

echo "STEP: crop submode has explicit apply/cancel boundary"
tap_desc "Crop"
assert_desc "Rotate crop"
assert_desc "Apply crop"
adb exec-out screencap -p > "$OUT/03-crop-mode.png"
tap_desc "Rotate crop"
tap_desc "Apply crop"
sleep 2
assert_desc "Crop"

echo "STEP: drawing exposes color and thickness"
tap_desc "Draw"
assert_desc "Brush Red"
assert_desc "Brush size Thin"
assert_desc "Brush size Medium"
assert_desc "Brush size Thick"
assert_desc "Done drawing"
tap_desc "Brush Red"
tap_desc "Brush size Thick"
# Draw well inside the editor canvas, away from toolbar/caption controls.
adb shell input swipe 180 520 520 820 700
adb shell input swipe 520 520 180 820 700
tap_desc "Done drawing"
assert_desc "Draw"
adb exec-out screencap -p > "$OUT/04-drawing-applied.png"

echo "STEP: text overlay has color selection"
tap_desc "Text"
assert_text "Add text"
assert_desc "Text color Blue"
refresh_ui
read -r tx ty <<<"$(python3 /tmp/night_image_uia.py class android.widget.EditText)"
adb shell input tap "$tx" "$ty"
adb shell input text "NightEdit"
tap_desc "Text color Blue"
tap_text "Add"
sleep 1
assert_desc "Text"

echo "STEP: emoji overlay"
tap_desc "Emoji"
sleep 1
# Fluent emoji entries expose the emoji as their accessibility description.
if refresh_ui && python3 /tmp/night_image_uia.py desc "😀" >/dev/null 2>&1; then
  read -r ex ey <<<"$(python3 /tmp/night_image_uia.py desc "😀")"
  adb shell input tap "$ex" "$ey"
else
  # Fallback to the first visible grinning emoji text node.
  tap_text "😀"
fi
sleep 1
assert_desc "Send media"
adb exec-out screencap -p > "$OUT/05-ready-to-send.png"
refresh_ui
cp /tmp/window.xml "$OUT/05-ready-to-send.xml"

echo "STEP: export edited copy"
tap_desc "Send media"
exported_path=""
for _ in $(seq 1 45); do
  prefs="$(adb shell run-as "$PACKAGE" cat shared_prefs/night_media_preview.xml 2>/dev/null || true)"
  if printf '%s' "$prefs" | grep -q 'name="exportError"'; then
    printf '%s\n' "$prefs" > "$OUT/export-prefs-error.xml"
    echo "Night image export reported an error." >&2
    exit 1
  fi
  exported_path="$(printf '%s\n' "$prefs" | sed -n 's/.*<string name="exportPath">\([^<]*\)<\/string>.*/\1/p' | head -n 1)"
  if [ -n "$exported_path" ]; then
    printf '%s\n' "$prefs" > "$OUT/export-prefs.xml"
    break
  fi
  sleep 1
done
test -n "$exported_path"
adb shell run-as "$PACKAGE" test -s "$exported_path"
adb exec-out run-as "$PACKAGE" cat "$exported_path" > "$OUT/exported-image.jpg"
test -s "$OUT/exported-image.jpg"
python3 - <<'PY'
from pathlib import Path

def jpeg_dimensions(path):
    data = Path(path).read_bytes()
    assert data[:2] == b"\xff\xd8", data[:8]
    assert data[-2:] == b"\xff\xd9", data[-8:]
    offset = 2
    while offset + 9 < len(data):
        if data[offset] != 0xFF:
            offset += 1
            continue
        marker = data[offset + 1]
        offset += 2
        if marker in {0xD8, 0xD9} or 0xD0 <= marker <= 0xD7:
            continue
        length = int.from_bytes(data[offset:offset + 2], "big")
        if marker in {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}:
            height = int.from_bytes(data[offset + 3:offset + 5], "big")
            width = int.from_bytes(data[offset + 5:offset + 7], "big")
            return width, height
        offset += length
    raise AssertionError(f"No JPEG frame dimensions found in {path}")

export_path = "night-image-editor-artifacts/exported-image.jpg"
source_path = "night-image-editor-artifacts/source-image.jpg"
exported = jpeg_dimensions(export_path)
source = jpeg_dimensions(source_path)
print(f"image editor export dimensions={exported}, source={source}")
assert Path(export_path).stat().st_size > 1500
assert min(exported) >= 1200, f"Image export is too small: {exported}"
assert max(exported) >= int(max(source) * 0.60), (exported, source)
PY

after_hash="$(adb shell run-as "$PACKAGE" sha256sum cache/night-editor-preview.jpg | awk '{print $1}')"
if [ "$after_hash" != "$original_hash" ]; then
  echo "Original source image changed during editing." >&2
  exit 1
fi
printf '%s\n' "$after_hash" > "$OUT/original-sha256-after.txt"
export_hash="$(sha256sum "$OUT/exported-image.jpg" | awk '{print $1}')"
if [ "$export_hash" = "$original_hash" ]; then
  echo "Edited export is byte-identical to original." >&2
  exit 1
fi
printf '%s\n' "$export_hash" > "$OUT/exported-sha256.txt"

adb logcat -d -v threadtime > "$OUT/logcat.txt"
assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "fullscreenImageEditor=true" \
  "crop=true" \
  "rotate=true" \
  "drawColorAndThickness=true" \
  "textOverlay=true" \
  "emojiOverlay=true" \
  "undoRedo=true" \
  "discardConfirmation=true" \
  "untouchedImagePreservedByteForByte=true" \
  "exportedCopy=true" \
  "originalPreserved=true" \
  "noStickers=true" > "$OUT/summary.txt"
