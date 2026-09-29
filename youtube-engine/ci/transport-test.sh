#!/usr/bin/env bash
set -uo pipefail

result=0

if [ "${YT_SPLIT_INSTRUMENTATION:-0}" = "1" ]; then
  main_apk="$(find youtube-engine/youtube-engine-testapp/build/outputs/apk/debug -type f -name '*x86_64*debug.apk' | head -n 1)"
  test_apk="$(find youtube-engine/youtube-engine-testapp/build/outputs/apk/androidTest/debug -type f -name '*.apk' | head -n 1)"

  if [ -z "$main_apk" ] || [ ! -f "$main_apk" ] || [ -z "$test_apk" ] || [ ! -f "$test_apk" ]; then
    echo "Missing API 26 instrumentation APKs"
    result=1
  else
    adb install -r "$main_apk" || result=1
    adb install -r "$test_apk" || result=1

    run_test_class() {
      local class_name="$1"
      local output
      local status=0
      output="$(adb shell am instrument -w -r \
        -e class "$class_name" \
        dev.tomex.youtube.testapp.test/androidx.test.runner.AndroidJUnitRunner 2>&1)" || status=$?
      printf '%s\n' "$output"
      if [ "$status" -ne 0 ] || ! grep -Eq '^OK \([0-9]+ tests?\)' <<<"$output"; then
        result=1
      fi

      # Do not let one instrumentation process retain the other's app/native heap
      # on the smaller API 26 emulator.
      adb shell am force-stop dev.tomex.youtube.testapp >/dev/null 2>&1 || true
      adb shell am force-stop dev.tomex.youtube.testapp.test >/dev/null 2>&1 || true
    }

    # Keep Media3 decode acceptance and the multi-megabyte live-player runtime proof
    # in separate instrumentation processes on API 26.
    run_test_class dev.tomex.youtube.testapp.PlaybackAcceptanceTest
    run_test_class dev.tomex.youtube.testapp.RealTransportTest
  fi
else
  gradle --no-daemon -p youtube-engine :youtube-engine-testapp:connectedDebugAndroidTest || result=$?
fi

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
  url="$(sed -n "s#.*YT_PROOF ${label} url=\(https://www\.youtube\.com/s/player/[^ ]*/base\.js\).*#\1#p" youtube-engine/transport-proof.txt | tail -n 1)"
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
