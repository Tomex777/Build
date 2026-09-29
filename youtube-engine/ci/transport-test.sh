#!/usr/bin/env bash
set -uo pipefail
result=0
gradle --no-daemon -p youtube-engine :youtube-engine-testapp:connectedDebugAndroidTest || result=$?
adb logcat -d -v brief | grep YT_PROOF > youtube-engine/transport-proof.txt || true
cat youtube-engine/transport-proof.txt
mkdir -p youtube-engine/live-player-debug

# Instrumentation may uninstall the target package before this script can run-as it.
# Re-fetch only the exact trusted player bundle URLs emitted by the live test so the
# transport artifact always contains the bundle that produced the diagnostics.
download_player() {
  local label="$1"
  local output="$2"
  local url
  url="$(sed -n "s#.*YT_PROOF ${label} url=\\(https://www\\.youtube\\.com/s/player/[^ ]*/base\\.js\\).*#\\1#p" youtube-engine/transport-proof.txt | tail -n 1)"
  case "$url" in
    https://www.youtube.com/s/player/*/base.js)
      curl --fail --silent --show-error --location --max-time 30 "$url" -o "youtube-engine/live-player-debug/$output" ||
        rm -f "youtube-engine/live-player-debug/$output"
      ;;
  esac
}

download_player "live-player-js" "live-player-ias.js"
download_player "live-player-sibling" "live-player-sibling.js"
download_player "live-player-embed" "live-player-embed.js"
download_player "iframe-player" "live-player-iframe.js"

# Keep the private-files path as a best-effort fallback for local/emulator runs where
# the target package remains installed after instrumentation.
for name in live-player-ias.js live-player-sibling.js live-player-embed.js live-player-iframe.js; do
  if [ ! -s "youtube-engine/live-player-debug/$name" ]; then
    adb exec-out run-as dev.tomex.youtube.testapp cat "files/$name" > "youtube-engine/live-player-debug/$name" 2>/dev/null ||
      rm -f "youtube-engine/live-player-debug/$name"
  fi
done
exit "$result"
