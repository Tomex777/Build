#!/usr/bin/env bash
set -euo pipefail

ENDLESS_PROJECT="${1:?Path to reconstructed Endless project is required}"
ARTIFACT_DIR="$GITHUB_WORKSPACE/artifacts/endless-m5-runtime"
mkdir -p "$ARTIFACT_DIR"

capture_diagnostics() {
    local result=$?
    trap - EXIT
    set +e
    adb -s emulator-5554 pull \
        /sdcard/Android/data/com.night.endless/files/endless-runtime \
        "$ARTIFACT_DIR/screenshots"
    adb -s emulator-5554 logcat -d -b crash > "$ARTIFACT_DIR/crash-logcat.txt"
    adb -s emulator-5554 logcat -d -s AndroidRuntime:E GLSurfaceView:E > "$ARTIFACT_DIR/runtime-logcat.txt"
    adb -s emulator-5554 shell dumpsys activity activities > "$ARTIFACT_DIR/activities.txt"
    exit "$result"
}
trap capture_diagnostics EXIT

# Android can report boot completion before package services are responsive.
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

cd "$ENDLESS_PROJECT"
if gradle :app:connectedDebugAndroidTest --stacktrace; then
    exit 0
else
    exit $?
fi
