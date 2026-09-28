#!/usr/bin/env bash
set -euo pipefail

ENDLESS_PROJECT="${1:?Path to reconstructed Endless project is required}"
ARTIFACT_DIR="$GITHUB_WORKSPACE/artifacts/endless-m5-runtime"
mkdir -p "$ARTIFACT_DIR"

capture_diagnostics() {
    local result=$?
    trap - EXIT
    set +e
    # The instrumentation test copies screenshots here before AGP uninstalls
    # the target APK, so rendered evidence survives connected-test cleanup.
    adb -s emulator-5554 pull \
        /sdcard/Download/endless-runtime \
        "$ARTIFACT_DIR/screenshots" || true
    adb -s emulator-5554 logcat -d -b all > "$ARTIFACT_DIR/logcat.txt"
    adb -s emulator-5554 shell dumpsys activity activities > "$ARTIFACT_DIR/activities.txt"
    adb -s emulator-5554 shell dumpsys dropbox --print data_app_anr > "$ARTIFACT_DIR/app-anr-dropbox.txt"
    adb -s emulator-5554 shell dumpsys dropbox --print system_app_anr > "$ARTIFACT_DIR/system-anr-dropbox.txt"
    report_dir="$ENDLESS_PROJECT/app/build/reports/androidTests/connected/debug"
    results_dir="$ENDLESS_PROJECT/app/build/outputs/androidTest-results/connected/debug"
    if [[ -d "$report_dir" ]]; then cp -R "$report_dir" "$ARTIFACT_DIR/android-test-report"; fi
    if [[ -d "$results_dir" ]]; then cp -R "$results_dir" "$ARTIFACT_DIR/android-test-results"; fi
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
adb -s emulator-5554 shell settings put global animator_duration_scale 0
adb -s emulator-5554 shell settings put global transition_animation_scale 0
adb -s emulator-5554 shell settings put global window_animation_scale 0
sleep 10

cd "$ENDLESS_PROJECT"
if timeout 20m gradle :app:connectedDebugAndroidTest --stacktrace --no-daemon; then
    exit 0
else
    exit $?
fi
