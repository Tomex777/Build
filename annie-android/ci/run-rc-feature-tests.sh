#!/usr/bin/env bash
set -euo pipefail

REPORT_ROOT=annie-android/build/rc-feature-reports
mkdir -p "$REPORT_ROOT"
classes=(
  ChatHistoryTest
  ScriptStudioFileCreationTest
  AnnieVoiceRecordingTest
  AnnieVoiceGestureTest
  AnnieVoicePauseResumeTest
  AnnieGeckoTabRestoreTest
  AnnieGeckoDownloadHistoryTest
  AnnieGeckoDownloadManagerTest
  AnnieGeckoBrowserUiTest
  AnnieGeckoBrowserEndToEndTest
  AnnieImageViewerTest
  AnnieImageEditorTest
  AnnieGeckoFilePickerTest
)
status=0
for class in "${classes[@]}"; do
    echo "Run acceptance class: $class"
    set +e
    timeout --signal=TERM --kill-after=20s 300s gradle --no-daemon --stacktrace -p annie-android \
        :app:connectedDebugAndroidTest \
        "-Pandroid.testInstrumentationRunnerArguments.class=com.tomex777.annie.$class"
    result=$?
    set -e
    target="$REPORT_ROOT/$class"
    mkdir -p "$target"
    if [ -d annie-android/app/build/outputs/androidTest-results ]; then
        cp -R annie-android/app/build/outputs/androidTest-results "$target/outputs"
    fi
    if [ -d annie-android/app/build/reports/androidTests ]; then
        cp -R annie-android/app/build/reports/androidTests "$target/reports"
    fi
    adb logcat -d -s TestRunner:I AnnieGeckoFixture:W AndroidRuntime:E > "$target/logcat.txt" || true
    if [ "$result" -ne 0 ]; then status=1; fi
    if ! python3 annie-android/ci/verify-rc-feature-report.py "$target/outputs" "$class"; then
        status=1
    fi
done
exit "$status"
