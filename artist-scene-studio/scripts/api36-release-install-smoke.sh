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

adb shell uiautomator dump /sdcard/mise-release-window.xml
adb pull /sdcard/mise-release-window.xml "$PROOF_DIR/mise-release-window.xml"
grep -Fq 'text="Mise"' "$PROOF_DIR/mise-release-window.xml"
adb exec-out screencap -p > "$PROOF_DIR/mise-api36-release.png"
test -s "$PROOF_DIR/mise-api36-release.png"

# Fail the release gate on fatal startup/runtime failures, but ignore historical
# log lines emitted before this process launch.
PID="$(adb shell pidof studio.artistscene.app | tr -d '\r')"
adb logcat -d --pid="$PID" > "$PROOF_DIR/mise-api36-release-logcat.txt" || true
if grep -E 'FATAL EXCEPTION|AndroidRuntime: Process: studio\.artistscene\.app' "$PROOF_DIR/mise-api36-release-logcat.txt"; then
  echo "Fatal Mise runtime error found in release smoke logcat" >&2
  exit 1
fi
