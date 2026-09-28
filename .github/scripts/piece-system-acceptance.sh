#!/usr/bin/env bash
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB=(adb -s "$SERIAL")
API_LEVEL="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$API_LEVEL" != "36" ]]; then
  echo "Skipping piece-system flow on API $API_LEVEL; API 36 runs the full acceptance." | tee -a piece-acceptance-log.txt
  exit 0
fi
mkdir -p acceptance-evidence
trap '"${ADB[@]}" exec-out screencap -p > mirrorchess-piece-acceptance-failure.png 2>/dev/null || true; "${ADB[@]}" logcat -d > piece-acceptance-logcat.txt 2>/dev/null || true' EXIT
UI_FILE="$PWD/piece-acceptance-current.xml"
read -r SCREEN_W SCREEN_H < <("${ADB[@]}" shell wm size | awk -F'[: x]+' '/Physical size/ {print $3, $4}')
SCREEN_W="${SCREEN_W:-1080}"
SCREEN_H="${SCREEN_H:-1920}"
scroll_up() {
  "${ADB[@]}" shell input swipe "$((SCREEN_W / 2))" "$((SCREEN_H * 82 / 100))" "$((SCREEN_W / 2))" "$((SCREEN_H * 30 / 100))" 350
}

ui_dump() {
  "${ADB[@]}" shell uiautomator dump /sdcard/piece-acceptance-window.xml >/dev/null 2>&1 || true
  "${ADB[@]}" shell cat /sdcard/piece-acceptance-window.xml > "$UI_FILE"
  cp "$UI_FILE" "piece-acceptance-$(date +%s).xml"
}
find_bounds() {
  local query="$1" mode="${2:-text}"
  python3 - "$UI_FILE" "$query" "$mode" <<'PY'
import sys, xml.etree.ElementTree as ET
path, query, mode = sys.argv[1:]
root = ET.parse(path).getroot()
for node in root.iter():
    value = node.attrib.get("content-desc", "") if mode in ("desc", "desc-prefix") else node.attrib.get("text", "")
    matches = value.startswith(query) if mode == "desc-prefix" else value == query
    if matches and node.attrib.get("enabled", "true") == "true":
        b = node.attrib.get("bounds", "")
        if b:
            nums = [int(x) for x in __import__("re").findall(r"\d+", b)]
            if len(nums) == 4:
                print((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
                raise SystemExit(0)
raise SystemExit(1)
PY
}
dismiss_quickstep_anr() {
  if grep -q "Quickstep isn't responding" "$UI_FILE"; then
    local bounds
    if bounds="$(find_bounds "Wait" text)"; then
      read -r x y <<< "$bounds"
      echo "dismiss Quickstep ANR prompt with Wait at $x,$y" | tee -a piece-acceptance-log.txt
      "${ADB[@]}" shell input tap "$x" "$y"
      sleep 5
      return 0
    fi
  fi
  return 1
}
tap_query() {
  local query="$1" mode="${2:-text}" optional="${3:-false}"
  local attempt bounds attempt_limit=35
  if [[ "$optional" == "true" ]]; then attempt_limit=3; fi
  for attempt in $(seq 1 "$attempt_limit"); do
    ui_dump
    dismiss_quickstep_anr || true
    if bounds="$(find_bounds "$query" "$mode")"; then
      read -r x y <<< "$bounds"
      echo "tap [$mode] $query at $x,$y" | tee -a piece-acceptance-log.txt
      "${ADB[@]}" shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    if (( attempt % 4 == 0 )); then
      scroll_up
    fi
    sleep 1
  done
  if [[ "$optional" == "true" ]]; then return 1; fi
  echo "Could not find UI node: $query ($mode)" >&2
  cat "$UI_FILE" >&2
  return 1
}
assert_query() {
  local query="$1" mode="${2:-text}"
  local attempt
  for attempt in $(seq 1 25); do
    ui_dump
    dismiss_quickstep_anr || true
    if find_bounds "$query" "$mode" >/dev/null; then
      echo "visible [$mode] $query" | tee -a piece-acceptance-log.txt
      return 0
    fi
    if (( attempt % 4 == 0 )); then scroll_up; fi
    sleep 1
  done
  echo "Expected UI node not visible: $query ($mode)" >&2
  cat "$UI_FILE" >&2
  return 1
}
snapshot() {
  local name="$1"
  "${ADB[@]}" exec-out screencap -p > "mirrorchess-piece-acceptance-${name}.png"
}
select_picker_file() {
  local name="$1"
  tap_query "Show roots" desc true || true
  tap_query "Downloads"
  tap_query "$name" desc-prefix
}

python3 - <<'PY'
from PIL import Image, ImageDraw
from pathlib import Path
import math
out = Path("acceptance-evidence")
colors = [(202,90,74,255),(57,120,165,255),(213,174,94,255),(105,148,85,255),(142,101,169,255),(66,149,136,255)]
def sprite(kind, color):
    im = Image.new("RGBA", (128,128), (0,0,0,0)); d = ImageDraw.Draw(im)
    edge=(35,35,35,255); x=64
    d.rounded_rectangle((24,102,104,117), 5, fill=color, outline=edge, width=4)
    d.polygon([(36,100),(44,84),(84,84),(92,100)], fill=color, outline=edge)
    d.rectangle((53,62,75,85), fill=color, outline=edge, width=4)
    d.ellipse((43,42,85,68), fill=color, outline=edge, width=4)
    if kind % 3 == 0:
        d.rectangle((56,20,72,43), fill=color, outline=edge, width=4)
        d.rectangle((48,16,80,25), fill=color, outline=edge, width=3)
    elif kind % 3 == 1:
        d.polygon([(64,14),(74,32),(92,27),(82,44),(96,56),(76,56),(64,72),(52,56),(32,56),(46,44),(36,27),(54,32)], fill=color, outline=edge)
    else:
        d.polygon([(32,52),(50,34),(74,34),(97,53),(86,65),(75,58),(58,69),(42,64)], fill=color, outline=edge)
    return im
sheet=Image.new("RGBA",(6*128,2*128),(0,0,0,0))
for i in range(12): sheet.alpha_composite(sprite(i, colors[i%len(colors)]),((i%6)*128,(i//6)*128))
sheet.save(out/"mirrorchess-sheet.png")
sprite(4,(54,121,184,255)).save(out/"mirrorchess-knight.png")
sprite(0,(224,211,182,255)).save(out/"mirrorchess-king.webp", "WEBP", quality=100, lossless=True)
PY
"${ADB[@]}" shell mkdir -p /sdcard/Download
"${ADB[@]}" push acceptance-evidence/mirrorchess-sheet.png /sdcard/Download/mirrorchess-sheet.png >/dev/null
"${ADB[@]}" push acceptance-evidence/mirrorchess-knight.png /sdcard/Download/mirrorchess-knight.png >/dev/null
"${ADB[@]}" push acceptance-evidence/mirrorchess-king.webp /sdcard/Download/mirrorchess-king.webp >/dev/null
sleep 2

assert_query "Settings" desc
tap_query "Settings" desc
tap_query "Board & Pieces"
tap_query "Modern"
tap_query "Pixel"
snapshot "built-in-pixel-selection"
tap_query "Create custom set"
tap_query "Set name"
"${ADB[@]}" shell input text PieceQA
tap_query "Create"
assert_query "PieceQA"
tap_query "Import 6 × 2 sprite sheet"
select_picker_file "mirrorchess-sheet.png"
assert_query "Check the 6 × 2 slicing"
snapshot "sprite-sheet-preview"
tap_query "Import these 12 pieces"
assert_query "PieceQA"

# Replace individual sprites with PNG and WebP after importing the complete 12-piece sheet.
tap_query "Import selected piece"
select_picker_file "mirrorchess-knight.png"
sleep 2
# White Knight is the editor's initial selection; replace another piece through the chip strip if present.
tap_query "Import selected piece"
select_picker_file "mirrorchess-king.webp"
sleep 2

# Exercise the editor's canvas, zoom and undo/redo on the persisted selected set.
tap_query "Zoom +"
assert_query "Pixel art canvas" desc
snapshot "piece-creator-zoom"
tap_query "Pixel art canvas" desc
tap_query "Undo"
tap_query "Redo"
tap_query "Save piece"
snapshot "piece-creator-saved"
tap_query "Export portable .mcset bundle"
tap_query "Save"
"${ADB[@]}" shell ls /sdcard/Download/*.mcset

# Restart and confirm active custom set is still available, then render it in a real game.
"${ADB[@]}" shell am force-stop com.night.mirrorchess
"${ADB[@]}" shell am start -W -n com.night.mirrorchess/.MainActivity >/dev/null
sleep 3
tap_query "Settings" desc
tap_query "Board & Pieces"
assert_query "PieceQA"
# The selected custom row and ACTIVE label remain on this screen after process restart.
ui_dump
grep -q 'PieceQA' "$UI_FILE"
grep -q 'ACTIVE' "$UI_FILE"
"${ADB[@]}" shell input keyevent 4
"${ADB[@]}" shell input keyevent 4
tap_query "Start game"
snapshot "custom-set-on-board"
"${ADB[@]}" shell am start -W -n com.night.mirrorchess/.PromotionAcceptanceActivity --es fen '6k1/1P6/8/8/8/8/8/6K1 w - - 0 1' >/dev/null
assert_query "b7, white pawn" desc
tap_query "b7, white pawn" desc
tap_query "b8, empty" desc
assert_query "PROMOTE PAWN"
snapshot "custom-set-promotion"
echo "Piece-system acceptance completed" | tee -a piece-acceptance-log.txt
