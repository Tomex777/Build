#!/usr/bin/env bash
set -euo pipefail

output=${1:?Provide the PNG output path}
runtime_dir=$(dirname "$output")
base=$(basename "$output" .png)
preflight="$runtime_dir/$base-window-preflight.txt"
postflight="$runtime_dir/$base-window-postflight.txt"
activity_preflight="$runtime_dir/$base-activity-preflight.txt"
activity_postflight="$runtime_dir/$base-activity-postflight.txt"

assert_yomi_foreground() {
    local phase=$1
    local dump=$2
    adb shell dumpsys activity activities > "$dump"
    if ! grep -Eq "mResumedActivity:.*app\\.yomi\\.reader\\.dev/app\\.yomi\\.reader\\.(MainActivity|ReaderActivity)|topResumedActivity=.*app\\.yomi\\.reader\\.dev/app\\.yomi\\.reader\\.(MainActivity|ReaderActivity)" "$dump"; then
        echo "Refusing $output: Yomi is not the resumed app during $phase" >&2
        grep -E "mResumedActivity|topResumedActivity" "$dump" | tail -n 20 >&2 || true
        exit 1
    fi
}

reject_anr() {
    local dump=$1
    local phase=$2
    if grep -Eq 'Application Not Responding: (system|com\.android\.systemui|app\.yomi\.reader\.dev)' "$dump"; then
        rm -f "$output"
        adb logcat -d -v threadtime > "$runtime_dir/$base-$phase-anr-logcat.txt" || true
        echo "ANR window evidence during $phase:" >&2
        grep -E 'Application Not Responding:|mCurrentFocus|topResumedActivity' "$dump" | tail -n 40 >&2 || true
        grep -E 'ANR in |Input dispatching timed out|YomiStartup|YomiReader|FATAL EXCEPTION|not responding' "$runtime_dir/$base-$phase-anr-logcat.txt" | tail -n 80 >&2 || true
        echo "Refusing $output: Android showed an ANR dialog during $phase" >&2
        exit 1
    fi
}

refresh_windows() {
    adb shell dumpsys window windows > "$preflight"
}

refresh_windows
assert_yomi_foreground preflight "$activity_preflight"
# The API 36 software emulator can show a system ANR dialog while Android finishes
# starting background services. Give the system a chance to recover, then choose
# Wait on the dialog. Never capture while any ANR window remains visible.
attempt=0
while grep -Eq 'Application Not Responding: (system|com\.android\.systemui)' "$preflight" && [ "$attempt" -lt 15 ]; do
    sleep 5
    adb shell input tap 300 1350 || true
    sleep 2
    refresh_windows
    attempt=$((attempt + 1))
done
reject_anr "$preflight" preflight
sleep 1
adb exec-out screencap -p > "$output"
adb shell dumpsys window windows > "$postflight"
assert_yomi_foreground postflight "$activity_postflight"
# A system ANR that appears during capture also invalidates the screenshot. Try to
# dismiss it only for diagnostics; the PNG remains rejected and removed.
reject_anr "$postflight" postflight
test -s "$output"
test "$(wc -c < "$output")" -gt 4096
file "$output" | grep -q 'PNG image data'
