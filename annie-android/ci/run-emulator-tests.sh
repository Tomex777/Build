#!/usr/bin/env bash
set -euo pipefail

set +e
gradle --no-daemon --stacktrace -p annie-android :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.notPackage=com.tomex777.annie.processdeath
TEST_STATUS=$?
set -e
PROCESS_STATUS=0
SCREENSHOT_DIR="annie-android/build/emulator-screenshots"
CI_REPORT_DIR="annie-android/build/ci-test-reports"
mkdir -p "$SCREENSHOT_DIR" "$CI_REPORT_DIR/full-suite"

if [ -d annie-android/app/build/outputs/androidTest-results ]; then
    cp -R annie-android/app/build/outputs/androidTest-results "$CI_REPORT_DIR/full-suite/outputs"
fi
if [ -d annie-android/app/build/reports/androidTests ]; then
    cp -R annie-android/app/build/reports/androidTests "$CI_REPORT_DIR/full-suite/reports"
fi

if [ "$TEST_STATUS" -eq 0 ]; then
    set +e
    APP_DEBUG_APK="$(find annie-android/app/build/outputs/apk/debug -maxdepth 1 -type f -name 'app-x86_64-debug.apk' -print -quit)"
    TEST_DEBUG_APK="$(find annie-android/app/build/outputs/apk -type f -path '*/androidTest/debug/*.apk' -print -quit)"
    SEED_STATUS=1
    if [ -n "$APP_DEBUG_APK" ] && [ -n "$TEST_DEBUG_APK" ]; then
        adb install -r "$APP_DEBUG_APK"
        APP_INSTALL_STATUS=$?
        adb install -t -r "$TEST_DEBUG_APK"
        TEST_INSTALL_STATUS=$?
        if [ "$APP_INSTALL_STATUS" -eq 0 ] && [ "$TEST_INSTALL_STATUS" -eq 0 ]; then
            SEED_OUTPUT="$(adb shell am instrument -w -e class com.tomex777.annie.processdeath.ProcessDeathSeedTest com.tomex777.annie.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
            SEED_STATUS=$?
            printf '%s\n' "$SEED_OUTPUT"
            if ! printf '%s\n' "$SEED_OUTPUT" | grep -Fq 'OK (1 test)'; then
                SEED_STATUS=1
            fi
        fi
    fi
    FORCE_STOP_STATUS=1
    LAUNCH_STATUS=1
    RESTORE_STATUS=1
    PERSISTED_STATUS=1
    if [ "$SEED_STATUS" -eq 0 ]; then
        adb shell am force-stop com.tomex777.annie
        FORCE_STOP_STATUS=$?
        if [ "$FORCE_STOP_STATUS" -eq 0 ]; then
            adb shell am start -W -n com.tomex777.annie/.MainActivity
            LAUNCH_STATUS=$?
        fi
    fi
    set -e

    if [ "$SEED_STATUS" -eq 0 ] && [ "$FORCE_STOP_STATUS" -eq 0 ] && [ "$LAUNCH_STATUS" -eq 0 ]; then
        PROCESS_DEATH_XML="/sdcard/annie-process-death-hierarchy.xml"
        for attempt in $(seq 1 20); do
            if adb shell uiautomator dump "$PROCESS_DEATH_XML" >/dev/null 2>&1 && \
                adb shell cat "$PROCESS_DEATH_XML" | tr -d '\r' | grep -Fq 'Conversation restored after process death'; then
                RESTORE_STATUS=0
                break
            fi
            sleep 1
        done
        if adb shell run-as com.tomex777.annie cat shared_prefs/annie_chat_history_v1.xml \
            > "$CI_REPORT_DIR/process-death-shared-preferences.xml" 2>/dev/null && \
            grep -Fq 'ci_process_death_seeded' "$CI_REPORT_DIR/process-death-shared-preferences.xml" && \
            grep -Fq 'Conversation restored after process death' "$CI_REPORT_DIR/process-death-shared-preferences.xml"; then
            PERSISTED_STATUS=0
        fi
        adb exec-out screencap -p > "$SCREENSHOT_DIR/annie-process-death-restored-chat.png" || true
    fi

    if [ "$SEED_STATUS" -ne 0 ] || [ "$FORCE_STOP_STATUS" -ne 0 ] || \
        [ "$LAUNCH_STATUS" -ne 0 ] || [ "$RESTORE_STATUS" -ne 0 ] || [ "$PERSISTED_STATUS" -ne 0 ]; then
        PROCESS_STATUS=1
    fi
fi

echo "===== Android instrumented test XML ====="
find annie-android/app/build/outputs/androidTest-results "$CI_REPORT_DIR" -type f -name '*.xml' -print -exec cat {} \; 2>/dev/null || true

