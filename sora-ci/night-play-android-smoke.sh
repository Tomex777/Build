#!/usr/bin/env bash
set -euo pipefail
OUT=/tmp/sora-night-play-android
mkdir -p "$OUT"
adb wait-for-device
adb install -r "$SORA_ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
adb shell am force-stop com.night.sora
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 3

dump_ui() {
  adb shell uiautomator dump /sdcard/sora-night-ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/sora-night-ui.xml /tmp/sora-night-ui.xml >/dev/null 2>&1 || true
}
find_point() {
  python3 - "$1" <<'PY'
import re,sys,xml.etree.ElementTree as ET
target=sys.argv[1].lower()
try: root=ET.parse('/tmp/sora-night-ui.xml').getroot()
except Exception: raise SystemExit(1)
exact=[];partial=[]
for n in root.iter('node'):
    d=n.attrib
    value=(d.get('text','') or d.get('content-desc','')).strip()
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',d.get('bounds',''))
    if not value or not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2<=x1 or y2<=y1: continue
    coords=(int((x1+x2)/2),int((y1+y2)/2))
    if value.lower()==target: exact.append(coords)
    elif target in value.lower(): partial.append(coords)
matches=exact or partial
if not matches: raise SystemExit(1)
print(*matches[0])
PY
}
tap_text() {
  local label="$1" size width height
  size=$(adb shell wm size | grep -Eo '[0-9]+x[0-9]+' | tail -n 1)
  width="${size%x*}"; height="${size#*x}"
  for attempt in $(seq 1 22); do
    dump_ui
    if find_point "$label" > /tmp/sora-night-point.txt; then
      read -r x y < /tmp/sora-night-point.txt
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    adb shell input swipe "$((width/2))" "$((height*78/100))" "$((width/2))" "$((height*27/100))" 340
    sleep .35
  done
  adb exec-out screencap -p > "$OUT/failure-${label//[^A-Za-z0-9]/_}.png"
  echo "NIGHT Play entry not tappable: $label" >&2
  return 1
}
wait_web_title() {
  local label="$1"
  for attempt in $(seq 1 20); do
    dump_ui
    if grep -Fqi 'android.webkit.WebView' /tmp/sora-night-ui.xml && find_point "$label" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  adb exec-out screencap -p > "$OUT/failure-web-${label//[^A-Za-z0-9]/_}.png"
  echo "Embedded game did not render its real HTML heading: $label" >&2
  sed -e 's/></>\n</g' /tmp/sora-night-ui.xml | grep -E 'text=|class=' | tail -n 35 >&2 || true
  return 1
}
shot() {
  adb exec-out screencap -p > "$OUT/$1.png"
  test "$(wc -c < "$OUT/$1.png")" -gt 10000
}

tap_text Games
# One useful game/category filter, without duplicate search/list controls.
tap_text "Puzzle"
dump_ui
if ! find_point "Water Sort" >/dev/null 2>&1; then
  echo 'Puzzle filter did not expose Water Sort' >&2
  exit 1
fi
if find_point "Snake" >/dev/null 2>&1; then
  echo 'Puzzle filter incorrectly includes the Arcade-only Snake game' >&2
  exit 1
fi
tap_text "All"

# Verify the embedded engines and authored story are really offline. Internet
# is disabled after installing the app; screenshots and taps use USB/ADB only.
adb shell svc wifi disable >/dev/null 2>&1 || true
adb shell svc data disable >/dev/null 2>&1 || true
sleep 2

# Each selected game must render via the locally bundled assets, enter its
# real Canvas/DOM interface, accept a physical tap, then return to Sora.
tested_games=0
# ADB can read standard input. Keep the game table on dedicated FD 3 so
# no subprocess can consume the remaining rows of this acceptance suite.
while IFS='|' read -r id title <&3; do
  [[ -z "$id" ]] && continue
  tested_games=$((tested_games + 1))
  tap_text "$title"
  wait_web_title "$title"
  shot "$id-loaded"
  # Exercise Android touch dispatch. The HTML engine itself responds to
  # pointer input; animations/visuals are inspected from screenshots.
  width=$(adb shell wm size | grep -Eo '[0-9]+x[0-9]+' | tail -n1 | cut -dx -f1)
  height=$(adb shell wm size | grep -Eo '[0-9]+x[0-9]+' | tail -n1 | cut -dx -f2)
  adb shell input tap "$((width/2))" "$((height*55/100))"
  sleep .4
  shot "$id-after-touch"
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  dump_ui
  if grep -Fq 'android.webkit.WebView' /tmp/sora-night-ui.xml; then
    echo "Android Back stayed inside embedded game instead of returning: $id" >&2
    exit 1
  fi
done 3<<'GAMES'
last-call|The Last Call
wing-hop|Wing Hop
block-drop|Block Drop
minesweeper|Minesweeper
maze-chase|Maze Chase
fruit-slice|Fruit Slice
sky-jumper|Sky Jumper
endless-runner|Endless Runner
space-shooter|Space Shooter
brick-breaker|Brick Breaker
sudoku|Sudoku
connect-four|Connect Four
chess|Chess
draughts|Draughts
whot|WHOT
colour-clash|Colour Clash
naija-ludo|Nigerian Ludo
snakes-ladders|Snakes & Ladders
night-estates|NIGHT Estates
jigsaw|Jigsaw Puzzle
piano-tiles|Piano Tiles
sling-siege|Sling Siege
GAMES
if (( tested_games != 22 )); then
  echo "Only tested $tested_games of 22 NIGHT Play experiences!" >&2
  exit 1
fi

# A selected game's native Back restores Sora's Games tab. Go to the first
# part of the list before asserting the native Water Sort entry.
dimensions=$(adb shell wm size | grep -Eo '[0-9]+x[0-9]+' | tail -n 1)
width="${dimensions%x*}"; height="${dimensions#*x}"
for _ in $(seq 1 12); do
  adb shell input swipe "$((width/2))" "$((height*25/100))" "$((width/2))" "$((height*83/100))" 160 </dev/null
done
tap_text "Water Sort"
dump_ui
if ! find_point "LEVEL 1" >/dev/null 2>&1 || ! find_point "MOVES" >/dev/null 2>&1; then
  shot "failure-water-sort"
  echo 'Native Water Sort board did not load' >&2
  exit 1
fi
shot native-water-sort
adb shell input keyevent KEYCODE_BACK
echo "22 bundled NIGHT Play experiences plus Water Sort launched with Android touch and Back."
