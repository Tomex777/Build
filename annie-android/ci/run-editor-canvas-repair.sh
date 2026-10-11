#!/usr/bin/env bash
set -uo pipefail

REPORT_ROOT=annie-android/build/repair-reports
mkdir -p "$REPORT_ROOT"
adb logcat -c
adb logcat -v threadtime -s TestRunner:I AnnieCanvasTest:I AndroidRuntime:E > "$REPORT_ROOT/checkpoints.txt" 2>&1 &
LOGGER_PID=$!
trap 'kill "$LOGGER_PID" 2>/dev/null || true' EXIT
status=0
for target in 'ScriptChatFlowTest#editorSupportsSelectionDeletionReplacementClipboardAndMultilineRanges' AnnieCanvasMessageTest; do
    timeout --signal=TERM --kill-after=20s 420s gradle --no-daemon --stacktrace -p annie-android \
        :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tomex777.annie.$target"
    result=$?
    destination="$REPORT_ROOT/${target%%#*}"
    mkdir -p "$destination"
    cp -R annie-android/app/build/outputs/androidTest-results "$destination/outputs" || status=1
    adb logcat -d > "$destination/logcat.txt"
    if [ "$result" -ne 0 ]; then status=1; fi
done
adb pull /sdcard/Pictures/AnnieCI "$REPORT_ROOT/screenshots" || true
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
expected = {
    ('com.tomex777.annie.ScriptChatFlowTest', 'editorSupportsSelectionDeletionReplacementClipboardAndMultilineRanges'),
    *{('com.tomex777.annie.AnnieCanvasMessageTest', name) for name in (
        'interactiveCanvasRunsOfflineAndRetainsItsWebViewAcrossFullscreen',
        'builtInSnakeCommandProducesPlayableCanvasAndCapturesItsChatPreview',
        'snakeCanvasAppearsInsideTheRealChatAndAcceptsFingerSwipes')},
}
cases = {}
for path in Path('annie-android/build/repair-reports').rglob('*.xml'):
    root = ET.parse(path).getroot()
    for case in root.iter('testcase'):
        cases[(case.get('classname'), case.get('name'))] = case
assert expected <= cases.keys(), f'Missing methods: {expected - cases.keys()}'
for key in expected:
    assert all(cases[key].find(tag) is None for tag in ('failure', 'error', 'skipped')), key
print('PASS editor selection/clipboard and all three native Canvas acceptance methods')
PY
if [ "$?" -ne 0 ]; then status=1; fi
exit "$status"
