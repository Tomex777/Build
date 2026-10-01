#!/usr/bin/env bash
set -euo pipefail

APK="${1:-/tmp/mise-release/Mise-runtime-release.apk}"
PROOF_DIR="artist-scene-studio"

test -s "$APK"
adb install -r "$APK"
adb shell am force-stop studio.artistscene.app || true
adb shell am start -W -n studio.artistscene.app/.MainActivity

for _ in $(seq 1 30); do
  if adb shell pidof studio.artistscene.app >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

adb shell pidof studio.artistscene.app >/dev/null
sleep 2

WINDOW_XML="$PROOF_DIR/mise-release-window.xml"
rm -f "$WINDOW_XML"
for attempt in 1 2 3; do
  adb shell rm -f /sdcard/mise-release-window.xml >/dev/null 2>&1 || true
  if timeout 20s adb shell uiautomator dump /sdcard/mise-release-window.xml >/tmp/mise-release-uiautomator.txt 2>&1 \
    && timeout 20s adb pull /sdcard/mise-release-window.xml "$WINDOW_XML" >/dev/null 2>&1 \
    && test -s "$WINDOW_XML" \
    && grep -Fq 'text="Mise"' "$WINDOW_XML"; then
    break
  fi
  rm -f "$WINDOW_XML"
  echo "Release UiAutomator proof attempt $attempt failed; retrying" >&2
  sleep 1
done
test -s "$WINDOW_XML"
grep -Fq 'text="Mise"' "$WINDOW_XML"

SCREENSHOT="$PROOF_DIR/mise-api36-release.png"
rm -f "$SCREENSHOT"
for attempt in 1 2 3; do
  if timeout 20s adb exec-out screencap -p > "$SCREENSHOT" && test -s "$SCREENSHOT"; then
    break
  fi
  rm -f "$SCREENSHOT"
  echo "Release screenshot attempt $attempt failed; retrying" >&2
  sleep 1
done
test -s "$SCREENSHOT"

# Fail the release gate on fatal startup/runtime failures, but ignore historical
# log lines emitted before this process launch.
PID="$(adb shell pidof studio.artistscene.app | tr -d '\r')"
adb logcat -d --pid="$PID" > "$PROOF_DIR/mise-api36-release-logcat.txt" || true
if grep -E 'FATAL EXCEPTION|AndroidRuntime: Process: studio\.artistscene\.app' "$PROOF_DIR/mise-api36-release-logcat.txt"; then
  echo "Fatal Mise runtime error found in release smoke logcat" >&2
  exit 1
fi
