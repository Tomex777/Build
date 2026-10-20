# NIGHT Play — Agent 10: Independent full-catalog long-session gameplay QA

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 10". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are 10IGHT Play Agent 10" (or "You are Agent 10" within the clearly identified 10IGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGE10TS.md`, `docs/agent-backlog.md`, `night-play/DESIG10.md`, merged current code, and **GitHub issue #459 including its latest comments**. Do not recite plans: inspect, implement, test and submit work. If returning from a previous agent chat, this WAVE 2 scope has priority.

**Execution:** Start from the *latest* `feature/party-online-rooms-20261019`, not your old first-wave branch; create a short-lived `agent/play-v2-1010-topic`, inspect in-flight PRs to avoid duplicate changes, post [PLAY A#][CLAIM] on issue #459. For shared file edits obtain Agent 1's acknowledgement. Work `inspect → reproduce → code → Chromium real touch/keyboard play → screenshot/perf → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, score, collision, touch feel and appropriate difficulty; CI green alone is not done. Record issue messages and branches; no promise of autonomous work after session ends.

## Major Wave-2 implementation work

Build and execute inventory-driven E2E checks for **every currently linked playable game**, not a sample. Real Chromium CDP touch gestures/keyboard, win/loss/restart, difficulty over minutes, scoring and collision, persistence, responsive layouts and frame time. Independently play browser flows and inspect real screenshots; flag missing routes and visually bad assets. Test ≥5 viewports, multiple inputs, keyboard focus and at least 2 independent remote clients for multiplayer when backend is approved. Use instrumentation logs to pinpoint source, report reproducible game-specific issues, verify repairs. Reject fabricated "done" from green unit/build checks.

## Required real acceptance evidence

Evidence: per-game QA matrix [pass/fail/blocked] with route, commit, device/browser/viewport, real inputs, screenshot/recording path, observed score/endgame and FPS notes. A1 gets explicit release gate decision. Physical Android access must be called out as unavailable rather than pretended.

## Work with other agents

Own `night-play/qa/`, QA reports/workflows. Return each bug to agent 2–9 and retest merged integrated head, not isolated PR only.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A10][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
