#!/usr/bin/env bash
set -euo pipefail

output=${1:?Provide the PNG output path}
runtime_dir=$(dirname "$output")
base=$(basename "$output" .png)
preflight="$runtime_dir/$base-window-preflight.txt"
postflight="$runtime_dir/$base-window-postflight.txt"

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
# A system ANR that appears during capture also invalidates the screenshot. Try to
# dismiss it only for diagnostics; the PNG remains rejected and removed.
reject_anr "$postflight" postflight
test -s "$output"
test "$(wc -c < "$output")" -gt 4096
file "$output" | grep -q 'PNG image data'
