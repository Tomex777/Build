#!/usr/bin/env bash
set -euo pipefail

RUNTIME_DIR="$GITHUB_WORKSPACE/torri-output/runtime"
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
    adb -s emulator-5554 shell uiautomator dump /sdcard/torri-final.xml >/dev/null 2>&1
    adb -s emulator-5554 pull /sdcard/torri-final.xml "$RUNTIME_DIR/final-window.xml" >/dev/null 2>&1
    exit "$result"
}
trap capture_diagnostics EXIT

capture() {
    local name="$1"
    adb -s emulator-5554 exec-out screencap -p > "$RUNTIME_DIR/$name.png"
    file "$RUNTIME_DIR/$name.png" | grep -q 'PNG image data'
    test "$(stat -c%s "$RUNTIME_DIR/$name.png")" -gt 12000
}

dump_ui() {
    adb -s emulator-5554 shell uiautomator dump /sdcard/torri-window.xml >/dev/null
    adb -s emulator-5554 pull /sdcard/torri-window.xml "$RUNTIME_DIR/window.xml" >/dev/null
}

find_coords() {
    local needle="$1"
    python3 - "$RUNTIME_DIR/window.xml" "$needle" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
nodes = list(root.iter("node"))

def score(node):
    values = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
    if any(value == needle for value in values):
        return 2
    if any(needle.casefold() in value.casefold() for value in values if value):
        return 1
    return 0

for wanted_score in (2, 1):
    for node in nodes:
        if score(node) != wanted_score:
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

    echo "Could not find UI text: $needle" >&2
    dump_ui || true
    python3 - "$RUNTIME_DIR/window.xml" <<'PY' >&2 || true
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
seen = []
for node in root.iter("node"):
    for key in ("text", "content-desc"):
        value = node.attrib.get(key, "").strip()
        if value and value not in seen:
            seen.append(value)
print("\n".join(seen))
PY
    return 1
}

sdk_level=""
for attempt in $(seq 1 24); do
    sdk_level="$(adb -s emulator-5554 shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
    if [[ "$sdk_level" == "36" ]] && adb -s emulator-5554 shell pm path android >/dev/null 2>&1; then
        break
    fi
    echo "Waiting for responsive API 36 package services (attempt $attempt, SDK=${sdk_level:-unknown})"
    sleep 10
done
[[ "$sdk_level" == "36" ]]
adb -s emulator-5554 shell pm path android
sleep 10

test -s "$APK"
adb -s emulator-5554 install -r "$APK"

# Generate deterministic Local Source fixtures and persist first-run state.
adb -s emulator-5554 shell am start -W -n "$PACKAGE/$BOOTSTRAP_ACTIVITY" | tee "$RUNTIME_DIR/bootstrap.txt"
adb -s emulator-5554 shell am force-stop "$PACKAGE"

# Capture the SDK 36 startup path immediately, then the settled Library screen.
adb -s emulator-5554 logcat -c || true
adb -s emulator-5554 shell am start -n "$PACKAGE/$MAIN_ACTIVITY" > "$RUNTIME_DIR/launch.txt"
capture "00-startup"
sleep 4
adb -s emulator-5554 shell pidof "$PACKAGE" | tee "$RUNTIME_DIR/pid.txt"
test -s "$RUNTIME_DIR/pid.txt"
capture "01-library"

# More/About identity surfaces.
tap_text "More"
capture "02-more"
tap_text "About"
sleep 1
capture "03-about"
adb -s emulator-5554 shell input keyevent 4
sleep 1

# Source and extension surfaces.
tap_text "Browse"
sleep 2
capture "04-sources"
tap_text "Extensions"
sleep 2
capture "05-extensions"
tap_text "Sources"
sleep 1
tap_text "Local source"
sleep 3
capture "06-local-source"

# Cover extremes. Local Source sorts these fixture titles alphabetically; keeping
# that order means the helper only needs to scroll forward.
for title in "Torri Blue" "Torri Bright" "Torri Dark" "Torri Green" "Torri Monochrome" "Torri Red"; do
    tap_text "$title"
    sleep 3
    slug="$(echo "$title" | tr '[:upper:] ' '[:lower:]-')"
    capture "details-$slug"

    if [[ "$title" == "Torri Red" ]]; then
        tap_text "Chapter 1"
        sleep 4
        capture "07-reader"
        adb -s emulator-5554 shell pidof "$PACKAGE" | tee "$RUNTIME_DIR/reader-pid.txt"
        test -s "$RUNTIME_DIR/reader-pid.txt"
        adb -s emulator-5554 shell input keyevent 4
        sleep 2
    fi

    adb -s emulator-5554 shell input keyevent 4
    sleep 2
done

adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt"
if grep -A 80 'FATAL EXCEPTION' "$RUNTIME_DIR/logcat.txt" | grep -q "$PACKAGE"; then
    echo "Torri crashed during API 36 runtime smoke" >&2
    exit 1
fi

shot_count="$(find "$RUNTIME_DIR" -maxdepth 1 -name '*.png' | wc -l)"
test "$shot_count" -ge 14
echo "Captured $shot_count Torri API 36 screenshots"
