#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 scripts/test-repair-contract.py
python3 scripts/repair-m0b-merge.py
if [ ! -f ./gradlew ]; then
  echo 'Missing gradlew: overlay this ZIP onto the COMPLETE annie-android repo first.' >&2
  exit 2
fi
if [ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  echo 'ANDROID_HOME or ANDROID_SDK_ROOT is not set; an Android SDK is required.' >&2
  exit 2
fi
./gradlew --no-daemon testDebugUnitTest
sh scripts/check-annie-types.sh
./gradlew --no-daemon assembleDebug
printf '\nAPK path: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
