#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-hub-tabs
mkdir -p "$OUT"
adb wait-for-device
adb install -r "$SORA_ROOT/meme-extension/build/outputs/apk/debug/meme-extension-debug.apk" >/dev/null
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 3

shot() {
  adb exec-out screencap -p > "$OUT/$1.png"
  adb shell uiautomator dump /sdcard/sora-hub.xml >/dev/null 2>&1 || true
  adb pull /sdcard/sora-hub.xml "$OUT/$1.xml" >/dev/null 2>&1 || true
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/sora-hub-window.xml >/dev/null 2>&1 || true
}

node_exists() {
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
needle=sys.argv[1].lower()
root=ET.parse('/tmp/sora-hub-window.xml').getroot()
for node in root.iter('node'):
    values=[node.attrib.get('text',''),node.attrib.get('content-desc','')]
    if any(needle in value.lower() for value in values):
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for() {
  local label="$1" timeout="${2:-25}"
  for _ in $(seq 1 "$timeout"); do
    dump_ui
    if node_exists "$label"; then return 0; fi
    sleep 1
  done
  shot "failure-$label"
  adb logcat -d -t 600 | grep -Ei 'com\.night\.sora|AndroidRuntime|FATAL EXCEPTION' | tail -n 120 >&2 || true
  echo "Timed out waiting for '$label'" >&2
  return 1
}

tap() {
  local label="$1"
  wait_for "$label"
  python3 - "$label" <<'PY' > /tmp/sora-hub-tap.txt
import re,sys,xml.etree.ElementTree as ET
needle=sys.argv[1].lower()
root=ET.parse('/tmp/sora-hub-window.xml').getroot()
nodes=list(root.iter('node'))
exact=[node for node in nodes if needle in (node.attrib.get('text','').lower(), node.attrib.get('content-desc','').lower())]
for node in exact or nodes:
    if node not in exact and needle not in (node.attrib.get('text','')+' '+node.attrib.get('content-desc','')).lower(): continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); break
else: raise SystemExit(1)
PY
  read -r x y < /tmp/sora-hub-tap.txt
  adb shell input tap "$x" "$y"
  sleep 1
}

tap_scrolling() {
  local label="$1"
  for _ in 1 2 3 4 5; do
    dump_ui
    if node_exists "$label"; then tap "$label"; return 0; fi
    adb shell input swipe 540 1850 540 650 350
    sleep 1
  done
  echo "Could not find '$label' while scrolling" >&2
  return 1
}

# Games now uses actual saved Anime/Manga art and reports real play counts.
tap Games
wait_for "Title Match"
wait_for "YOUR PLAY"
wait_for "Save at least two Anime or Manga titles"
shot games-empty-library

# Library can be legitimately empty in a clean emulator. Verify the real empty
# state on All and Memes instead of assuming an item was seeded by another flow.
tap Library
wait_for "0 items"
wait_for "No saved items in this filter"
shot library-empty
adb shell input swipe 950 350 160 350 350
sleep 1
tap Memes
wait_for "0 items"
wait_for "No saved items in this filter"
shot library-memes-empty

# Check every working More destination without mutating user data.
tap More
tap Downloads
wait_for "Active transfers"
shot downloads-empty
adb shell input keyevent 4
tap Statistics
wait_for "Statistics"
shot statistics
adb shell input keyevent 4
tap Data & storage
wait_for "Clear catalog cache"
shot data-storage
adb shell input keyevent 4
tap Player & reader
wait_for "DEFAULT SOURCES"
wait_for "No compatible source installed"
shot player-reader-settings
adb shell input keyevent 4
tap AI & models
wait_for "AI provider not connected"
shot ai-provider-state
adb shell input keyevent 4
tap About & help
wait_for "About Sora"
wait_for "Version"
shot about
adb shell input keyevent 4
tap_scrolling Appearance
wait_for Display
shot system-display-settings
adb shell input keyevent 4

# Bible opens the real reader, and book -> chapter navigation is functional.
bible_loaded=0
tap Home
tap_scrolling "Open Bible"
wait_for "Books"
wait_for "Go to reference"
shot bible-hub
tap Genesis
wait_for "Choose a chapter"
shot bible-chapters
tap 1
if wait_for "In the beginning" 30; then
  bible_loaded=1
  tap "In the beginning"
  wait_for Highlight
  tap Highlight
  wait_for Unhighlight
  adb shell input keyevent 4
  tap "In the beginning"
  tap "Bookmark verse"
  wait_for "Remove bookmark"
  adb shell input keyevent 4
  tap "In the beginning"
  tap "Add note"
  wait_for Note
  tap Note
  adb shell input text "checked%20in%20emulator"
  tap Save
  wait_for "Verse has a note"
elif ! wait_for "Couldn’t load this passage" 5; then
  echo 'Bible reader showed neither loaded scripture nor a truthful network error.' >&2
  exit 1
fi
shot bible-reader-or-network-error
adb shell input keyevent 4
wait_for "Choose a chapter"
adb shell input keyevent 4
wait_for "Books"
if [[ "$bible_loaded" == "1" ]] && wait_for "Continue reading" 8; then
  tap "Continue reading"
  wait_for "Genesis 1"
  shot bible-exact-reading-resume
  adb shell input keyevent 4
  wait_for "Choose a chapter"
  adb shell input keyevent 4
  wait_for "Books"
fi
adb shell input keyevent 4
wait_for "Sora"
shot home-after-bible

# The non-Anime/Manga media destinations expose truthful source failures.
tap Media
tap "Anime & Manga"
tap "Movies & TV"
wait_for "Catalog unavailable"
wait_for "Retry"
shot movies-source-state
tap "Movies & TV"
tap Music
wait_for "No compatible music catalog source is installed"
wait_for "Manage sources"
shot music-source-state
tap Music
tap Memes
if wait_for "Less like this" 40; then
  tap "♡ Save"
  wait_for "✓ Saved"
  shot meme-saved
  tap "Less like this"
  shot meme-hidden-from-feed
  tap Library
  adb shell input swipe 950 350 160 350 350
  sleep 1
  tap Memes
  wait_for "1 item"
  shot library-saved-meme
  tap Media
  tap "Anime & Manga"
  tap Memes
else
  # An installed extension may exist while its feed is unavailable. The honest
  # error message is provider-specific; Retry and Manage sources are stable UI.
  wait_for "Retry"
  wait_for "Manage sources"
  shot memes-source-state
fi

# The Media switcher routes its Bible entry into the same reader.
tap Memes
tap Bible
wait_for "Books"
shot media-bible-entry
adb shell input keyevent 4

if adb logcat -d -t 1500 | grep -E 'FATAL EXCEPTION|ANR in com\.night\.sora'; then
  echo 'Android reported a Sora crash or ANR during hub validation.' >&2
  exit 1
fi
touch "$OUT/HUB_TABS_PASS"
echo 'Hub tabs emulator walkthrough passed.'
