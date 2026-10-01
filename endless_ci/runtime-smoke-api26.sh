#!/usr/bin/env bash
set -euo pipefail

ENDLESS_PROJECT="${1:?Path to reconstructed Endless project is required}"
ARTIFACT_DIR="$GITHUB_WORKSPACE/artifacts/endless-api26-runtime"
mkdir -p "$ARTIFACT_DIR"

APP_ID="com.night.endless"
TEST_ID="com.night.endless.test"

capture_diagnostics() {
    local result=$?
    trap - EXIT
    set +e

    adb -s emulator-5554 pull         /sdcard/Android/data/$APP_ID/files/endless-runtime         "$ARTIFACT_DIR/screenshots" || true
    adb -s emulator-5554 logcat -d -b all > "$ARTIFACT_DIR/logcat.txt" 2>&1 || true
    adb -s emulator-5554 shell dumpsys activity activities > "$ARTIFACT_DIR/activities.txt" 2>&1 || true
    adb -s emulator-5554 shell dumpsys meminfo "$APP_ID" > "$ARTIFACT_DIR/meminfo.txt" 2>&1 || true
    adb -s emulator-5554 shell dumpsys gfxinfo "$APP_ID" > "$ARTIFACT_DIR/gfxinfo.txt" 2>&1 || true
    adb -s emulator-5554 shell dumpsys SurfaceFlinger --list > "$ARTIFACT_DIR/surfaceflinger-layers.txt" 2>&1 || true
    adb -s emulator-5554 uninstall "$TEST_ID" >/dev/null 2>&1 || true
    adb -s emulator-5554 uninstall "$APP_ID" >/dev/null 2>&1 || true
    exit "$result"
}
trap capture_diagnostics EXIT

sdk_level="$(adb -s emulator-5554 shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$sdk_level" == "26" ]]

APP_APK="${ENDLESS_RUNTIME_APK:-$ENDLESS_PROJECT/app/build/outputs/apk/debug/app-debug.apk}"
printf 'runtimeApk=%s\n' "$APP_APK" > "$ARTIFACT_DIR/runtime-variant.txt"
sha256sum "$APP_APK" >> "$ARTIFACT_DIR/runtime-variant.txt"
TEST_APK="$ENDLESS_PROJECT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test -s "$APP_APK"
test -s "$TEST_APK"

adb -s emulator-5554 install -r -t "$APP_APK"
adb -s emulator-5554 install -r -t "$TEST_APK"
adb -s emulator-5554 shell pm path "$APP_ID"
adb -s emulator-5554 shell pm path "$TEST_ID"

RUNNER="$(
    adb -s emulator-5554 shell pm list instrumentation         | tr -d '\r'         | awk -v target="$APP_ID" '
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
timeout 5m adb -s emulator-5554 shell am instrument -w -r     -e class com.night.endless.CompatibilityRuntimeTest     "$RUNNER"     | tee "$ARTIFACT_DIR/instrumentation.txt"
instrument_status=${PIPESTATUS[0]}
set -e

if [[ "$instrument_status" -ne 0 ]]; then
    echo "API 26 instrumentation command failed with exit $instrument_status" >&2
    exit "$instrument_status"
fi

if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "$ARTIFACT_DIR/instrumentation.txt"; then
    echo "API 26 instrumentation reported a test failure" >&2
    exit 1
fi

if ! grep -Eq '^OK \([0-9]+ tests?\)' "$ARTIFACT_DIR/instrumentation.txt"; then
    echo "API 26 instrumentation did not report successful JUnit completion" >&2
    cat "$ARTIFACT_DIR/instrumentation.txt" >&2
    exit 1
fi