echo "===== Collect emulator screenshots ====="
adb shell ls -la /sdcard/Pictures/AnnieCI || true
adb pull /sdcard/Pictures/AnnieCI "$SCREENSHOT_DIR" || true
adb shell ls -la /sdcard/Android/data/com.tomex777.annie/files/Pictures/AnnieCI || true
adb pull /sdcard/Android/data/com.tomex777.annie/files/Pictures/AnnieCI "$SCREENSHOT_DIR" || true
echo "Collected $(find "$SCREENSHOT_DIR" -maxdepth 1 -type f -name '*.png' | wc -l) PNG screenshots."

RELEASE_STATUS=0
if [ "$TEST_STATUS" -eq 0 ] && [ "$PROCESS_STATUS" -eq 0 ]; then
    echo "===== Smoke-test installable release APK ====="
    RELEASE_APK="$(find annie-android/app/build/outputs/apk/release -maxdepth 1 -type f -name 'app-x86_64-release-unsigned.apk' -print -quit)"
    APKSIGNER="$(command -v apksigner || true)"
    if [ -z "$APKSIGNER" ] && [ -n "${ANDROID_HOME:-}" ]; then
        APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
    fi
    if [ -z "$RELEASE_APK" ] || [ -z "$APKSIGNER" ] || [ ! -f "$HOME/.android/debug.keystore" ]; then
        echo "::error::Release smoke prerequisites are missing."
        RELEASE_STATUS=1
    else
        RELEASE_SMOKE_APK="${RUNNER_TEMP:-/tmp}/annie-x86_64-release-ci-signed.apk"
        cp "$RELEASE_APK" "$RELEASE_SMOKE_APK"
        set +e
        "$APKSIGNER" sign \
            --ks "$HOME/.android/debug.keystore" \
            --ks-key-alias androiddebugkey \
            --ks-pass pass:android \
            --key-pass pass:android \
            "$RELEASE_SMOKE_APK"
        SIGN_STATUS=$?
        adb uninstall com.tomex777.annie >/dev/null 2>&1 || true
        adb install -r "$RELEASE_SMOKE_APK"
        INSTALL_STATUS=$?
        LAUNCH_OUTPUT="$(adb shell am start -W -n com.tomex777.annie/.MainActivity 2>&1)"
        LAUNCH_STATUS=$?
        printf '%s\n' "$LAUNCH_OUTPUT"
        set -e

        RELEASE_VISIBLE=1
        if [ "$SIGN_STATUS" -eq 0 ] && [ "$INSTALL_STATUS" -eq 0 ] && [ "$LAUNCH_STATUS" -eq 0 ]; then
            RELEASE_XML="/sdcard/annie-release-hierarchy.xml"
            for attempt in $(seq 1 20); do
                if adb shell uiautomator dump "$RELEASE_XML" >/dev/null 2>&1 && \
                    adb shell cat "$RELEASE_XML" | tr -d '\r' | grep -Fq 'Annie'; then
                    RELEASE_VISIBLE=0
                    break
                fi
                sleep 1
            done
            adb exec-out screencap -p > "$SCREENSHOT_DIR/annie-release-launch.png" || true
        fi

        if [ "$SIGN_STATUS" -ne 0 ] || [ "$INSTALL_STATUS" -ne 0 ] || \
            [ "$LAUNCH_STATUS" -ne 0 ] || [ "$RELEASE_VISIBLE" -ne 0 ]; then
            echo "::error::Release APK did not sign, install, launch, and render Annie successfully."
            RELEASE_STATUS=1
        fi
    fi
fi

if [ "$TEST_STATUS" -ne 0 ] || [ "$PROCESS_STATUS" -ne 0 ] || [ "$RELEASE_STATUS" -ne 0 ]; then
    adb logcat -d | grep -Ei 'libvlc|vlc|vout|video output|get_buffer|decoder|h264|android_display|AnnieVLC|VideoHelper|Invalid surface size|can.t get Video Surface|EGL|GLES|egl|emugl|SurfaceView|AndroidRuntime|ActivityTaskManager|ProcessDeath' > "$SCREENSHOT_DIR/diagnostic-logcat.txt" || true
fi

if [ "$TEST_STATUS" -eq 0 ] && [ "$PROCESS_STATUS" -eq 0 ] && [ -z "$(find "$SCREENSHOT_DIR" -type f -name '*.png' -print -quit)" ]; then
    echo "::error::Emulator tests passed without producing retrievable screenshots."
    TEST_STATUS=1
fi

adb shell rm -rf /sdcard/Pictures/AnnieCI || true
if [ "$TEST_STATUS" -ne 0 ]; then exit "$TEST_STATUS"; fi
if [ "$PROCESS_STATUS" -ne 0 ]; then exit "$PROCESS_STATUS"; fi
exit "$RELEASE_STATUS"
