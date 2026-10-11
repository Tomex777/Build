#!/usr/bin/env bash
set -uo pipefail

# Collect independent Canvas evidence even if another acceptance class fails.
# Both failures remain release blockers.
bash annie-android/ci/run-rc-feature-tests.sh
FEATURE_STATUS=$?
timeout --signal=TERM --kill-after=20s 300s gradle --no-daemon --stacktrace -p annie-android \
    :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.tomex777.annie.AnnieCanvasMessageTest
CANVAS_STATUS=$?
if [ "$FEATURE_STATUS" -ne 0 ]; then exit "$FEATURE_STATUS"; fi
exit "$CANVAS_STATUS"
