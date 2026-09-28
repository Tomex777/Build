#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: start-emulator-api36.sh <runtime-smoke-script> [args...]" >&2
    exit 2
fi
SMOKE_SCRIPT="$1"
shift
ANDROID_HOME="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
EVIDENCE_DIR="${API36_EVIDENCE_DIR:?API36_EVIDENCE_DIR must point to the runtime artifact directory}"
mkdir -p "$EVIDENCE_DIR"
EMULATOR_LOG="$EVIDENCE_DIR/emulator-console.log"
EMULATOR_PID=""

cleanup() {
    local result=$?
    trap - EXIT
    set +e
    if [[ "$result" -ne 0 ]]; then
        adb -s emulator-5554 logcat -d -b all > "$EVIDENCE_DIR/boot-or-runtime-logcat.txt"
        adb -s emulator-5554 shell getprop > "$EVIDENCE_DIR/emulator-properties.txt"
        adb -s emulator-5554 shell service list > "$EVIDENCE_DIR/emulator-services.txt"
    fi
    adb -s emulator-5554 emu kill >/dev/null 2>&1
    if [[ -n "$EMULATOR_PID" ]]; then wait "$EMULATOR_PID" >/dev/null 2>&1; fi
    exit "$result"
}
trap cleanup EXIT

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then SDKMANAGER="$ANDROID_HOME/cmdline-tools/16.0/bin/sdkmanager"; fi
AVDMANAGER="$(dirname "$SDKMANAGER")/avdmanager"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "platforms;android-36" "emulator" "system-images;android-36;google_apis;x86_64"

printf 'no\n' | "$AVDMANAGER" create avd --force -n api36-smoke -k "system-images;android-36;google_apis;x86_64" -d pixel_6
"$ANDROID_HOME/emulator/emulator" -port 5554 -avd api36-smoke \
    -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim -memory 3072 -accel off \
    >"$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!
adb start-server
adb -s emulator-5554 wait-for-device

ready=false
for attempt in $(seq 1 120); do
    sdk="$(adb -s emulator-5554 shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
    boot="$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    package="$(adb -s emulator-5554 shell pm path android 2>/dev/null || true)"
    input="$(adb -s emulator-5554 shell service check input 2>/dev/null || true)"
    window="$(adb -s emulator-5554 shell service check window 2>/dev/null || true)"
    if [[ "$sdk" == 36 && "$boot" == 1 && "$package" == package:* && "$input" == *found* && "$window" == *found* ]]; then
        ready=true
        break
    fi
    echo "Waiting for API 36 system services (attempt $attempt, SDK=${sdk:-unknown}, boot=${boot:-unknown}, package=${package:-unavailable}, input=${input:-unavailable}, window=${window:-unavailable})"
    sleep 10
done
if [[ "$ready" != true ]]; then
    echo "API 36 emulator did not expose PackageManager, input, and window services" >&2
    exit 1
fi

adb -s emulator-5554 shell settings put global animator_duration_scale 0
adb -s emulator-5554 shell settings put global transition_animation_scale 0
adb -s emulator-5554 shell settings put global window_animation_scale 0
adb -s emulator-5554 shell input keyevent 82
bash "$SMOKE_SCRIPT" "$@"
