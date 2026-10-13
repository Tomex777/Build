#!/usr/bin/env bash
set -uo pipefail
REPORT_ROOT=annie-android/build/repair-reports
mkdir -p "$REPORT_ROOT"
status=0
for class in ApiTwoRuntimeTest GuestRuntimeTest HostAiRuntimeTest AnnieCanvasMessageTest TypeScriptRuntimeTest ScriptStudioFileCreationTest AnnieImageEditorTest; do
    timeout --signal=TERM --kill-after=20s 420s gradle --no-daemon --stacktrace -p annie-android \
        :app:connectedDebugAndroidTest \
        "-Pandroid.testInstrumentationRunnerArguments.class=com.tomex777.annie.$class" || status=1
    mkdir -p "$REPORT_ROOT/$class"
    cp -R annie-android/app/build/outputs/androidTest-results "$REPORT_ROOT/$class/outputs" || status=1
    python3 annie-android/ci/verify-rc-feature-report.py "$REPORT_ROOT/$class/outputs" "$class" || status=1
done
adb logcat -d > "$REPORT_ROOT/logcat.txt"
mkdir -p "$REPORT_ROOT/screenshots"
adb pull /sdcard/Pictures/AnnieCI "$REPORT_ROOT/screenshots" || true
exit "$status"
