#!/usr/bin/env bash
set -euo pipefail

APK="${VEYA_APK:?VEYA_APK must point to the built APK}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHOT_DIR="$ROOT/build/emulator-screenshots"
REPORT_DIR="$ROOT/build/ci-report"
mkdir -p "$SHOT_DIR" "$REPORT_DIR"

adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb install -r -g "$APK"

adb shell am force-stop com.veya.app || true
adb shell am start -W -n com.veya.app/.MainActivity | tee "$REPORT_DIR/am-start.txt"

for _ in $(seq 1 20); do
  if adb shell pidof com.veya.app >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

PID="$(adb shell pidof com.veya.app | tr -d '\r' || true)"
if [[ -z "$PID" ]]; then
  echo "Veya process is not running" >&2
  adb logcat -d > "$REPORT_DIR/logcat.txt" || true
  exit 1
fi

echo "$PID" > "$REPORT_DIR/pid.txt"
adb shell dumpsys activity activities > "$REPORT_DIR/activities.txt"
adb shell dumpsys package com.veya.app > "$REPORT_DIR/package.txt"
adb logcat -d > "$REPORT_DIR/logcat.txt" || true

if ! grep -q 'com.veya.app/.MainActivity' "$REPORT_DIR/activities.txt"; then
  echo "Veya MainActivity is not present in activity state" >&2
  exit 1
fi

capture() {
  local name="$1"
  sleep 1
  adb exec-out screencap -p > "$SHOT_DIR/$name.png"
  test -s "$SHOT_DIR/$name.png"
  local bytes
  bytes="$(wc -c < "$SHOT_DIR/$name.png")"
  if [[ "$bytes" -lt 10000 ]]; then
    echo "Screenshot $name is suspiciously small: $bytes bytes" >&2
    exit 1
  fi
}

