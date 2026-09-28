#!/usr/bin/env bash
set -euo pipefail

RUNTIME_DIR="$GITHUB_WORKSPACE/torri-output/runtime"
APK="$GITHUB_WORKSPACE/torri-output/Torri-x86_64-debug.apk"
PACKAGE="app.torri.dev"
BOOTSTRAP_SENTINEL="/sdcard/Android/data/$PACKAGE/files/TorriCiStorage/.bootstrap-complete"
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

assert_no_torri_crash() {
    local focus=""
    focus="$(adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
    if [[ "$focus" == *"app.torri.dev/eu.kanade.tachiyomi.crash.CrashActivity"* ]]; then
        echo "Torri entered CrashActivity during API 36 runtime smoke: $focus" >&2
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
    test "$(stat -c%s "$RUNTIME_DIR/$name.png")" -gt 12000
}

wait_for_torri_focus() {
    local focus=""
    for attempt in $(seq 1 30); do
        focus="$(adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
        if [[ "$focus" == *"app.torri.dev/eu.kanade.tachiyomi.crash.CrashActivity"* ]]; then
            echo "Torri entered CrashActivity while waiting for foreground: $focus" >&2
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
    echo "Torri never became the focused window" >&2
    adb -s emulator-5554 shell dumpsys window > "$RUNTIME_DIR/window-manager-timeout.txt" 2>/dev/null || true
    return 1
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

find_exact_coords() {
    local needle="$1"
    python3 - "$RUNTIME_DIR/window.xml" "$needle" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()

for node in root.iter("node"):
    values = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
    if not any(value == needle for value in values):
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

return_to_main_navigation() {
    local coords=""
    for attempt in $(seq 1 5); do
        dump_ui
        coords="$(find_exact_coords "More" 2>/dev/null || true)"
        if [[ -n "$coords" ]]; then
            return 0
        fi
        assert_no_torri_crash
        adb -s emulator-5554 shell input keyevent 4
        sleep 1
    done

    dump_ui
    coords="$(find_exact_coords "More" 2>/dev/null || true)"
    if [[ -n "$coords" ]]; then
        return 0
    fi

    echo "Could not return to Torri main navigation after nested source/search flow" >&2
    return 1
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

wait_for_text() {
    local needle="$1"
    local attempts="${2:-12}"
    for attempt in $(seq 1 "$attempts"); do
        dump_ui
        if [[ -n "$(find_coords "$needle" 2>/dev/null || true)" ]]; then
            return 0
        fi
        assert_no_torri_crash
        sleep 1
    done
    echo "Timed out waiting for UI text: $needle" >&2
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
    return 0
}

run_bootstrap() {
    adb -s emulator-5554 shell rm -f "$BOOTSTRAP_SENTINEL" >/dev/null 2>&1 || true
    adb -s emulator-5554 shell am start -n "$PACKAGE/$BOOTSTRAP_ACTIVITY" | tee "$RUNTIME_DIR/bootstrap.txt"

    local bootstrap_ready=false
    for attempt in $(seq 1 90); do
        if adb -s emulator-5554 shell "test -s '$BOOTSTRAP_SENTINEL'" >/dev/null 2>&1; then
            bootstrap_ready=true
            break
        fi
        assert_no_torri_crash
        sleep 1
    done

    if [[ "$bootstrap_ready" != true ]]; then
        echo "Torri CI bootstrap did not complete within 90 seconds" >&2
        adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/bootstrap-timeout-logcat.txt" || true
        adb -s emulator-5554 shell dumpsys activity activities > "$RUNTIME_DIR/bootstrap-timeout-activities.txt" || true
        return 1
    fi

    adb -s emulator-5554 shell am force-stop "$PACKAGE"
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
run_bootstrap

# Capture the SDK 36 startup path immediately, then the settled Library screen.
adb -s emulator-5554 logcat -c || true
adb -s emulator-5554 shell am start -n "$PACKAGE/$MAIN_ACTIVITY" > "$RUNTIME_DIR/launch.txt"
wait_for_torri_focus
capture "00-startup"
sleep 4
wait_for_torri_focus
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
for title in "Torri Blue" "Torri Bright" "Torri Dark" "Torri Green" "Torri Missing" "Torri Monochrome" "Torri Red"; do
    tap_text "$title"
    if [[ "$title" != "Torri Missing" ]]; then
        wait_for_text "Chapter 1" 15
    else
        # Missing deliberately has no cover and no chapter so it cannot synthesize a cover.
        wait_for_text "0 chapters" 15
    fi
    sleep 1
    slug="$(echo "$title" | tr '[:upper:] ' '[:lower:]-')"
    capture "details-$slug"

    if [[ "$title" == "Torri Red" ]]; then
        tap_text "Chapter 1"
        sleep 4

        # Android shows an immersive-mode education bubble on a fresh emulator.
        # Dismiss it so the evidence proves Torri's real reader, not System UI.
        tap_text_if_present "Got it" || true
        sleep 1
        wait_for_torri_focus
        capture "07-reader"

        adb -s emulator-5554 shell pidof "$PACKAGE" | tee "$RUNTIME_DIR/reader-pid.txt"
        test -s "$RUNTIME_DIR/reader-pid.txt"

        # Reveal the real reader chrome once and preserve it as separate evidence.
        adb -s emulator-5554 shell input tap 540 960
        wait_for_text "Reading mode" 10
        capture "08-reader-controls"
        dump_ui
        cp "$RUNTIME_DIR/window.xml" "$RUNTIME_DIR/reader-controls.xml"

        # Exercise the mature reader configuration surfaces without changing the
        # selected mode. These dialogs are part of the reader handoff contract.
        tap_text "Reading mode"
        wait_for_text "Paged (right to left)" 10
        capture "08a-reading-mode"
        adb -s emulator-5554 shell input keyevent 4
        wait_for_text "Settings" 10

        tap_text "Settings"
        wait_for_text "General" 10

        # Reader settings opens on the General pane. Color filtering lives on
        # the separate Custom filter pane on current Mihon, and API 36 does not
        # expose off-pane semantics in the UI hierarchy. Navigate there
        # explicitly instead of treating an off-pane label as visible.
        tap_text "Custom filter"
        wait_for_text "Color filter" 10
        capture "08b-reader-settings"

        # Change a real reader preference, then prove the settings sheet can be
        # closed without destroying the active chapter/reader state.
        tap_text "Color filter"
        wait_for_text "Color filter" 10
        capture "08c-reader-filter-changed"
        adb -s emulator-5554 shell input keyevent 4
        wait_for_text "Reading mode" 10
        capture "08d-reader-after-settings"

        # Back out robustly even when the first Back only closes reader chrome.
        returned_to_details=false
        for attempt in $(seq 1 3); do
            adb -s emulator-5554 shell input keyevent 4
            sleep 1
            dump_ui
            if [[ -n "$(find_coords "Add to library" 2>/dev/null || true)" ]]; then
                returned_to_details=true
                break
            fi
        done
        [[ "$returned_to_details" == true ]]
    fi

    adb -s emulator-5554 shell input keyevent 4
    wait_for_text "Local source" 12
    sleep 1
done

# Exercise Local Source search and prove the result can be opened.
tap_text "Search"
adb -s emulator-5554 shell input text "Torri%sRed"
adb -s emulator-5554 shell input keyevent 66
sleep 2
capture "09-search"
tap_text "Torri Red"
sleep 2
capture "10-search-details"

# Back from details must restore the nested Local Source/search context first.
# Then unwind that nested stack until the real main-navigation "More" target
# is present. This deliberately uses exact matching so "More options" can
# never masquerade as the main destination.
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Filter" 12
return_to_main_navigation

# Finish on Torri's Settings surface instead of ending inside the source.
tap_text "More"
wait_for_text "Settings" 12
tap_text "Settings"
sleep 1
capture "11-settings"

adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt"
if grep -A 80 'FATAL EXCEPTION' "$RUNTIME_DIR/logcat.txt" | grep -q "$PACKAGE" ||
   grep -Fq 'GlobalExceptionHandler:' "$RUNTIME_DIR/logcat.txt"; then
    echo "Torri crashed during API 36 runtime smoke" >&2
    exit 1
fi

# A cover-adaptive details run is not valid if Local Source cover URIs failed.
if grep -F 'TorriCiStorage' "$RUNTIME_DIR/logcat.txt" | grep -Fq 'FileNotFoundException'; then
    echo "Torri Local Source cover/page fixture failed to load" >&2
    grep -F 'TorriCiStorage' "$RUNTIME_DIR/logcat.txt" | grep -F 'FileNotFoundException' >&2 || true
    exit 1
fi

shot_count="$(find "$RUNTIME_DIR" -maxdepth 1 -name '*.png' | wc -l)"
test "$shot_count" -ge 23
echo "Captured $shot_count Torri API 36 screenshots"
