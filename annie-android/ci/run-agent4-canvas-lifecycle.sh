#!/usr/bin/env bash
# Native Canvas lifecycle + established Snake/WebGL regression acceptance.
# Always collect logs, per-class test XML and screenshot evidence, even if one
# instrumented suite fails or times out. Run from repository root.
set -uo pipefail

REPORT_ROOT="annie-android/build/agent4-evidence"
mkdir -p "$REPORT_ROOT"
adb logcat -c || true
status=0

for suite in AnnieCanvasLifecycleTest AnnieCanvasMessageTest; do
    if [ "$suite" = "AnnieCanvasLifecycleTest" ]; then
        timeout_secs=600
    else
        timeout_secs=900
    fi
    suite_root="$REPORT_ROOT/$suite"
    mkdir -p "$suite_root"
    echo "=== $suite native Android acceptance ===" | tee "$suite_root/status.txt"
    timeout --signal=TERM --kill-after=20s "${timeout_secs}s" \
        gradle --no-daemon --stacktrace -p annie-android \
        :app:connectedDebugAndroidTest \
        "-Pandroid.testInstrumentationRunnerArguments.class=com.tomex777.annie.$suite" \
        > "$suite_root/gradle.log" 2>&1
    outcome=$?
    echo "gradle_exit=$outcome" | tee -a "$suite_root/status.txt"
    if [ "$outcome" -ne 0 ]; then
        status=1
        tail -n 100 "$suite_root/gradle.log" || true
    fi

    if [ -d annie-android/app/build/outputs/androidTest-results ]; then
        cp -R annie-android/app/build/outputs/androidTest-results "$suite_root/results"
    else
        echo "Missing Android instrumentation XML output" | tee -a "$suite_root/status.txt"
        status=1
    fi
    adb logcat -d > "$suite_root/logcat.txt" 2>&1 || status=1
done

adb pull /sdcard/Pictures/AnnieCI "$REPORT_ROOT/screenshots" \
    > "$REPORT_ROOT/screenshot-pull.log" 2>&1 || true

# Check exact required tests from BOTH suites, not merely Gradle's exit code.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

base = Path("annie-android/build/agent4-evidence")
required = {
    "AnnieCanvasLifecycleTest": {
        "hostPauseIsPerCanvasResumeKeepsStateAndCloseReleasesTheView",
        "pausedCanvasStaysPausedAcrossFullscreenAndReturnsToTheSameWebView",
        "hostLifecycleStopFreezesTimersWithoutUserPausingAndStartResumes",
        "canvasClippedByComposeViewportPausesWithoutBeingDestroyed",
        "userClosedCanvasRemainsClosedAfterItsLazyMessageControllerIsEvicted",
        "manuallyPausedCanvasRemainsPausedAfterLazyItemEviction",
        "closeCardRemainsClosedAfterComposeSavedStateRestoration",
    },
    "AnnieCanvasMessageTest": {
        "webGlShadersDrawReadPixelsAndSurviveFullscreen",
        "interactiveCanvasRunsOfflineAndRetainsItsWebViewAcrossFullscreen",
        "builtInSnakeCommandProducesPlayableCanvasAndCapturesItsChatPreview",
        "snakeCanvasAppearsInsideTheRealChatAndAcceptsFingerSwipes",
    },
}

errors = []
for suite, expected in required.items():
    got = {}
    for xml in (base / suite / "results").rglob("*.xml"):
        try:
            root = ET.parse(xml).getroot()
        except (ET.ParseError, OSError) as exc:
            errors.append(f"{suite}: unreadable {xml}: {exc}")
            continue
        for case in root.iter("testcase"):
            name = case.get("name")
            if name in expected:
                got[name] = case
    missing = expected - got.keys()
    if missing:
        errors.append(f"{suite}: missing executed methods: {sorted(missing)}")
    for name in expected & got.keys():
        case = got[name]
        failures = [
            node for tag in ("failure", "error", "skipped")
            for node in case.findall(tag)
        ]
        if failures:
            errors.append(f"{suite}.{name}: " + "; ".join(
                (node.get("message") or node.text or node.tag).strip()
                for node in failures
            ))
    print(f"{suite}: {len(got)}/{len(expected)} required native cases located")

# Do not equate a green Gradle build with the visual evidence gate.
for screenshot in (
    "annie-ci-annie-agent4-two-canvases-paused.png",
    "annie-ci-annie-agent4-canvas-closed-card.png",
    "annie-ci-annie-agent4-canvas-reopened.png",
    "annie-ci-annie-agent4-fullscreen-paused.png",
):
    matches = list((base / "screenshots").rglob(screenshot))
    if not matches or not any(path.stat().st_size >= 2048 for path in matches):
        errors.append(f"Missing/empty emulator screenshot: {screenshot}")

summary = base / "results-summary.txt"
summary.write_text(
    "PASS API-specific Canvas acceptance\n" if not errors
    else "FAIL native Canvas acceptance:\n" + "\n".join(errors) + "\n"
)
print(summary.read_text())
raise SystemExit(1 if errors else 0)
PY
test_status=$?
if [ "$test_status" -ne 0 ]; then status=1; fi

echo "Agent 4 native test status=$status"
exit "$status"