tap_ui_text() {
  local needle="$1"
  local attempts="${2:-20}"
  local xml="$REPORT_DIR/window-tap.xml"

  for _ in $(seq 1 "$attempts"); do
    adb shell uiautomator dump /sdcard/veya-window-tap.xml >/dev/null 2>&1 || true
    adb pull /sdcard/veya-window-tap.xml "$xml" >/dev/null 2>&1 || true

    if [[ -s "$xml" ]]; then
      local coords
      coords="$(python3 - "$xml" "$needle" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    raise SystemExit(1)

matches = []
for node in root.iter("node"):
    text = (node.attrib.get("text") or "").strip()
    desc = (node.attrib.get("content-desc") or "").strip()
    if text == needle or desc == needle:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            if x2 > x1 and y2 > y1:
                matches.append(((x1 + x2) // 2, (y1 + y2) // 2))

if matches:
    print(f"{matches[0][0]} {matches[0][1]}")
PY
)" || true
      if [[ "$coords" =~ ^[0-9]+[[:space:]][0-9]+$ ]]; then
        read -r x y <<<"$coords"
        adb shell input tap "$x" "$y"
        return 0
      fi
    fi
    sleep 1
  done

  echo "Could not find tappable UI text: $needle" >&2
  return 1
}

capture home

tap_ui_text "Library"
capture library
if cmp -s "$SHOT_DIR/home.png" "$SHOT_DIR/library.png"; then
  echo "Library navigation did not change the rendered surface" >&2
  exit 1
fi

tap_ui_text "Downloads"
capture downloads
if cmp -s "$SHOT_DIR/library.png" "$SHOT_DIR/downloads.png"; then
  echo "Downloads navigation did not change the rendered surface" >&2
  exit 1
fi

tap_ui_text "Settings"
capture settings
if cmp -s "$SHOT_DIR/downloads.png" "$SHOT_DIR/settings.png"; then
  echo "Settings navigation did not change the rendered surface" >&2
  exit 1
fi

tap_ui_text "About Veya"
capture about
if cmp -s "$SHOT_DIR/settings.png" "$SHOT_DIR/about.png"; then
  echo "About navigation did not change the rendered surface" >&2
  exit 1
fi

if [[ "${VEYA_LIVE_PLAYBACK_PROOF:-0}" == "1" ]]; then
  adb logcat -c || true
  adb shell am force-stop com.veya.app || true
  adb shell am start -W \
    -a android.intent.action.VIEW \
    -d "https://youtu.be/dQw4w9WgXcQ" \
    -n com.veya.app/.MainActivity \
    | tee "$REPORT_DIR/live-link-start.txt"

  if ! tap_ui_text "Play" 45; then
    capture live-details-failure
    exit 1
  fi

  player_seen=0
  for _ in $(seq 1 30); do
    adb shell dumpsys activity activities > "$REPORT_DIR/live-activities.txt"
    if grep -q 'com.veya.app/.player.VeyaPlayerActivity' "$REPORT_DIR/live-activities.txt"; then
      player_seen=1
      break
    fi
    sleep 1
  done
  if [[ "$player_seen" -ne 1 ]]; then
    echo "VeyaPlayerActivity did not reach the foreground" >&2
    capture live-player-missing
    exit 1
  fi

  playback_proven=0
  for _ in $(seq 1 90); do
    adb logcat -d -v brief > "$REPORT_DIR/live-logcat.txt" || true
    if grep -q 'VeyaVLC.*audioSlaveAdded=true' "$REPORT_DIR/live-logcat.txt" &&
       grep -Eq 'VeyaVLC.*frameProof pictures=[1-9][0-9]*.*positionMs=[1-9][0-9]*' "$REPORT_DIR/live-logcat.txt"; then
      playback_proven=1
      break
    fi
    sleep 1
  done

  capture live-player

  if [[ "$playback_proven" -ne 1 ]]; then
    echo "libVLC did not prove rendered video with its adaptive audio slave" >&2
    exit 1
  fi

  # Prove engine-provided WebVTT captions reach libVLC and playback keeps advancing.
  adb logcat -c || true
  if ! tap_ui_text "CC" 20; then
    echo "Caption control was not exposed for a video with subtitle tracks" >&2
    capture live-caption-control-missing
    exit 1
  fi
  if ! tap_ui_text "English" 20; then
    echo "Expected live English caption track was not exposed" >&2
    capture live-caption-track-missing
    exit 1
  fi

  caption_proven=0
  for _ in $(seq 1 45); do
    adb logcat -d -v brief > "$REPORT_DIR/live-caption-logcat.txt" || true
    if grep -q 'VeyaVLC.*subtitleSelected=en|English|manual|.en' "$REPORT_DIR/live-caption-logcat.txt" &&
       grep -Eq 'VeyaVLC.*frameProof pictures=[1-9][0-9]*.*positionMs=[1-9][0-9]*' "$REPORT_DIR/live-caption-logcat.txt"; then
      caption_proven=1
      break
    fi
    sleep 1
  done

  capture live-player-subtitles

  if [[ "$caption_proven" -ne 1 ]]; then
    echo "WebVTT subtitle selection did not remain active while video advanced" >&2
    exit 1
  fi

  echo "livePlayback=PASS" >> "$REPORT_DIR/status.txt"
  echo "liveCaptions=PASS" >> "$REPORT_DIR/status.txt"
  echo "liveVideoId=dQw4w9WgXcQ" >> "$REPORT_DIR/status.txt"

  # Complete a real adaptive download, restart the app, then prove local-file
  # playback still renders video with its downloaded audio slave.
  adb shell input keyevent KEYCODE_BACK
  if ! tap_ui_text "Download" 30; then
    echo "Video details did not expose Download after returning from playback" >&2
    capture download-action-missing
    exit 1
  fi

  if ! tap_ui_text "Available offline" 300; then
    echo "Download did not reach a durable COMPLETE state" >&2
    capture download-incomplete
    exit 1
  fi
  capture download-complete

  adb shell am force-stop com.veya.app || true
  adb shell am start -W -n com.veya.app/.MainActivity \
    | tee "$REPORT_DIR/offline-restart-start.txt"

  if ! tap_ui_text "Downloads" 30; then
    echo "Downloads destination was not reachable after restart" >&2
    capture downloads-after-restart-missing
    exit 1
  fi
  capture downloads-after-restart

  adb logcat -c || true
  if ! tap_ui_text "Play" 30; then
    echo "Completed download was not restored with a Play action" >&2
    capture offline-play-action-missing
    exit 1
  fi

  offline_player_seen=0
  for _ in $(seq 1 30); do
    adb shell dumpsys activity activities > "$REPORT_DIR/offline-activities.txt"
    if grep -q 'com.veya.app/.player.VeyaPlayerActivity' "$REPORT_DIR/offline-activities.txt"; then
      offline_player_seen=1
      break
    fi
    sleep 1
  done
  if [[ "$offline_player_seen" -ne 1 ]]; then
    echo "Offline VeyaPlayerActivity did not reach the foreground" >&2
    capture offline-player-missing
    exit 1
  fi

  offline_playback_proven=0
  for _ in $(seq 1 60); do
    adb logcat -d -v brief > "$REPORT_DIR/offline-logcat.txt" || true
    if grep -q 'VeyaVLC.*audioSlaveAdded=true offline=true' "$REPORT_DIR/offline-logcat.txt" &&
       grep -Eq 'VeyaVLC.*frameProof pictures=[1-9][0-9]*.*positionMs=[1-9][0-9]*' "$REPORT_DIR/offline-logcat.txt"; then
      offline_playback_proven=1
      break
    fi
    sleep 1
  done

  capture offline-player

  if [[ "$offline_playback_proven" -ne 1 ]]; then
    echo "Downloaded local video/audio did not prove libVLC playback after restart" >&2
    exit 1
  fi

  echo "offlineDownloadRestartPlayback=PASS" >> "$REPORT_DIR/status.txt"
fi

adb shell uiautomator dump /sdcard/veya-window.xml >/dev/null 2>&1 || true
adb pull /sdcard/veya-window.xml "$REPORT_DIR/window.xml" >/dev/null 2>&1 || true

if [[ -f "$REPORT_DIR/window.xml" ]] && ! grep -q 'Veya\|Settings\|Downloads' "$REPORT_DIR/window.xml"; then
  echo "Warning: accessibility dump did not expose expected Veya text" | tee "$REPORT_DIR/ui-warning.txt"
fi

if grep -E 'FATAL EXCEPTION|Process: com\.veya\.app.*FATAL' "$REPORT_DIR/logcat.txt"; then
  echo "Fatal exception detected in Veya logcat" >&2
  exit 1
fi

echo "runtime=PASS" | tee -a "$REPORT_DIR/status.txt"
echo "package=com.veya.app" >> "$REPORT_DIR/status.txt"
echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')" >> "$REPORT_DIR/status.txt"
