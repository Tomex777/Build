#!/usr/bin/env bash
set -euo pipefail

RUNTIME_DIR="${API26_EVIDENCE_DIR:-$GITHUB_WORKSPACE/torri-output/runtime-api26}"
APK="$GITHUB_WORKSPACE/torri-output/Torri-x86_64-debug.apk"
PACKAGE="app.torri.dev"
MAIN_ACTIVITY="eu.kanade.tachiyomi.ui.main.MainActivity"
BOOTSTRAP_ACTIVITY="eu.kanade.tachiyomi.ui.ci.TorriCiBootstrapActivity"
mkdir -p "$RUNTIME_DIR"

capture_diagnostics() {
    local result=$?
    trap - EXIT
    set +e
    adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt"
    adb -s emulator-5554 shell dumpsys activity activities > "$RUNTIME_DIR/activities.txt"
    adb -s emulator-5554 shell uiautomator dump /sdcard/torri-api26-final.xml >/dev/null 2>&1
    adb -s emulator-5554 pull /sdcard/torri-api26-final.xml "$RUNTIME_DIR/final-window.xml" >/dev/null 2>&1
    exit "$result"
}
trap capture_diagnostics EXIT

assert_no_torri_crash() {
    local focus=""
    focus="$(adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
    if [[ "$focus" == *"app.torri.dev/eu.kanade.tachiyomi.crash.CrashActivity"* ]]; then
        echo "Torri entered CrashActivity during API 26 runtime smoke: $focus" >&2
        adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt" || true
        grep -A 40 -B 4 'GlobalExceptionHandler' "$RUNTIME_DIR/logcat.txt" | tail -n 120 >&2 || true
        return 1
    fi
}

capture() {
    local name="$1"
    assert_no_torri_crash
    adb -s emulator-5554 exec-out screencap -p > "$RUNTIME_DIR/$name.png"
    file "$RUNTIME_DIR/$name.png" | grep -q 'PNG image data'
    test "$(stat -c%s "$RUNTIME_DIR/$name.png")" -gt 10000
}

wait_for_torri_focus() {
    local focus=""
    for attempt in $(seq 1 30); do
        focus="$(adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
        if [[ "$focus" == *"app.torri.dev/eu.kanade.tachiyomi.crash.CrashActivity"* ]]; then
            echo "Torri entered CrashActivity while waiting for foreground on API 26: $focus" >&2
            echo "$focus" > "$RUNTIME_DIR/current-focus.txt"
            return 1
        fi
        if [[ "$focus" == *"$PACKAGE"* ]]; then
            echo "$focus" > "$RUNTIME_DIR/current-focus.txt"
            return 0
        fi
        if [[ "$focus" == *"Application Not Responding"* || "$focus" == *"has stopped"* ]]; then
            echo "Unexpected system error dialog while waiting for Torri: $focus" >&2
            echo "$focus" > "$RUNTIME_DIR/current-focus.txt"
            return 1
        fi
        echo "Waiting for Torri foreground window (attempt $attempt/30, focus=${focus:-unknown})"
        sleep 1
    done
    echo "Torri never became the focused window on API 26" >&2
    return 1
}

dump_ui() {
    adb -s emulator-5554 shell uiautomator dump /sdcard/torri-api26-window.xml >/dev/null
    adb -s emulator-5554 pull /sdcard/torri-api26-window.xml "$RUNTIME_DIR/window.xml" >/dev/null
}

find_coords() {
    local needle="$1"
    python3 - "$RUNTIME_DIR/window.xml" "$needle" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()

for wanted_score in (2, 1):
    for node in root.iter("node"):
        values = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        score = 2 if any(value == needle for value in values) else 1 if any(needle.casefold() in value.casefold() for value in values if value) else 0
        if score != wanted_score:
            continue
        points = re.findall(r"\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if len(points) != 2:
            continue
        (x1, y1), (x2, y2) = ((int(x), int(y)) for x, y in points)
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_text() {
    local needle="$1"
    local coords=""
    for attempt in $(seq 1 6); do
        dump_ui
        coords="$(find_coords "$needle" 2>/dev/null || true)"
        if [[ -n "$coords" ]]; then
            read -r x y <<<"$coords"
            adb -s emulator-5554 shell input tap "$x" "$y"
            sleep 1
            return 0
        fi
        adb -s emulator-5554 shell input swipe 540 1500 540 620 320
        sleep 1
    done
    echo "Could not find API 26 UI text: $needle" >&2
    return 1
}

tap_text_if_present() {
    local needle="$1"
    local coords=""
    dump_ui
    coords="$(find_coords "$needle" 2>/dev/null || true)"
    if [[ -z "$coords" ]]; then
        return 1
    fi
    read -r x y <<<"$coords"
    adb -s emulator-5554 shell input tap "$x" "$y"
    sleep 1
}

sdk_level=""
for attempt in $(seq 1 24); do
    sdk_level="$(adb -s emulator-5554 shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
    if [[ "$sdk_level" == "26" ]] && adb -s emulator-5554 shell pm path android >/dev/null 2>&1; then
        break
    fi
    echo "Waiting for responsive API 26 package services (attempt $attempt, SDK=${sdk_level:-unknown})"
    sleep 5
done
[[ "$sdk_level" == "26" ]]
adb -s emulator-5554 shell pm path android

test -s "$APK"
adb -s emulator-5554 install -r "$APK"

adb -s emulator-5554 shell am start -W -n "$PACKAGE/$BOOTSTRAP_ACTIVITY" | tee "$RUNTIME_DIR/bootstrap.txt"
adb -s emulator-5554 shell am force-stop "$PACKAGE"

adb -s emulator-5554 logcat -c || true
adb -s emulator-5554 shell am start -W -n "$PACKAGE/$MAIN_ACTIVITY" | tee "$RUNTIME_DIR/launch.txt"
wait_for_torri_focus
sleep 3
capture "00-library"

tap_text "Browse"
sleep 2
capture "01-browse"

tap_text "Local source"
sleep 2
capture "02-local-source"

tap_text "Torri Red"
sleep 3
capture "03-details"

tap_text "Chapter 1"
sleep 4
tap_text_if_present "Got it" || true
sleep 1
wait_for_torri_focus
capture "04-reader"

adb -s emulator-5554 shell pidof "$PACKAGE" | tee "$RUNTIME_DIR/reader-pid.txt"
test -s "$RUNTIME_DIR/reader-pid.txt"

adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt"
if grep -A 80 'FATAL EXCEPTION' "$RUNTIME_DIR/logcat.txt" | grep -q "$PACKAGE" ||
   grep -Fq 'GlobalExceptionHandler:' "$RUNTIME_DIR/logcat.txt"; then
    echo "Torri crashed during API 26 runtime smoke" >&2
    exit 1
fi
if grep -F 'TorriCiStorage' "$RUNTIME_DIR/logcat.txt" | grep -Fq 'FileNotFoundException'; then
    echo "Torri Local Source fixture failed to load on API 26" >&2
    exit 1
fi

shot_count="$(find "$RUNTIME_DIR" -maxdepth 1 -name '*.png' | wc -l)"
test "$shot_count" -ge 5
echo "Captured $shot_count Torri API 26 screenshots"
