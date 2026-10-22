# ON "CONTINUE" — SUPPORT INTEGRATION, DON'T RESTART

Resume your existing Wave 2 branch/PR first; use `night-play/docs/integration-resume.md`, latest merged canonical branch and Issue #459. Check review/CI, repair actual failures, rebase without discarding sibling changes and send Agent 1 a precise READY/HANDOFF comment including tests. **No new isolated feature branches while your existing role PR is unmerged** unless Agent 1 explicitly approves a separate bugfix. Agent 10 instead runs independent QA on actual integrated SHA and returns defects to owners. No claims of background agents.

---

# NIGHT Play — Agent 10: Independent full-catalog long-session gameplay QA

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 10". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are NIGHT Play Agent 10" (or "You are Agent 10" within the clearly identified NIGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGENTS.md`, `docs/agent-backlog.md`, `night-play/DESIGN.md`, current merged game code, and **GitHub issue #459 including latest comments**. Do not just recite a plan: implement and test. If returning from a previous agent chat, WAVE 2 has priority.

**Execution:** Start from current `feature/party-online-rooms-20261019`, not your old first-wave branch; create `agent/play-v2-10-qa`, inspect open PRs and post [PLAY A10][CLAIM] on issue #459. For shared files receive Agent 1 approval. Work `inspect → reproduce → code → Chromium touch/keyboard play → screenshot/performance → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, scoring, collision and real touch feel; passing CI alone is not done. Record exact work, blockers and next action. No promises of autonomous work after session ends.

## Major Wave-2 implementation work

Build and execute inventory-driven E2E checks for **every currently linked playable game**, not a sample. Real Chromium CDP touch gestures/keyboard, win/loss/restart, difficulty over minutes, scoring and collision, persistence, responsive layouts and frame time. Independently play browser flows and inspect real screenshots; flag missing routes and visually bad assets. Test ≥5 viewports, multiple inputs, keyboard focus and at least 2 independent remote clients for multiplayer when backend is approved. Use instrumentation logs to pinpoint source, report reproducible game-specific issues, verify repairs. Reject fabricated "done" from green unit/build checks.

## Required real acceptance evidence

Evidence: per-game QA matrix [pass/fail/blocked] with route, commit, device/browser/viewport, real inputs, screenshot/recording path, observed score/endgame and FPS notes. A1 gets explicit release gate decision. Physical Android access must be called out as unavailable rather than pretended.

## Work with other agents

Own `night-play/qa/`, QA reports/workflows. Return each bug to agent 2–9 and retest merged integrated head, not isolated PR only.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A10][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
