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
        echo "Refusing $output: Android showed an ANR dialog during $phase" >&2
        exit 1
    fi
}

adb shell dumpsys window windows > "$preflight"
reject_anr "$preflight" preflight
adb exec-out screencap -p > "$output"
adb shell dumpsys window windows > "$postflight"
reject_anr "$postflight" postflight
test -s "$output"
test "$(wc -c < "$output")" -gt 4096
file "$output" | grep -q 'PNG image data'
