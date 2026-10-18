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
# Isolate the two real-render suites: one stuck instrumented test should
# not hide the failure cause or block the screenshots from the other.
: > mise-under-tree-test.log
result=0
for suite in UnderTreeSceneInstrumentedTest MountainTerrainInstrumentedTest; do
  echo "===== Mise Android QA suite: $suite =====" | tee -a mise-under-tree-test.log
  timeout --signal=TERM --kill-after=10s 300s gradle :app:connectedDebugAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    "-Pandroid.testInstrumentationRunnerArguments.class=studio.artistscene.app.$suite" \
    --rerun-tasks --stacktrace 2>&1 | tee -a mise-under-tree-test.log
  outcome=${PIPESTATUS[0]}
  if [[ "$outcome" -ne 0 ]]; then
    echo "Mise suite FAILED: $suite (status $outcome)" | tee -a mise-under-tree-test.log
    result=1
  fi
done

adb pull /sdcard/Android/data/studio.artistscene.app/files/under-tree-clean.png under-tree-proof/under-tree-clean.png || true
adb pull /sdcard/Android/data/studio.artistscene.app/files/mountain-real-3d.png under-tree-proof/mountain-real-3d.png || true
adb pull /sdcard/Android/data/studio.artistscene.app/files/mountain-empty.png under-tree-proof/mountain-empty.png || true
adb pull /sdcard/Android/data/studio.artistscene.app/files/under-tree-empty.png under-tree-proof/under-tree-empty.png || true
adb logcat -d -v time > under-tree-proof/logcat.txt || true
echo "Mise scene test result: $result"
exit "$result"
