#!/usr/bin/env bash
set -euo pipefail
mkdir -p dist/coexistence
adb install -r apps/nami/app/build/outputs/apk/debug/app-x86_64-debug.apk
adb install -r apps/nami/test-fixtures/nami-native-extension-fixture/build/outputs/apk/debug/nami-native-extension-fixture-debug.apk
adb shell am start -W -n app.nami.android/.MainActivity
adb shell run-as app.nami.android sh -c 'echo NAMI_PRIVATE_STATE > files/coexistence-marker'
adb install -r apps/mira/app/build/outputs/apk/debug/app-x86_64-debug.apk
adb install -r apps/mira/test-fixtures/mira-native-extension-fixture/build/outputs/apk/debug/mira-native-extension-fixture-debug.apk
adb install -r apps/mira/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell run-as app.mira.android sh -c 'echo MIRA_PRIVATE_STATE > files/coexistence-marker'
for package in app.nami.android app.mira.android; do
    adb shell pm path "$package" | tee -a dist/coexistence/packages.txt
    adb shell am force-stop "$package"
    adb shell am start -W -n "$package/.MainActivity" | tee "dist/coexistence/$package-launch.txt"
    sleep 5
    adb shell pidof "$package"
    adb exec-out screencap -p > "dist/coexistence/$package.png"
done
adb shell run-as app.nami.android cat files/coexistence-marker | tr -d '\r' | grep -qx NAMI_PRIVATE_STATE
adb shell run-as app.mira.android cat files/coexistence-marker | tr -d '\r' | grep -qx MIRA_PRIVATE_STATE
output=$(adb shell am instrument -w -r -e class app.mira.android.MiraExtensionAbiTest app.mira.android.test/androidx.test.runner.AndroidJUnitRunner)
printf '%s\n' "$output" | tee dist/coexistence/mira-extension-abi.txt
printf '%s\n' "$output" | grep -Eq 'OK .+ tests?'
adb uninstall app.mira.android
adb shell pm path app.nami.android
adb shell run-as app.nami.android cat files/coexistence-marker | tr -d '\r' | grep -qx NAMI_PRIVATE_STATE
printf '%s\n' 'Private storage survives sibling install, restart, and uninstall.' > dist/coexistence/storage-isolation.txt
