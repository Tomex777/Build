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
adb install -r "$ROOT/tumblr-meme-extension/build/outputs/apk/debug/tumblr-meme-extension-debug.apk"
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
  sleep 2
else
  echo "Could not find Home Bible entry" >&2
  exit 1
fi

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

# Verse interaction sheet, highlighting, and note editor.
if tap_text "In the beginning"; then
  sleep 1
  shot "07-bible-verse-actions"
  if tap_text "Highlight"; then
    adb shell input keyevent 4
    sleep 1
    shot "08-bible-highlighted"
    if tap_text "In the beginning"; then
      if tap_text "Add note"; then
        sleep 1
        shot "09-bible-note-editor"
        adb shell input keyevent 4
        sleep 1
      fi
    fi
  fi
else
  echo "Could not open verse actions" >&2
  exit 1
fi

# Bookmark a real verse if available.
if tap_text "Bookmark verse"; then
  sleep 1
  shot "10-bible-bookmarked"
fi

# Validate internal system-back behavior: Reader -> Chapters -> Hub -> Sora.
adb shell input keyevent 4
sleep 1
shot "11-bible-back-to-chapters"
adb shell input keyevent 4
sleep 1
shot "12-bible-back-to-hub"
adb shell input keyevent 4
sleep 1

# Root -> Media -> Memes.
tap_text "Media"
sleep 1
tap_text "Anime & Manga"
sleep 1
shot "13-media-switcher"
tap_text "Memes"
sleep 5
shot "14-memes-feed-or-state"

# If live providers are unavailable, recovery actions must exist. Otherwise open a live post.
if tap_text "Open post"; then
  sleep 3
  shot "15-meme-detail"
  adb shell input keyevent 4
  sleep 1
else
  dump_ui
  if coords_for "Retry" >/dev/null 2>&1 && coords_for "Sources" >/dev/null 2>&1; then
    shot "15-meme-recovery-actions"
  else
    echo "Meme feed unavailable without Retry/Sources recovery" >&2
    exit 1
  fi
fi

# Saved memes must have a first-class Library filter.
tap_text "Library"
sleep 1
shot "16-library-before-meme-filter"
adb shell input swipe 950 190 150 190 400
sleep 1
dump_ui
if coords_for "Memes" >/dev/null 2>&1; then
  tap_text "Memes"
  shot "17-library-memes-filter"
else
  echo "Library Memes filter not reachable" >&2
  exit 1
fi

dump_ui
cp /tmp/window.xml "$OUT/final-window.xml"
ls -lh "$OUT"
