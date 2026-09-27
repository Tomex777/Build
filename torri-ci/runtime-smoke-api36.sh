#!/usr/bin/env bash
set -euo pipefail

RUNTIME_DIR="$GITHUB_WORKSPACE/torri-output/runtime"
APK="$GITHUB_WORKSPACE/torri-output/Torri-universal.apk"
mkdir -p "$RUNTIME_DIR"

capture_diagnostics() {
    local result=$?
    trap - EXIT
    set +e
    adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt"
    adb -s emulator-5554 shell dumpsys activity activities > "$RUNTIME_DIR/activities.txt"
    exit "$result"
}
trap capture_diagnostics EXIT

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
sleep 20

test -s "$APK"
adb -s emulator-5554 install -r "$APK"
adb -s emulator-5554 shell monkey -p app.torri 1

foreground=""
for attempt in $(seq 1 30); do
    foreground="$(adb -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' || true)"
    if [[ "$foreground" == *"app.torri"* ]] && adb -s emulator-5554 shell pidof app.torri >/dev/null 2>&1; then
        break
    fi
    sleep 2
done
echo "$foreground"
[[ "$foreground" == *"app.torri"* ]]
adb -s emulator-5554 exec-out screencap -p > "$RUNTIME_DIR/torri-home.png"
file "$RUNTIME_DIR/torri-home.png" | grep -q 'PNG image data'
test "$(stat -c%s "$RUNTIME_DIR/torri-home.png")" -gt 20000
