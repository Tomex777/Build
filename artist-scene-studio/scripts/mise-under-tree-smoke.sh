#!/usr/bin/env bash
# Capture the REAL Mise Android test and keep test failures visible in Actions.
set -uo pipefail
mkdir -p under-tree-proof
gradle :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=studio.artistscene.app.UnderTreeSceneInstrumentedTest \
  --stacktrace 2>&1 | tee mise-under-tree-test.log
result=${PIPESTATUS[0]}

adb pull /sdcard/Android/data/studio.artistscene.app/files/under-tree-clean.png under-tree-proof/under-tree-clean.png || true
adb pull /sdcard/Android/data/studio.artistscene.app/files/under-tree-empty.png under-tree-proof/under-tree-empty.png || true
adb logcat -d -v time > under-tree-proof/logcat.txt || true
echo "Mise scene test result: $result"
exit "$result"
