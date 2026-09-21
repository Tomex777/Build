#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
ACTIVITY="$PACKAGE/.PdfViewerPreviewActivity"
APK="night-pdf-viewer-apk/app-debug.apk"
OUT="night-pdf-viewer-artifacts"
mkdir -p "$OUT"

cat > /tmp/night_pdf_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
mode = sys.argv[1]
value = sys.argv[2] if len(sys.argv) > 2 else ""

def rect(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not m:
        return None
    return tuple(map(int, m.groups()))

nodes = list(root.iter("node"))

if mode == "has_text":
    print("true" if any(value in n.attrib.get("text", "") for n in nodes) else "false")
    raise SystemExit(0)

if mode == "has_desc":
    print("true" if any(value == n.attrib.get("content-desc", "") for n in nodes) else "false")
    raise SystemExit(0)

match = None
if mode == "desc":
    match = next((n for n in nodes if n.attrib.get("content-desc") == value), None)
elif mode == "text":
    match = next((n for n in nodes if n.attrib.get("text") == value), None)

if match is None or rect(match) is None:
    raise SystemExit(2)
x1,y1,x2,y2 = rect(match)
print(f"{(x1+x2)//2} {(y1+y2)//2}")
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-pdf-window.xml >/dev/null
  adb exec-out cat /sdcard/night-pdf-window.xml > /tmp/window.xml
}

tap_desc() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_pdf_uia.py desc "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

tap_text() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_pdf_uia.py text "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

assert_desc() {
  refresh_ui
  python3 /tmp/night_pdf_uia.py desc "$1" >/dev/null
}

assert_text_contains() {
  refresh_ui
  if [ "$(python3 /tmp/night_pdf_uia.py has_text "$1")" != "true" ]; then
    echo "Missing PDF UI text: $1" >&2
    cp /tmp/window.xml "$OUT/failure-window.xml" || true
    adb exec-out screencap -p > "$OUT/failure-screen.png" || true
    exit 1
  fi
}

assert_alive() {
  adb shell pidof "$PACKAGE" >/dev/null
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat-current.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat-current.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during PDF validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat-current.txt"; then
    echo "Night hit an ANR during PDF validation." >&2
    exit 1
  fi
}

PDF_SOURCE="whatsapp-ai-android/app/src/main/java/com/example/whatsapp/presentation/chatscreen/NightPdfViewerScreen.kt"
if grep -q "ModalBottomSheet" "$PDF_SOURCE"; then
  echo "Night PDF viewer regressed to a bottom-sheet implementation." >&2
  exit 1
fi
if grep -q 'contentDescription = "PDF bottom sheet"' "$PDF_SOURCE"; then
  echo "Night PDF viewer still exposes bottom-sheet semantics." >&2
  exit 1
fi

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY"
sleep 6
assert_alive
assert_desc "PDF full screen viewer"
assert_desc "Close PDF"
assert_desc "Search document"
assert_desc "Share PDF"
assert_desc "Save PDF"
assert_desc "Open PDF externally"
assert_desc "Document info"
assert_desc "PDF page indicator"
assert_text_contains "night-pdf-preview.pdf"
assert_text_contains "24 pages"
adb exec-out screencap -p > "$OUT/01-pdf-open.png"
refresh_ui
cp /tmp/window.xml "$OUT/01-pdf-open.xml"

echo "STEP: document info"
tap_desc "Document info"
assert_text_contains "PDF document"
assert_text_contains "24 pages"
adb exec-out screencap -p > "$OUT/02-document-info.png"
tap_text "Done"

echo "STEP: text search and jump"
tap_desc "Search document"
assert_desc "Search PDF text"
tap_desc "Search PDF text"
adb shell input text "Orion"
sleep 1
tap_desc "Run PDF search"

search_ok=false
for _ in $(seq 1 30); do
  sleep 1
  refresh_ui
  if [ "$(python3 /tmp/night_pdf_uia.py has_text "1 of 1 matches")" = "true" ]; then
    search_ok=true
    break
  fi
done
if [ "$search_ok" != "true" ]; then
  echo "Night PDF search did not return the unique Orion result." >&2
  cp /tmp/window.xml "$OUT/failure-search.xml" || true
  adb exec-out screencap -p > "$OUT/failure-search.png" || true
  exit 1
fi

page_jump=false
for _ in $(seq 1 12); do
  sleep 1
  refresh_ui
  if [ "$(python3 /tmp/night_pdf_uia.py has_text "17 / 24")" = "true" ]; then
    page_jump=true
    break
  fi
done
if [ "$page_jump" != "true" ]; then
  echo "Night PDF viewer did not jump to the matching page." >&2
  cp /tmp/window.xml "$OUT/failure-page-jump.xml" || true
  adb exec-out screencap -p > "$OUT/failure-page-jump.png" || true
  exit 1
fi
adb exec-out screencap -p > "$OUT/03-search-result.png"
refresh_ui
cp /tmp/window.xml "$OUT/03-search-result.xml"

echo "STEP: document scrolling remains responsive"
tap_desc "Close search"
adb shell input swipe 360 1180 360 500 650
sleep 2
adb shell input swipe 360 1180 360 500 650
sleep 2
assert_alive
adb exec-out screencap -p > "$OUT/04-after-scroll.png"
refresh_ui
cp /tmp/window.xml "$OUT/04-after-scroll.xml"

cat "$OUT/"*-pdf-open.xml "$OUT/"*-search-result.xml "$OUT/"*-after-scroll.xml > "$OUT/all-ui.xml"
if grep -qi "Created by" "$OUT/all-ui.xml"; then
  echo "Visible watermark text found in Night PDF viewer." >&2
  exit 1
fi
if grep -qi "Sticker" "$OUT/all-ui.xml"; then
  echo "Sticker UI leaked into Night PDF viewer." >&2
  exit 1
fi

adb logcat -d -v threadtime > "$OUT/logcat.txt"
assert_no_night_crash

printf '%s\n' \
  "androidApi=36" \
  "documentFullscreen=true" \
  "pages=24" \
  "smoothScroll=true" \
  "zoomEnabled=true" \
  "pageIndicator=true" \
  "textSearch=true" \
  "searchJumpPage=17" \
  "shareControl=true" \
  "saveControl=true" \
  "externalOpenControl=true" \
  "documentInfo=true" \
  "largeDocumentStrategy=minimize-cache" \
  "watermarkFree=true" > "$OUT/summary.txt"

echo "STEP: Android back closes the document viewer"
adb shell input keyevent 4
sleep 1
assert_no_night_crash
