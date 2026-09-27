#!/usr/bin/env bash
set -uo pipefail
result=0
gradle --no-daemon -p youtube-engine :youtube-engine-testapp:connectedDebugAndroidTest || result=$?
adb logcat -d -v brief | grep YT_PROOF > youtube-engine/transport-proof.txt || true
cat youtube-engine/transport-proof.txt
exit "$result"
