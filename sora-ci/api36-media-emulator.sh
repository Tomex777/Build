#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/sora-bible-fixture.log
python3 "$GITHUB_WORKSPACE/sora-ci/bible-fixture-server.py" > "$OUT" 2>&1 &
bible_fixture_pid=$!

finish_bible_fixture() {
  local status=$?
  trap - EXIT
  if (( status != 0 )); then
    echo 'Bible fixture access log:'
    cat "$OUT" || true
    if command -v adb >/dev/null 2>&1; then
      echo 'Bible reader logcat:'
      adb logcat -d -t 1500 2>/dev/null | grep -E 'SoraBible|AndroidRuntime|FATAL EXCEPTION' || true
    fi
  fi
  kill "$bible_fixture_pid" 2>/dev/null || true
  wait "$bible_fixture_pid" 2>/dev/null || true
  exit "$status"
}
trap finish_bible_fixture EXIT

ready=0
for attempt in {1..20}; do
  if curl -fsS http://127.0.0.1:8765/health | grep -q '"status": "ok"'; then
    ready=1
    break
  fi
  sleep 1
done
if [[ "$ready" != 1 ]]; then
  cat "$OUT" >&2 || true
  exit 1
fi
curl -fsS 'http://127.0.0.1:8765/Genesis+1?translation=web' |
  python3 -c 'import json,sys; assert json.load(sys.stdin)["verses"][0]["text"].startswith("In the beginning")'

bash "$GITHUB_WORKSPACE/sora-ci/anime-manga-live-smoke.sh"
bash "$GITHUB_WORKSPACE/sora-ci/hub-tabs-smoke.sh"
