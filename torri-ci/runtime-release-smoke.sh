#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${1:?usage: runtime-release-smoke.sh <api-level>}"
ROOT="$GITHUB_WORKSPACE/torri-output"
EVIDENCE_DIR="${RELEASE_EVIDENCE_DIR:?RELEASE_EVIDENCE_DIR must be set}"
SEED_APK="$ROOT/Torri-x86_64-release-seed.apk"
RELEASE_APK="$ROOT/Torri-1.0.0-universal-release-QA-debugsigned.apk"
PACKAGE="app.torri"
MAIN_ACTIVITY="eu.kanade.tachiyomi.ui.main.MainActivity"
BOOTSTRAP_ACTIVITY="eu.kanade.tachiyomi.ui.ci.TorriCiBootstrapActivity"
SENTINEL="/sdcard/Android/data/$PACKAGE/files/TorriCiStorage/.bootstrap-complete"

mkdir -p "$EVIDENCE_DIR"

capture() {
  local name="$1"
  adb -s emulator-5554 exec-out screencap -p > "$EVIDENCE_DIR/$name.png"
  file "$EVIDENCE_DIR/$name.png" | grep -q 'PNG image data'
  test "$(stat -c%s "$EVIDENCE_DIR/$name.png")" -gt 9000
}

dump_ui() {
  adb -s emulator-5554 shell uiautomator dump /sdcard/torri-release.xml >/dev/null
  adb -s emulator-5554 pull /sdcard/torri-release.xml "$EVIDENCE_DIR/window.xml" >/dev/null
}

find_coords() {
  local needle="$1"
  python3 - "$EVIDENCE_DIR/window.xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
needle = sys.argv[2]
for wanted in (2, 1):
    for node in root.iter("node"):
        vals = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        score = 2 if any(v == needle for v in vals) else 1 if any(needle.casefold() in v.casefold() for v in vals if v) else 0
        if score != wanted:
            continue
        pts = re.findall(r"\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if len(pts) == 2:
            (x1,y1),(x2,y2)=((int(x),int(y)) for x,y in pts)
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_text() {
  local needle="$1" attempts="${2:-15}"
  for _ in $(seq 1 "$attempts"); do
    dump_ui
    if find_coords "$needle" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "Timed out waiting for release UI text: $needle" >&2
  return 1
}

tap_text() {
  local needle="$1" coords=""
  for _ in $(seq 1 7); do
    dump_ui
    coords="$(find_coords "$needle" 2>/dev/null || true)"
    if [[ -n "$coords" ]]; then
      read -r x y <<<"$coords"
      adb -s emulator-5554 shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    adb -s emulator-5554 shell input swipe 540 1600 540 650 300
    sleep 1
  done
  echo "Could not find release UI text: $needle" >&2
  return 1
}

tap_if_present() {
  local needle="$1" coords=""
  dump_ui
  coords="$(find_coords "$needle" 2>/dev/null || true)"
  [[ -n "$coords" ]] || return 1
  read -r x y <<<"$coords"
  adb -s emulator-5554 shell input tap "$x" "$y"
  sleep 1
}

wait_for_focus() {
  for _ in $(seq 1 30); do
    local focus
    focus="$(adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
    if [[ "$focus" == *"$PACKAGE"* ]]; then return 0; fi
    if [[ "$focus" == *"CrashActivity"* || "$focus" == *"has stopped"* ]]; then
      echo "Release entered an error surface: $focus" >&2
      return 1
    fi
    sleep 1
  done
  return 1
}

show_reader_controls() {
  dump_ui
  if find_coords "Reading mode" >/dev/null 2>&1; then return 0; fi
  adb -s emulator-5554 shell input tap 540 960
  wait_for_text "Reading mode" 10
}

test -s "$SEED_APK"
test -s "$RELEASE_APK"

# Seed deterministic data with a debug-only APK that has the production package id.
adb -s emulator-5554 uninstall "$PACKAGE" >/dev/null 2>&1 || true
adb -s emulator-5554 install "$SEED_APK"
adb -s emulator-5554 shell rm -f "$SENTINEL" >/dev/null 2>&1 || true
adb -s emulator-5554 shell am start -n "$PACKAGE/$BOOTSTRAP_ACTIVITY" > "$EVIDENCE_DIR/seed-bootstrap.txt"
ready=false
for _ in $(seq 1 90); do
  if adb -s emulator-5554 shell "test -s '$SENTINEL'" >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]]
adb -s emulator-5554 shell am force-stop "$PACKAGE"

# Replace the seed with the actual non-debuggable release APK while preserving seeded app data.
adb -s emulator-5554 install -r "$RELEASE_APK" | tee "$EVIDENCE_DIR/release-install.txt"
adb -s emulator-5554 shell dumpsys package "$PACKAGE" > "$EVIDENCE_DIR/package.txt"
grep -q 'versionName=1.0.0' "$EVIDENCE_DIR/package.txt"
grep -q 'versionCode=10000' "$EVIDENCE_DIR/package.txt"
if grep -q 'TorriCiBootstrapActivity' "$EVIDENCE_DIR/package.txt"; then
  echo "Debug-only bootstrap activity leaked into release package" >&2
  exit 1
fi

adb -s emulator-5554 logcat -c || true
adb -s emulator-5554 shell am start -W -n "$PACKAGE/$MAIN_ACTIVITY" | tee "$EVIDENCE_DIR/launch.txt"
wait_for_focus
sleep 3
wait_for_text "Library" 15
capture "release-01-library"

tap_text "More"
wait_for_text "Downloaded only" 12
capture "release-02-more"

tap_text "Settings"
wait_for_text "Appearance" 12
capture "release-03-settings"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "About"
wait_for_text "Torri" 12
capture "release-04-about"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "Browse"
wait_for_text "Sources" 12
tap_text "Sources"
tap_text "Local source"
wait_for_text "Torri Red" 15
capture "release-05-local-source"

tap_text "Torri Red"
wait_for_text "Chapter 1" 15
capture "release-06-details"
tap_text "Chapter 1"
sleep 4
tap_if_present "Got it" || true
wait_for_focus
capture "release-07-reader"
show_reader_controls
capture "release-08-reader-controls"

if [[ "$API_LEVEL" == "36" ]]; then
  # Exercise real release process death/re-entry on the same seeded manga.
  adb -s emulator-5554 shell input tap 540 960
  adb -s emulator-5554 shell input tap 540 2100 || true
  sleep 1
  adb -s emulator-5554 shell am force-stop "$PACKAGE"
  adb -s emulator-5554 shell am start -W -n "$PACKAGE/$MAIN_ACTIVITY" > "$EVIDENCE_DIR/relaunch.txt"
  wait_for_focus
  wait_for_text "Library" 15
  tap_text "Browse"
  wait_for_text "Sources" 12
  tap_text "Sources"
  tap_text "Local source"
  wait_for_text "Torri Red" 15
  tap_text "Torri Red"
  wait_for_text "Chapter 1" 15
  tap_text "Chapter 1"
  sleep 4
  wait_for_focus
  capture "release-09-reader-reopened"
fi

adb -s emulator-5554 logcat -d -b all > "$EVIDENCE_DIR/logcat.txt"
if grep -A 80 'FATAL EXCEPTION' "$EVIDENCE_DIR/logcat.txt" | grep -q "$PACKAGE" ||
   grep -Fq 'GlobalExceptionHandler:' "$EVIDENCE_DIR/logcat.txt"; then
  echo "Release package crashed during API $API_LEVEL smoke" >&2
  exit 1
fi

echo "Torri 1.0.0 release smoke passed on API $API_LEVEL"
