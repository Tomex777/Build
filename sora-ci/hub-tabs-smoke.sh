#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-hub-tabs
mkdir -p "$OUT"
adb wait-for-device
adb install -r "$SORA_ROOT/live-extension/build/outputs/apk/debug/live-extension-debug.apk" >/dev/null
adb install -r "$SORA_ROOT/meme-extension/build/outputs/apk/debug/meme-extension-debug.apk" >/dev/null
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
# Bible requests are served by the deterministic local fixture configured by CI.
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

wait_for_pixel_launcher_anr() {
  python3 - <<'PY' > /tmp/sora-hub-wait-tap.txt
import re,xml.etree.ElementTree as ET
root=ET.parse('/tmp/sora-hub-window.xml').getroot()
for node in root.iter('node'):
    if node.attrib.get('text','').strip() != 'Wait': continue
    match=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if match:
        x1,y1,x2,y2=map(int,match.groups()); print((x1+x2)//2,(y1+y2)//2); break
else: raise SystemExit(1)
PY
  read -r x y < /tmp/sora-hub-wait-tap.txt
  adb shell input tap "$x" "$y"
  sleep 2
}

library_state_ready() {
  python3 - <<'PY'
import re, xml.etree.ElementTree as ET
root=ET.parse('/tmp/sora-hub-window.xml').getroot()
screen=re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',root.attrib.get('bounds',''))
height=int(screen.group(4)) if screen else 2400
nodes=list(root.iter('node'))
texts=[node.attrib.get('text','').strip() for node in nodes]
has_heading=False
for node in nodes:
    bounds=re.match(r'\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if node.attrib.get('text','').strip() == 'Library' and bounds and int(bounds.group(2)) < height//2:
        has_heading=True
        break
counts=[int(match.group(1)) for text in texts if (match := re.fullmatch(r'(\d+) items?',text))]
empty='No saved items in this filter.' in texts
if has_heading and counts and ((counts[0] == 0 and empty) or (counts[0] > 0 and not empty)):
    raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_library_state() {
  for _ in $(seq 1 25); do
    dump_ui
    if library_state_ready; then return 0; fi
    sleep 1
  done
  shot failure-library-state
  echo 'Library did not show a saved-item count consistent with its empty state.' >&2
  return 1
}

wait_for() {
  local label="$1" timeout="${2:-25}"
  for _ in $(seq 1 "$timeout"); do
    dump_ui
    if node_exists "$label"; then return 0; fi
    if node_exists "Pixel Launcher isn't responding"; then
      wait_for_pixel_launcher_anr
      continue
    fi
    sleep 1
  done
  shot "failure-$label"
  adb logcat -d | grep -Ei 'SoraBible|com\.night\.sora|AndroidRuntime|FATAL EXCEPTION' | tail -n 160 >&2 || true
  echo "Timed out waiting for '$label'" >&2
  return 1
}

wait_for_any() {
  local timeout="${1:-50}"
  shift
  for _ in $(seq 1 "$timeout"); do
    dump_ui
    for label in "$@"; do
      if node_exists "$label"; then
        echo "Found one valid state: $label"
        return 0
      fi
    done
    if node_exists "Pixel Launcher isn't responding"; then
      wait_for_pixel_launcher_anr
    else
      sleep 1
    fi
  done
  shot failure-source-state
  echo "None of the expected source states appeared: $*" >&2
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
  for _ in $(seq 1 12); do
    dump_ui
    if node_exists "$label"; then tap "$label"; return 0; fi
    adb shell input swipe 540 1850 540 650 350
    sleep 1
  done
  echo "Could not find '$label' while scrolling" >&2
  return 1
}

# Games must expose real playable boards instead of no-op game cards.
tap Games
wait_for "Snake"
wait_for "2048"
wait_for "Tic-tac-toe"
wait_for "Memory match"
wait_for "Cover quiz"
shot games-arcade-hub

tap "Snake"
wait_for "SCORE"
wait_for "Start"
tap "Start"
# The button stays at the same coordinates when its label changes to Pause.
# Pause immediately: Snake otherwise reaches the wall before UIAutomator's
# next multi-second accessibility dump. Verify Pause -> Resume even at score 0.
read -r snake_button_x snake_button_y < /tmp/sora-hub-tap.txt
adb shell input tap "$snake_button_x" "$snake_button_y"
wait_for "Resume"
wait_for "Paused"
shot games-snake-paused
tap "Resume"
adb shell input swipe 530 850 530 640 180
shot games-snake-steered
adb shell input keyevent KEYCODE_BACK
wait_for "Memory match"

tap "2048"
wait_for "SCORE"
wait_for "New"
shot games-2048-board
# Exercise touch/swipe control rather than accepting a static 2048 board.
# Following a left/right pair always moves at least the initial two tiles.
adb shell input swipe 800 800 260 800 240
sleep 1
shot games-2048-after-swipe
# Two starting tiles can randomly both occupy the left edge. In that case,
# try right and up before failing, rather than misdiagnosing legal no-op moves.
if cmp -s "$OUT/games-2048-board.png" "$OUT/games-2048-after-swipe.png"; then
  adb shell input swipe 260 800 800 800 240
  sleep 1
  shot games-2048-after-swipe
fi
if cmp -s "$OUT/games-2048-board.png" "$OUT/games-2048-after-swipe.png"; then
  adb shell input swipe 540 940 540 500 240
  sleep 1
  shot games-2048-after-swipe
fi
if cmp -s "$OUT/games-2048-board.png" "$OUT/games-2048-after-swipe.png"; then
  echo '2048 board was visually unchanged after three gestures.' >&2
  exit 1
fi
tap "All games"

tap "Tic-tac-toe"
wait_for "Your turn"
shot games-tictactoe-board
# The nine X/O squares are drawn with Canvas; tap the top-left playable cell.
# A successful interaction renders marks and changes the screenshot.
adb shell input tap 265 605
sleep 2
shot games-tictactoe-after-move
if cmp -s "$OUT/games-tictactoe-board.png" "$OUT/games-tictactoe-after-move.png"; then
  echo 'Tic-tac-toe did not respond visually to a board tap.' >&2
  exit 1
fi
tap "Play again"
wait_for "Your turn"
tap "All games"

tap "Memory match"
wait_for "MOVES"
shot games-memory-board
# Tap two face-down memory cards by accessibility bounds (not hardcoded
# screen positions). The counter increments whether or not they match.
for flip in 1 2; do
  dump_ui
  python3 - <<'PYMEM'
import re,subprocess,xml.etree.ElementTree as ET
root=ET.parse('/tmp/sora-hub-window.xml').getroot()
for node in root.iter('node'):
    if node.attrib.get('content-desc') != 'Hidden': continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2=map(int,m.groups())
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        break
else: raise SystemExit('No hidden memory card available')
PYMEM
  sleep .15
done
wait_for "MOVES  1"
shot games-memory-after-two-flips
tap "Restart"
wait_for "MOVES  0"
tap "All games"

tap "Cover quiz"
wait_for "TITLE MATCH"
wait_for "YOUR PLAY"
wait_for "Save at least two Anime or Manga titles"
shot games-empty-library
tap "All games"

tap_scrolling "Daily 5"
wait_for "QUESTION"
shot games-daily-five
tap "All games"

tap_scrolling "Who am I?"
wait_for "QUESTION"
shot games-who-am-i
tap "All games"

tap_scrolling "Who said it?"
wait_for "QUESTION"
shot games-who-said-it
tap "All games"

tap_scrolling "Bible trivia"
wait_for "QUESTION"
shot games-bible-trivia
# Complete all five Bible questions using their real displayed text. That
# verifies quiz choice, Next/Results navigation and persisted best score.
for round in 1 2 3 4 5; do
  dump_ui
  answer=$(python3 - <<'PYBIBLE'
import xml.etree.ElementTree as ET
root=ET.parse('/tmp/sora-hub-window.xml').getroot()
texts=[n.attrib.get('text','') for n in root.iter('node')]
answers={
    'Which book begins the Bible?':'Genesis',
    'Who built the ark before the great flood?':'Noah',
    'Who defeated Goliath using a sling?':'David',
    'Who was thrown into a den of lions?':'Daniel',
    'According to the Gospels, where was Jesus born?':'Bethlehem',
    "Which book contains 'The Lord is my shepherd'?":'Psalms',
    'Who led the Israelites out of Egypt?':'Moses',
    'Who spent three days in a great fish?':'Jonah',
    'Which disciple denied knowing Jesus three times?':'Peter',
}
for prompt,right in answers.items():
    if prompt in texts:
        print(right);break
else: raise SystemExit('Unknown Bible question: '+repr(texts))
PYBIBLE
  )
  tap "$answer"
  wait_for "Correct!"
  if (( round == 5 )); then tap "See results"; else tap "Next question"; fi
done
wait_for "Round complete"
wait_for "Best: 5 correct"
adb shell run-as com.night.sora cat shared_prefs/sora_arcade_v1.xml | grep -q 'name="quiz_best_bible" value="5"'
shot games-bible-trivia-result
tap "All games"

tap_scrolling "Would you rather?"
wait_for "Make your choice"
shot games-would-rather
tap "Finish your favorite manga in one night"
wait_for "Choice saved on this device"
adb shell run-as com.night.sora cat shared_prefs/sora_arcade_v1.xml | grep -q 'name="rather_0_0" value="1"'
tap "Next choice"
wait_for "ROUND 2 / 10"
shot games-would-rather-next
tap "All games"

tap_scrolling "Meme caption"
wait_for "Save a meme in your Library"
shot games-meme-caption-empty

# The live Anime flow can seed a real title before this walkthrough. Check that
# Library shows its count and matching content/empty state, then verify Memes.
tap Library
wait_library_state
shot library-all-filter
adb shell input swipe 950 350 160 350 350
sleep 1
tap Memes
wait_for "0 items"
wait_for "No saved items in this filter"
shot library-memes-empty

# Check every working More destination without mutating user data.
tap More
tap Extensions
wait_for "Extensions"
wait_for "Sora Live Sources"
tap "Sora Live Sources"
wait_for "TMDB movie catalog"
wait_for "TMDB API Read Access Token"
wait_for "Save token"
shot extension-detail-tmdb
adb shell input keyevent 4
wait_for "Extensions"
wait_for "Reddit Memes"
tap "Reddit Memes"
wait_for "Reddit · r/memes"
wait_for "Reddit access"
wait_for "Installed-app client ID"
wait_for "Save ID"
shot extension-detail
adb shell input keyevent 4
wait_for "Extensions"
adb shell input keyevent 4
wait_for "Downloads"

tap Downloads
# Downloads UI now names the queue "Transfers & retries". The previous
# "Active transfers" assertion targeted copy removed from the actual screen,
# so it failed after successful navigation. Verify the real queue, completed
# grouping and empty-state copy to catch an actual route/render regression.
wait_for "Transfers & retries"
wait_for "Nothing downloading right now"
wait_for "Downloaded"
wait_for "No completed files"
shot downloads-empty
adb shell input keyevent 4
tap Statistics
wait_for "Statistics"
shot statistics
adb shell input keyevent 4
tap "Data & storage"
wait_for "Clear catalog cache"
shot data-storage
adb shell input keyevent 4
tap "Player & reader"
wait_for "DEFAULT SOURCES"
wait_for "No compatible source installed"
shot player-reader-settings
adb shell input keyevent 4
tap "AI & models"
wait_for "AI provider not connected"
wait_for "Message Sora"
shot ai-provider-state
tap "Open menu"
tap "Generated images"
wait_for "Images you create with Sora stay here, beside your AI history."
shot ai-generated-images-empty
tap "Open menu"
tap Files
wait_for "Documents you attach to a chat will appear here."
shot ai-files-empty
tap "Open menu"
tap "New chat"
tap "Message Sora"
adb shell input text "sora-smoke-qa"
tap "Save message"
wait_for "sora-smoke-qa"
wait_for "Messages are stored locally until an AI provider is connected"
shot ai-local-message
# Dismiss the soft keyboard before tapping the AI close affordance. While the
# keyboard is open, Compose reports zero-sized bounds for the header buttons.
adb shell input keyevent 4
wait_for "Message Sora"
tap Close
tap_scrolling "About & help"
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
  # Start chapter navigation from the bottom of a long chapter. This makes
  # a retained LazyListState offset observable instead of letting a tiny fixture
  # fit entirely onscreen.
  for _ in 1 2 3 4 5; do adb shell input swipe 950 1450 950 420 350; done
  # The fixture uses deliberately long verse text; keep scrolling until the
  # chapter footer is attached to the UI tree before tapping it.
  tap_scrolling "Next"
  wait_for "Genesis 2" 20
  wait_for "The heavens and the earth were finished" 20
  python3 - <<'PYASSERT'
import re, xml.etree.ElementTree as ET
root=ET.parse('/tmp/sora-hub-window.xml').getroot()
nodes=list(root.iter('node'))
def bounds(node):
    match=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    return tuple(map(int,match.groups())) if match else None
header=next((bounds(n) for n in nodes if n.attrib.get('text','').strip() == 'Genesis 2' and bounds(n)),None)
verse=next((bounds(n) for n in nodes if n.attrib.get('text','').startswith('The heavens and the earth were finished') and bounds(n)),None)
if header is None or verse is None:
    raise SystemExit('Could not locate Genesis 2 header and verse 1 after Next.')
if not (header[1] <= verse[1] < 1200):
    raise SystemExit(f'Genesis 2 verse 1 is not near the top after Next: header={header}, verse1={verse}')
PYASSERT
  shot bible-next-chapter-top
  tap_scrolling "Previous"
  wait_for "Genesis 1" 20
  wait_for "In the beginning" 20
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
  wait_for "In the beginning"
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
wait_for_any 50 "Featured movie" "TMDB API Read Access Token" "Catalog unavailable" "No movie titles were returned by the current source feed."
if node_exists "TMDB API Read Access Token" || node_exists "Catalog unavailable"; then
  wait_for "Retry"
  wait_for "Manage sources"
  shot movies-source-state
else
  shot movies-live-source
fi
tap Series
wait_for_any 50 "Featured series" "Catalog unavailable" "No TV titles were returned by the current source feed."
if node_exists "Catalog unavailable"; then
  wait_for "Retry"
  shot tv-source-state
else
  shot tv-live-source
fi
tap "Movies & TV"
tap Music
wait_for_any 50 "For you" "Music sources could not load right now." "No Music items were returned by the installed source feed."
if node_exists "Music sources could not load right now."; then
  wait_for "Retry"
  wait_for "Manage sources"
  shot music-source-state
else
  shot music-live-source
fi
tap Music
tap Memes
if wait_for_any 40 "Less like this" "installed-app client ID" "Memes sources could not load right now."; then
  if node_exists "installed-app client ID" || node_exists "Memes sources could not load right now."; then
    # Reddit can decline or time out requests from hosted emulator networks.
    # Treat its explicit unavailable state as valid, without first recording
    # an expected timeout as a failure screenshot.
    wait_for "Retry"
    wait_for "Manage sources"
    shot memes-source-state
  else
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
  fi
else
  echo 'Memes feed showed neither content nor its honest unavailable state.' >&2
  exit 1
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
# Re-check the real Compose boards at 360dp (720x1600 / 2x density).
# This catches layouts that fit the Pixel 7's default viewport but overflow on
# narrower Android phones. Keep this after the full-flow checks so coordinates
# used for the deterministic media validations still refer to the stock device.
adb shell wm size 720x1600
adb shell wm density 320
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 3
tap Games
wait_for "Snake"
tap Snake
wait_for "SCORE"
wait_for "Start"
shot responsive-360dp-snake
tap "All games"
tap "2048"
wait_for "New"
shot responsive-360dp-2048
tap "All games"
tap "Tic-tac-toe"
wait_for "Your turn"
shot responsive-360dp-tictactoe
tap "All games"
tap "Memory match"
wait_for "MOVES"
shot responsive-360dp-memory
tap "All games"
tap_scrolling "Bible trivia"
wait_for "QUESTION"
shot responsive-360dp-trivia
tap "All games"
tap_scrolling "Would you rather?"
wait_for "Make your choice"
shot responsive-360dp-rather
tap "All games"

touch "$OUT/HUB_TABS_PASS"
echo 'Hub tabs emulator walkthrough and 360dp responsive game checks passed.'
