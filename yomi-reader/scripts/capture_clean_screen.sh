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
    if grep -Fq 'Application Not Responding:' "$dump"; then
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
    adb shell dumpsys window windows > "$1"
}

clear_android_anr() {
    local dump=$1
    local attempt=0
    while grep -Fq 'Application Not Responding:' "$dump" && [ "$attempt" -lt 15 ]; do
        if grep -Fq 'Application Not Responding: app.yomi.reader.dev' "$dump"; then
            reject_anr "$dump" preflight
        fi
        sleep 5
        adb shell input tap 300 1350 || true
        sleep 2
        refresh_windows "$dump"
        attempt=$((attempt + 1))
    done
}

refresh_windows "$preflight"
assert_yomi_foreground preflight "$activity_preflight"
# API 36 software emulators can surface background Android ANRs during startup. Clear
# the system dialog before capture; any frame taken while an ANR remains is discarded.
clear_android_anr "$preflight"
reject_anr "$preflight" preflight

capture_attempt=0
while :; do
    assert_yomi_foreground pre-capture "$activity_preflight"
    sleep 1
    adb exec-out screencap -p > "$output"
    refresh_windows "$postflight"
    assert_yomi_foreground post-capture "$activity_postflight"
    if ! grep -Fq 'Application Not Responding:' "$postflight"; then
        break
    fi

    rm -f "$output"
    if grep -Fq 'Application Not Responding: app.yomi.reader.dev' "$postflight"; then
        reject_anr "$postflight" postflight
    fi
    clear_android_anr "$postflight"
    reject_anr "$postflight" postflight
    capture_attempt=$((capture_attempt + 1))
    [ "$capture_attempt" -lt 3 ] || { echo "Refusing $output: Android repeatedly showed ANR dialogs during capture" >&2; exit 1; }
done

test -s "$output"
test "$(wc -c < "$output")" -gt 4096
file "$output" | grep -q 'PNG image data'
