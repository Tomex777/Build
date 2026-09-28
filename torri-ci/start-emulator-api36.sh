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

# Keep avdmanager and emulator on the exact same explicit storage root. The
# hosted runner can expose multiple Android home conventions; relying on HOME
# allowed avdmanager to report success while emulator searched another folder.
ANDROID_USER_HOME="${RUNNER_TEMP:-/tmp}/torri-android-user"
ANDROID_AVD_HOME="${RUNNER_TEMP:-/tmp}/torri-avd"
export ANDROID_USER_HOME ANDROID_AVD_HOME
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"

cleanup() {
    local result=$?
    trap - EXIT
    set +e
    if [[ "$result" -ne 0 ]]; then
        adb -s emulator-5554 logcat -d -b all > "$EVIDENCE_DIR/boot-or-runtime-logcat.txt" 2>/dev/null
        adb -s emulator-5554 shell getprop > "$EVIDENCE_DIR/emulator-properties.txt" 2>/dev/null
        adb -s emulator-5554 shell service list > "$EVIDENCE_DIR/emulator-services.txt" 2>/dev/null
        {
            echo "ANDROID_USER_HOME=$ANDROID_USER_HOME"
            echo "ANDROID_AVD_HOME=$ANDROID_AVD_HOME"
            echo
            find "$ANDROID_AVD_HOME" -maxdepth 2 -type f -o -type d 2>/dev/null | sort
        } > "$EVIDENCE_DIR/avd-layout.txt"
    fi
    adb -s emulator-5554 emu kill >/dev/null 2>&1 || true
    if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
        kill "$EMULATOR_PID" >/dev/null 2>&1 || true
        sleep 2
        kill -9 "$EMULATOR_PID" >/dev/null 2>&1 || true
    fi
    exit "$result"
}
trap cleanup EXIT

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then SDKMANAGER="$ANDROID_HOME/cmdline-tools/16.0/bin/sdkmanager"; fi
AVDMANAGER="$(dirname "$SDKMANAGER")/avdmanager"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "platforms;android-36" "emulator" "system-images;android-36;google_apis;x86_64"

rm -rf "$ANDROID_AVD_HOME/api36-smoke.avd" "$ANDROID_AVD_HOME/api36-smoke.ini"
printf 'no\n' | "$AVDMANAGER" create avd --force -n api36-smoke -k "system-images;android-36;google_apis;x86_64" -d pixel_6

echo "Created API 36 AVD in $ANDROID_AVD_HOME"
"$ANDROID_HOME/emulator/emulator" -list-avds | tee "$EVIDENCE_DIR/avd-list.txt"
test -f "$ANDROID_AVD_HOME/api36-smoke.ini"
test -d "$ANDROID_AVD_HOME/api36-smoke.avd"
grep -q '^api36-smoke$' "$EVIDENCE_DIR/avd-list.txt"

ACCEL_ARGS=(-accel off)
if [[ -e /dev/kvm ]]; then
    sudo chmod a+rw /dev/kvm >/dev/null 2>&1 || true
    if "$ANDROID_HOME/emulator/emulator" -accel-check >"$EVIDENCE_DIR/accel-check.txt" 2>&1; then
        ACCEL_ARGS=(-accel on)
    fi
else
    echo "No /dev/kvm on runner; using software acceleration" > "$EVIDENCE_DIR/accel-check.txt"
fi

"$ANDROID_HOME/emulator/emulator" -port 5554 -avd api36-smoke \
    -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim -memory 3072 "${ACCEL_ARGS[@]}" \
    >"$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!
adb start-server

# Never use an unbounded adb wait here. If emulator exits before registering
# with ADB, adb wait-for-device otherwise blocks until the whole CI job dies.
adb_ready=false
for attempt in $(seq 1 120); do
    if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
        echo "API 36 emulator exited before registering with ADB" >&2
        cat "$EMULATOR_LOG" >&2 || true
        exit 1
    fi
    state="$(adb -s emulator-5554 get-state 2>/dev/null || true)"
    if [[ "$state" == "device" ]]; then
        adb_ready=true
        break
    fi
    if (( attempt % 10 == 0 )); then
        echo "Waiting for API 36 emulator ADB registration (attempt $attempt/120)"
        tail -n 40 "$EMULATOR_LOG" || true
    fi
    sleep 3
done
if [[ "$adb_ready" != true ]]; then
    echo "API 36 emulator did not register with ADB" >&2
    cat "$EMULATOR_LOG" >&2 || true
    exit 1
fi

ready=false
for attempt in $(seq 1 120); do
    if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
        echo "API 36 emulator exited during Android boot" >&2
        cat "$EMULATOR_LOG" >&2 || true
        exit 1
    fi
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

# Do not send KEYCODE_MENU/HOME to "unlock" a freshly booted API 36 image.
# On the Google APIs image that dispatches to Pixel Launcher while it is still
# starting and can itself trigger an ANR that obscures the app under test.
adb -s emulator-5554 shell wm dismiss-keyguard >/dev/null 2>&1 || true
sleep 2

bash "$SMOKE_SCRIPT" "$@"
