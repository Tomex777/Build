#!/usr/bin/env bash
# Interactive emulator acceptance: two genuine Gecko PDF saves, offline listing,
# export picker, deletion, restart persistence, missing-file handling.
set -euo pipefail

OUT=night-browser/screenshots/agent07
APK=night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk
PKG=com.night.browser
mkdir -p "$OUT"
trap 'adb logcat -d -t 3500 | grep -E "FATAL EXCEPTION|AndroidRuntime|com.night.browser" > "$OUT/logcat.txt" || true' EXIT

adb install -r "$APK"
adb shell wm size 1080x1920
adb shell wm density 420
adb shell am start -n "$PKG/.MainActivity"
sleep 12

dump_ui() {
  adb shell uiautomator dump /sdcard/night-agent7-ui.xml >/dev/null 2>&1
  adb pull /sdcard/night-agent7-ui.xml "$OUT/ui.xml" >/dev/null 2>&1
}
tap_ui() {
  local label="$1"
  local xy=""
  # Gecko/Compose startup after process restart and PDF-viewer return is
  # asynchronous. Poll visible *NIGHT-owned* nodes before declaring a failure.
  for _ in $(seq 1 12); do
    dump_ui
    xy=$(python3 - "$OUT/ui.xml" "$label" "$PKG" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.get('package') != sys.argv[3]:
        continue
    if node.get('content-desc') == sys.argv[2] or node.get('text') == sys.argv[2]:
        b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
        if len(b)==4 and b[2]>b[0] and b[3]>b[1]:
            print((b[0]+b[2])//2, (b[1]+b[3])//2)
            break
PY
)
    [ -n "$xy" ] && break
    sleep 1
  done
  if [ -z "$xy" ]; then
    echo "NIGHT UI target absent after 12 UI polls: $label" >&2
    cp "$OUT/ui.xml" "$OUT/absent-$(echo "$label" | tr ' ' '-').xml"
    adb shell dumpsys activity activities > "$OUT/activity-when-$(
      echo "$label" | tr ' ' '-').txt" || true
    adb shell screencap -p /sdcard/night-agent7-failure.png || true
    adb pull /sdcard/night-agent7-failure.png "$OUT/absent-$(
      echo "$label" | tr ' ' '-').png" >/dev/null 2>&1 || true
    return 1
  fi
  adb shell input tap $xy
  sleep 1
}
shot() {
  adb shell screencap -p /sdcard/night-agent7.png
  adb pull /sdcard/night-agent7.png "$OUT/$1.png" >/dev/null
}
pdf_names() {
  # Nested Android 'sh -c ... | wc' pipelines can be parsed by adb incorrectly.
  # Count files on CI host after a direct run-as directory listing.
  adb shell run-as "$PKG" ls files/saved-pages 2>/dev/null | tr -d '\r' |
    grep -E '^page-.*[.]pdf$' || true
}
pdf_count() {
  pdf_names | awk 'END { print NR + 0 }'
}
delete_all_test_pdfs() {
  while IFS= read -r name; do
    [ -z "$name" ] && continue
    adb shell run-as "$PKG" rm "files/saved-pages/$name"
  done < <(pdf_names)
}
wait_for_pdfs() {
  local need="$1"
  for _ in $(seq 1 24); do
    local n
    n=$(pdf_count)
    if [ "$n" -ge "$need" ]; then echo "Saved PDFs: $n";return;fi
    sleep 2
  done
  echo "Expected $need saved PDF files; found $(pdf_count)" >&2
  exit 1
}

# Use a live site tile instead of blind address coordinates obscured by the IME.
tap_ui 'Wikipedia'
sleep 18
dump_ui
shot "01-real-webpage"
if ! grep -qi 'wikipedia.org' "$OUT/ui.xml"; then
  echo "Browser did not navigate to Wikipedia. Inspect screenshot/UI dump." >&2
  exit 1
fi

tap_ui 'More'
tap_ui 'Save PDF copy'
wait_for_pdfs 1
sleep 2
tap_ui 'More'
tap_ui 'Save PDF copy'
wait_for_pdfs 2

tap_ui 'More'
tap_ui 'Time Machine'
sleep 2
shot "02-two-versions"
dump_ui
if ! grep -q '2 versions' "$OUT/ui.xml"; then
  echo "Expected grouped two-version UI" >&2;exit 1
fi

# Launch the real ACTION_CREATE_DOCUMENT picker, then cancel; original remains.
tap_ui 'Export PDF to a folder'
shot "03-export-picker"
adb shell input keyevent KEYCODE_BACK
sleep 2

# Simulate offline mode, and request opening a saved PDF (with or without a viewer).
adb shell svc wifi disable || true
adb shell svc data disable || true
tap_ui 'Open PDF version'
shot "04-open-copy-offline"
adb shell input keyevent KEYCODE_BACK || true
sleep 2
adb shell am force-stop "$PKG"
# Explicitly foreground NIGHT after the external PDF viewer was opened; an
# existing third-party viewer must not be mistaken for a missing browser menu.
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
tap_ui 'More'
tap_ui 'Time Machine'
shot "05-after-restart"
dump_ui
grep -q '2 versions' "$OUT/ui.xml"

# Confirm the delete action also removes backing files.
tap_ui 'Delete PDF version'
tap_ui 'Delete'
sleep 3
test "$(pdf_count)" -eq 1
shot "06-after-delete"

# Broken-file state must be understandable and deletable rather than crashing.
delete_all_test_pdfs
test "$(pdf_count)" -eq 0
adb shell am force-stop "$PKG"
# Explicitly foreground NIGHT after the external PDF viewer was opened; an
# existing third-party viewer must not be mistaken for a missing browser menu.
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
tap_ui 'More'
tap_ui 'Time Machine'
shot "07-missing-file"
dump_ui
grep -q 'PDF file missing' "$OUT/ui.xml"
tap_ui 'Delete PDF version'
tap_ui 'Delete'
shot "08-missing-entry-deleted"
echo 'Agent 7 emulator acceptance passed' | tee "$OUT/result.txt"
