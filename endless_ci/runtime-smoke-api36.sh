#!/usr/bin/env bash
set -euo pipefail

ENDLESS_PROJECT="${1:?Path to reconstructed Endless project is required}"
ARTIFACT_DIR="$GITHUB_WORKSPACE/artifacts/endless-m5-runtime"
mkdir -p "$ARTIFACT_DIR"

APP_ID="com.night.endless"
TEST_ID="com.night.endless.test"

capture_diagnostics() {
    local result=$?
    trap - EXIT
    set +e

    # Manual instrumentation keeps the target APK installed until this trap, so
    # Android 16 scoped storage cannot delete the renderer evidence first.
    adb -s emulator-5554 pull \
        /sdcard/Android/data/$APP_ID/files/endless-runtime \
        "$ARTIFACT_DIR/screenshots" || true

    adb -s emulator-5554 logcat -d -b all > "$ARTIFACT_DIR/logcat.txt"
    adb -s emulator-5554 shell dumpsys activity activities > "$ARTIFACT_DIR/activities.txt"
    adb -s emulator-5554 shell dumpsys meminfo "$APP_ID" > "$ARTIFACT_DIR/meminfo.txt"
    adb -s emulator-5554 shell dumpsys gfxinfo "$APP_ID" > "$ARTIFACT_DIR/gfxinfo.txt"
    adb -s emulator-5554 shell dumpsys SurfaceFlinger --list > "$ARTIFACT_DIR/surfaceflinger-layers.txt"
    adb -s emulator-5554 shell dumpsys dropbox --print data_app_anr > "$ARTIFACT_DIR/app-anr-dropbox.txt"
    adb -s emulator-5554 shell dumpsys dropbox --print system_app_anr > "$ARTIFACT_DIR/system-anr-dropbox.txt"

    adb -s emulator-5554 uninstall "$TEST_ID" >/dev/null 2>&1 || true
    adb -s emulator-5554 uninstall "$APP_ID" >/dev/null 2>&1 || true
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
# Suppress Android's one-time immersive-mode tutorial so the first rendered
# frame belongs to Endless rather than the system confirmation overlay.
adb -s emulator-5554 shell settings put secure immersive_mode_confirmations confirmed || true
sleep 5

cd "$ENDLESS_PROJECT"
timeout 10m gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --no-daemon

APP_APK="$ENDLESS_PROJECT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ENDLESS_PROJECT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test -s "$APP_APK"
test -s "$TEST_APK"

adb -s emulator-5554 install -r -t "$APP_APK"
adb -s emulator-5554 install -r -t "$TEST_APK"
adb -s emulator-5554 shell pm path "$APP_ID"
adb -s emulator-5554 shell pm path "$TEST_ID"
adb -s emulator-5554 shell cmd package resolve-activity --brief "$APP_ID" \
    | tee "$ARTIFACT_DIR/resolved-activity.txt"

RUNNER="$(
    adb -s emulator-5554 shell pm list instrumentation \
        | tr -d '\r' \
        | awk -v target="$APP_ID" '
            index($0, "(target=" target ")") {
                sub(/^instrumentation:/, "", $0)
                sub(/ \(target=.*/, "", $0)
                print
                exit
            }
        '
)"
if [[ -z "$RUNNER" ]]; then
    echo "Unable to resolve instrumentation runner for $APP_ID" >&2
    adb -s emulator-5554 shell pm list instrumentation >&2 || true
    exit 1
fi

echo "Instrumentation runner: $RUNNER"
adb -s emulator-5554 logcat -c || true

set +e
timeout 8m adb -s emulator-5554 shell am instrument -w -r \
    -e class com.night.endless.MarsSurfaceRuntimeTest,com.night.endless.MoonSurfaceRuntimeTest,com.night.endless.OrbitalNavigationRuntimeTest \
    "$RUNNER" \
    | tee "$ARTIFACT_DIR/instrumentation.txt"
instrument_status=${PIPESTATUS[0]}
set -e

if [[ "$instrument_status" -ne 0 ]]; then
    echo "Instrumentation command failed with exit $instrument_status" >&2
    exit "$instrument_status"
fi

if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "$ARTIFACT_DIR/instrumentation.txt"; then
    echo "Instrumentation reported a test failure" >&2
    exit 1
fi

if ! grep -Eq '^OK \([0-9]+ tests?\)' "$ARTIFACT_DIR/instrumentation.txt"; then
    echo "Instrumentation did not report a successful JUnit completion" >&2
    cat "$ARTIFACT_DIR/instrumentation.txt" >&2
    exit 1
fi
