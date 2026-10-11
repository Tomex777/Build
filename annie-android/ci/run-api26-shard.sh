#!/usr/bin/env bash
set -euo pipefail

SHARD="${1:?Usage: run-api26-shard.sh <0|1|2|3|canvas|rest|inline|snake|chat>}"
case "$SHARD" in 0|1|2|3|canvas|rest|inline|snake|chat) ;; *) echo "::error::Invalid shard $SHARD" >&2; exit 2 ;; esac

# Each non-process-death instrumentation class is in exactly one shard.
# Process death and release-smoke acceptance remain separate gates.
CLASSES="$(python3 - "$SHARD" <<'PY'
import pathlib, sys
root = pathlib.Path("annie-android/app/src/androidTest/java")
sources = sorted(path for path in root.rglob("*Test.kt")
                 if "processdeath" not in path.parts)
group = sys.argv[1]
names = [p.with_suffix("").relative_to(root).as_posix().replace("/", ".") for p in sources]
if group in ("inline", "snake", "chat"):
    method = {
        "inline": "interactiveCanvasRunsOfflineAndRetainsItsWebViewAcrossFullscreen",
        "snake": "builtInSnakeCommandProducesPlayableCanvasAndCapturesItsChatPreview",
        "chat": "snakeCanvasAppearsInsideTheRealChatAndAcceptsFingerSwipes",
    }[group]
    print("com.tomex777.annie.AnnieCanvasMessageTest#" + method)
    print(f"Android 8 Canvas method: {method}", file=sys.stderr)
    sys.exit(0)
if group in ("canvas", "rest"):
    # Isolate the 9 classes in the one slow shard. Canvas previously had
    # flaky emulator interactions and is tested alone first.
    subset = [n for i, n in enumerate(names) if i % 4 == 1]
    selected = [n for n in subset if (n.endswith(".AnnieCanvasMessageTest")) == (group == "canvas")]
else:
    selected = [n for i, n in enumerate(names) if i % 4 == int(group)]
assert selected, f"Empty Android 8 test shard {group}"
print(",".join(selected))
print(f"Shard {group}: {len(selected)} classes", file=sys.stderr)
PY
)"

python3 annie-android/ci/generic-file-fixture.py > "annie-api26-fixture-${SHARD}.log" 2>&1 &
FIXTURE_PID=$!
trap 'kill "$FIXTURE_PID" 2>/dev/null || true' EXIT

adb reverse tcp:18765 tcp:18765
python3 - <<'PY'
import time, urllib.request
for i in range(40):
    try:
        with urllib.request.urlopen("http://127.0.0.1:18765/sample.blorp", timeout=2) as res:
            assert len(res.read()) == 65536
        break
    except Exception:
        if i == 39:
            raise
        time.sleep(.3)
PY

echo "Android 8 shard $SHARD classes: $CLASSES"
set +e
# Bound a hung instrumentation command to 25 minutes so a single Android 8
# test cannot silently consume the full 80-minute release-gate budget.
timeout --signal=TERM --kill-after=20s 1500s gradle --no-daemon --stacktrace -p annie-android :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$CLASSES"
RESULT=$?
set -e

REPORT_DIR="annie-android/build/api26-shards/${SHARD}"
mkdir -p "$REPORT_DIR"
cp "annie-api26-fixture-${SHARD}.log" "$REPORT_DIR/fixture.log" || true
adb logcat -d > "$REPORT_DIR/logcat.txt" || true
if [ -d annie-android/app/build/outputs/androidTest-results ]; then
  cp -R annie-android/app/build/outputs/androidTest-results "$REPORT_DIR/results"
fi
if [ -d annie-android/app/build/reports/androidTests ]; then
  cp -R annie-android/app/build/reports/androidTests "$REPORT_DIR/reports"
fi
if [ "$RESULT" -ne 0 ]; then
  echo "::error::Android 8 instrumentation shard $SHARD failed: $CLASSES"
fi
exit "$RESULT"
