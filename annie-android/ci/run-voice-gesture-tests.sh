#!/usr/bin/env bash
set -uo pipefail
REPORT_ROOT=annie-android/build/repair-reports
mkdir -p "$REPORT_ROOT"
timeout --signal=TERM --kill-after=20s 420s gradle --no-daemon --stacktrace -p annie-android \
    :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.tomex777.annie.AnnieVoiceGestureTest
status=$?
cp -R annie-android/app/build/outputs/androidTest-results "$REPORT_ROOT/outputs" || status=1
adb logcat -d > "$REPORT_ROOT/logcat.txt"
python3 annie-android/ci/verify-rc-feature-report.py "$REPORT_ROOT/outputs" AnnieVoiceGestureTest || status=1
exit "$status"
