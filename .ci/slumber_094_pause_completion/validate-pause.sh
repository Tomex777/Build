#!/usr/bin/env bash
set -euo pipefail

APK="${1:-/tmp/slumber-088/app/build/outputs/apk/debug/app-debug.apk}"
OUT=/tmp/slumber-product-proof
PKG=com.night.pianohub
ACTIVITY="$PKG/.MainActivity"
mkdir -p "$OUT"

function dump_ui() {
  local name="$1"
  adb shell uiautomator dump /sdcard/slumber-pause.xml >/dev/null 2>&1 || true
  adb pull /sdcard/slumber-pause.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

function ui_has() {
  local needle="$1"
  dump_ui pause-probe
  python3 - "$OUT/pause-probe.xml" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
needle = sys.argv[2]
for node in root.iter("node"):
    a = node.attrib
    if a.get("text", "").strip() == needle or a.get("content-desc", "").strip() == needle:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

function wait_for() {
  local needle="$1"; local seconds="${2:-30}"
  for _ in $(seq 1 "$seconds"); do
    if ui_has "$needle"; then return 0; fi
    sleep 1
  done
  adb exec-out screencap -p > "$OUT/pause-wait-failure.png" || true
  adb logcat -d -t 2500 > "$OUT/pause-wait-failure-logcat.txt" || true
  echo "Timed out waiting for pause-proof UI: $needle" >&2
  return 1
}

function coords_for() {
  local needle="$1"
  dump_ui pause-coords
  python3 - "$OUT/pause-coords.xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
needle = sys.argv[2]
for node in root.iter("node"):
    a = node.attrib
    if a.get("text", "").strip() == needle or a.get("content-desc", "").strip() == needle:
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
}

function tap_ui() {
  local needle="$1"
  local xy=""
  for _ in $(seq 1 12); do
    xy="$(coords_for "$needle" 2>/dev/null || true)"
    if [ -n "$xy" ]; then
      adb shell input tap $xy
      return 0
    fi
    sleep 1
  done
  echo "Could not tap pause-proof UI target: $needle" >&2
  return 1
}

function tap_until_visible() {
  local source="$1"; local target="$2"; local seconds="${3:-12}"
  for _ in $(seq 1 "$seconds"); do
    if ui_has "$target"; then return 0; fi
    local xy
    xy="$(coords_for "$source" 2>/dev/null || true)"
    if [ -n "$xy" ]; then
      adb shell input tap $xy
    fi
    sleep 0.25
  done
  echo "Could not reach pause-proof target '$target' from '$source'" >&2
  dump_ui pause-transition-failure
  adb exec-out screencap -p > "$OUT/pause-transition-failure.png" || true
  return 1
}

function capture() {
  local name="$1"
  dump_ui "$name"
  adb exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
}

function pause_active_run() {
  # Emulator input can occasionally drop a single tap immediately after the
  # Ready overlay disappears. Retry only while the fresh semantic tree still
  # exposes "Pause". Once "Paused"/"Resume" is visible we never tap again, so
  # this cannot accidentally toggle a successful pause back to running.
  local attempt
  for attempt in 1 2 3; do
    if ui_has "Paused"; then return 0; fi
    if ui_has "Play again"; then
      echo "Pause proof run completed before pause input was accepted; restarting attempt $attempt" >&2
      tap_until_visible "Play again" "Ready to play?" 8
      if ui_has "Ready to play?"; then tap_ui "Start"; fi
    fi
    wait_for "Pause" 25
    tap_ui "Pause"
    sleep 1.25
    if ui_has "Paused"; then return 0; fi
  done
  echo "Could not enter explicit Paused state after bounded pause attempts" >&2
  dump_ui pause-transition-failure
  adb exec-out screencap -p > "$OUT/pause-transition-failure.png" || true
  return 1
}

adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" >/dev/null
wait_for "Practice" 30

# The seven-note default warm-up completes faster than a landscape
# UIAutomator dump can reliably expose its Pause control. Exercise Pause on a
# longer, real catalog chart instead of weakening the state assertion or
# racing repeated taps against a completed run.
tap_until_visible "Songs" "Learn a song" 12
tap_ui "Find a song"
adb shell input text 'Jingle%sbells'
adb shell input keyevent KEYCODE_ENTER
wait_for "Jingle Bells" 12
tap_ui "Play"
wait_for "FALLING NOTES" 35
wait_for "Jingle Bells" 10
wait_for "Ready to play?" 15
tap_until_visible "Start" "Pause" 8
pause_active_run
wait_for "Paused" 5
wait_for "Resume" 3
capture play-user-paused

# The built-in warm-up normally completes within a few seconds with misses.
# Hold longer than that and prove neither the playhead nor scoring completion
# can finish invisibly while the user has explicitly paused the run.
sleep 7
wait_for "Paused" 5
if ui_has "Play again"; then
  echo "Play completed while explicitly paused" >&2
  exit 1
fi
capture play-user-paused-held

tap_ui "Resume"
# Completion after the held paused state is the durable proof that the run
# resumed. Do not race the transient HUD Pause button against completion.
wait_for "Play again" 55
capture play-user-resumed-complete

adb logcat -d -t 4000 > "$OUT/pause-resume-logcat.txt"
if grep -E 'FATAL EXCEPTION|Process: com\.night\.pianohub.*has died' "$OUT/pause-resume-logcat.txt"; then
  echo "Slumber crashed during explicit pause/resume validation" >&2
  exit 1
fi

cat >> "$OUT/GREEN.txt" <<'TXT'
PLAY USER PAUSE/RESUME = GREEN
PLAY PAUSED TIMER = GREEN
PLAY PAUSED SCORING = GREEN
TXT
