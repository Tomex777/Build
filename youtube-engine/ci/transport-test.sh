#!/usr/bin/env bash
set -uo pipefail
result=0
gradle --no-daemon -p youtube-engine :youtube-engine-testapp:connectedDebugAndroidTest || result=$?
adb logcat -d -v brief | grep YT_PROOF > youtube-engine/transport-proof.txt || true
cat youtube-engine/transport-proof.txt
mkdir -p youtube-engine/live-player-debug
for name in live-player-ias.js live-player-sibling.js live-player-embed.js; do
  adb exec-out run-as dev.tomex.youtube.testapp cat "files/$name" > "youtube-engine/live-player-debug/$name" 2>/dev/null || rm -f "youtube-engine/live-player-debug/$name"
done
exit "$result"
