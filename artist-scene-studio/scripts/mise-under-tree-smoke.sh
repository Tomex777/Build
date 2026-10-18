#!/usr/bin/env bash
# Capture the REAL Mise Android test and keep test failures visible in Actions.
set -uo pipefail
mkdir -p under-tree-proof
# Higher-density real Android 16 render proof; API 26 retains its stable
# compact GPU surface so minimum-SDK coverage remains reliable.
if [[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 35 ]]; then
  adb shell wm size 720x1280
  adb shell wm density 320
  sleep 2
fi
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
