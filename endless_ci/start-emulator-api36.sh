#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: start-emulator-api36.sh <runtime-smoke-script> [args...]" >&2
    exit 2
fi

SMOKE_SCRIPT="$1"
shift
ANDROID_HOME="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# Keep AVD creation and emulator lookup on one explicit path. The previous
# harness let avdmanager and emulator disagree about the AVD home, then blocked
# forever in adb wait-for-device after the emulator exited with "Unknown AVD".
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
mkdir -p "$ANDROID_AVD_HOME"

EVIDENCE_DIR="${API36_EVIDENCE_DIR:?API36_EVIDENCE_DIR must point to the runtime artifact directory}"
mkdir -p "$EVIDENCE_DIR"
EMULATOR_LOG="$EVIDENCE_DIR/emulator-console.log"
AVD_LOG="$EVIDENCE_DIR/avd-layout.txt"
EMULATOR_PID=""

cleanup() {
    local result=$?
    trap - EXIT
    set +e

    {
        echo "result=$result"
        echo "ANDROID_AVD_HOME=$ANDROID_AVD_HOME"
        echo "emulator_pid=${EMULATOR_PID:-unset}"
        if [[ -n "$EMULATOR_PID" ]]; then
            if kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
                echo "emulator_process=alive"
            else
                echo "emulator_process=exited"
            fi
        fi
        echo "--- emulator -list-avds ---"
        "$ANDROID_HOME/emulator/emulator" -list-avds 2>&1 || true
        echo "--- avd files ---"
        find "$ANDROID_AVD_HOME" -maxdepth 2 -type f -o -type d 2>/dev/null | sort || true
    } > "$AVD_LOG"

    if [[ "$result" -ne 0 ]]; then
        timeout 15 adb -s emulator-5554 logcat -d -b all > "$EVIDENCE_DIR/boot-or-runtime-logcat.txt" 2>&1 || true
        timeout 10 adb -s emulator-5554 shell getprop > "$EVIDENCE_DIR/emulator-properties.txt" 2>&1 || true
        timeout 10 adb -s emulator-5554 shell service list > "$EVIDENCE_DIR/emulator-services.txt" 2>&1 || true
        cp "$EMULATOR_LOG" "$EVIDENCE_DIR/emulator-console-final.log" 2>/dev/null || true
    fi

    timeout 10 adb -s emulator-5554 emu kill >/dev/null 2>&1 || true
    if [[ -n "$EMULATOR_PID" ]]; then
        kill "$EMULATOR_PID" >/dev/null 2>&1 || true
        wait "$EMULATOR_PID" >/dev/null 2>&1 || true
    fi
    exit "$result"
}
trap cleanup EXIT

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
    SDKMANAGER="$ANDROID_HOME/cmdline-tools/16.0/bin/sdkmanager"
fi
AVDMANAGER="$(dirname "$SDKMANAGER")/avdmanager"
test -x "$SDKMANAGER"
test -x "$AVDMANAGER"

yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "platforms;android-36" "emulator" "system-images;android-36;google_apis;x86_64"

rm -rf "$ANDROID_AVD_HOME/api36-smoke.avd" "$ANDROID_AVD_HOME/api36-smoke.ini"
printf 'no\n' | "$AVDMANAGER" create avd --force     -n api36-smoke     -k "system-images;android-36;google_apis;x86_64"     -d pixel_6     -p "$ANDROID_AVD_HOME/api36-smoke.avd"

{
    echo "ANDROID_AVD_HOME=$ANDROID_AVD_HOME"
    echo "--- emulator -list-avds ---"
    "$ANDROID_HOME/emulator/emulator" -list-avds
    echo "--- avd files ---"
    find "$ANDROID_AVD_HOME" -maxdepth 2 -type f -o -type d | sort
} | tee "$AVD_LOG"

if ! "$ANDROID_HOME/emulator/emulator" -list-avds | grep -Fxq "api36-smoke"; then
    echo "api36-smoke was not registered in ANDROID_AVD_HOME=$ANDROID_AVD_HOME" >&2
    exit 1
fi

ACCEL_ARGS=(-accel off)
if [[ -e /dev/kvm ]]; then
    sudo chmod 666 /dev/kvm >/dev/null 2>&1 || true
    if [[ -r /dev/kvm && -w /dev/kvm ]]; then
        ACCEL_ARGS=(-accel on)
        echo "Using KVM acceleration for API 36 emulator"
    else
        echo "/dev/kvm exists but is not usable; falling back to software acceleration"
    fi
else
    echo "/dev/kvm is unavailable; falling back to software acceleration"
fi

"$ANDROID_HOME/emulator/emulator" -port 5554 -avd api36-smoke     -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim     -memory 3072 "${ACCEL_ARGS[@]}"     >"$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!

adb start-server

# Never use an unbounded adb wait here. If the emulator dies before registering
# with adb, report the emulator log immediately instead of burning the full job.
device_seen=false
for attempt in $(seq 1 120); do
    if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
        echo "API 36 emulator exited before adb discovered it" >&2
        cat "$EMULATOR_LOG" >&2 || true
        exit 1
    fi

    state="$(timeout 5 adb -s emulator-5554 get-state 2>/dev/null || true)"
    if [[ "$state" == "device" ]]; then
        device_seen=true
        break
    fi

    if (( attempt % 6 == 0 )); then
        echo "Waiting for emulator-5554 to become an adb device (attempt $attempt, state=${state:-missing})"
        tail -n 30 "$EMULATOR_LOG" || true
    fi
    sleep 5
done

if [[ "$device_seen" != true ]]; then
    echo "API 36 emulator never became an adb device" >&2
    cat "$EMULATOR_LOG" >&2 || true
    exit 1
fi

ready=false
for attempt in $(seq 1 120); do
    if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
        echo "API 36 emulator exited during boot" >&2
        cat "$EMULATOR_LOG" >&2 || true
        exit 1
    fi

    sdk="$(timeout 5 adb -s emulator-5554 shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
    boot="$(timeout 5 adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    package="$(timeout 5 adb -s emulator-5554 shell pm path android 2>/dev/null || true)"
    input="$(timeout 5 adb -s emulator-5554 shell service check input 2>/dev/null || true)"
    window="$(timeout 5 adb -s emulator-5554 shell service check window 2>/dev/null || true)"

    if [[ "$sdk" == 36 && "$boot" == 1 && "$package" == package:* && "$input" == *found* && "$window" == *found* ]]; then
        ready=true
        break
    fi

    if (( attempt % 6 == 0 )); then
        echo "Waiting for API 36 services (attempt $attempt, SDK=${sdk:-unknown}, boot=${boot:-unknown}, package=${package:-unavailable}, input=${input:-unavailable}, window=${window:-unavailable})"
    fi
    sleep 5
done

if [[ "$ready" != true ]]; then
    echo "API 36 emulator did not expose PackageManager, input, and window services" >&2
    cat "$EMULATOR_LOG" >&2 || true
    exit 1
fi

timeout 10 adb -s emulator-5554 shell settings put global animator_duration_scale 0
timeout 10 adb -s emulator-5554 shell settings put global transition_animation_scale 0
timeout 10 adb -s emulator-5554 shell settings put global window_animation_scale 0
timeout 10 adb -s emulator-5554 shell input keyevent 82 || true

bash "$SMOKE_SCRIPT" "$@"
