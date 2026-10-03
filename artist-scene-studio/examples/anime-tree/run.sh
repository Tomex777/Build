#!/usr/bin/env bash
set -u
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell wm size 1080x1920
adb shell wm density 240
set +e
gradle :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=studio.artistscene.app.AnimeTreeSceneTest --stacktrace > anime-scene-test.log 2>&1
result=$?
mkdir -p anime-proof
adb pull /sdcard/Android/data/studio.artistscene.app/files/ anime-proof/
adb shell screencap -p /sdcard/anime-final.png
adb pull /sdcard/anime-final.png anime-proof/final.png
adb logcat -d -v threadtime > anime-proof/logcat.txt
adb shell uiautomator dump /sdcard/anime-window.xml
adb pull /sdcard/anime-window.xml anime-proof/window.xml
# adb pull creates files/ under the destination; collect its screenshot files at the top.
find anime-proof/files -maxdepth 1 -name '*.png' -exec cp {} anime-proof/ \; 2>/dev/null
cat anime-scene-test.log
exit "$result"
