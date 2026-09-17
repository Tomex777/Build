#!/usr/bin/env bash
set -euo pipefail

ROOT="/tmp/sora-src/Sora_Android_v0_1"
OUT="$GITHUB_WORKSPACE/sora-emulator-screens"
mkdir -p "$OUT"

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system font_scale 1.0

adb install -r "$ROOT/meme-extension/build/outputs/apk/debug/meme-extension-debug.apk"
adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb shell am force-stop com.night.sora
adb shell am start -n com.night.sora/.MainActivity
sleep 3

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/${name}.png"
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null
}

coords_for() {
  local needle="$1"
  python3 - "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1]
root = ET.parse('/tmp/window.xml').getroot()
for node in root.iter('node'):
    text = node.attrib.get('text','')
    desc = node.attrib.get('content-desc','')
    if text == needle or desc == needle or needle in text or needle in desc:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_text() {
  local needle="$1"
  dump_ui
  local xy
  xy="$(coords_for "$needle")" || return 1
  adb shell input tap ${xy/,/ }
  sleep 1
}

tap_text_scroll() {
  local needle="$1"
  local direction="${2:-up}"
  for _ in 1 2 3 4 5; do
    if tap_text "$needle"; then return 0; fi
    if [[ "$direction" == "up" ]]; then
      adb shell input swipe 540 1850 540 650 350
    else
      adb shell input swipe 540 650 540 1850 350
    fi
    sleep 1
  done
  return 1
}

shot "01-home-top"

# Home -> Bible truthfulness entry.
if tap_text_scroll "Open Bible" up; then
  shot "02-home-bible-entry"
  # The previous tap may have opened it already.
  sleep 2
else
  echo "Could not find Home Bible entry" >&2
  exit 1
fi

# If still on Home because the matched node was non-clickable, tap again.
dump_ui
if coords_for "Bible reader" >/dev/null 2>&1 || coords_for "Continue reading" >/dev/null 2>&1 || coords_for "Books" >/dev/null 2>&1; then
  :
else
  tap_text "Open Bible" || true
  sleep 2
fi
shot "03-bible-hub"

# Translation picker.
if tap_text "WEB"; then
  sleep 1
  shot "04-bible-version-picker"
  adb shell input keyevent 4
  sleep 1
fi

# Book -> chapter -> reader with real live/cached scripture.
tap_text_scroll "Genesis" down || tap_text_scroll "Genesis" up
sleep 1
shot "05-bible-chapters"
tap_text "1"
sleep 4
shot "06-bible-reader"

# Bookmark a real verse if available.
if tap_text "Bookmark verse"; then
  sleep 1
  shot "07-bible-bookmarked"
fi

# Validate internal system-back behavior: Reader -> Chapters -> Hub -> Sora.
adb shell input keyevent 4
sleep 1
shot "08-bible-back-to-chapters"
adb shell input keyevent 4
sleep 1
shot "09-bible-back-to-hub"
adb shell input keyevent 4
sleep 1

# Root -> Media -> Memes.
tap_text "Media"
sleep 1
tap_text "Anime & Manga"
sleep 1
shot "10-media-switcher"
tap_text "Memes"
sleep 5
shot "11-memes-feed-or-state"

# If Reddit is reachable from this emulator, open a live post.
if tap_text "Open post"; then
  sleep 3
  shot "12-meme-detail"
else
  echo "No live meme post opened; retaining truthful feed/unavailable screenshot."
fi

dump_ui
cp /tmp/window.xml "$OUT/final-window.xml"
ls -lh "$OUT"
