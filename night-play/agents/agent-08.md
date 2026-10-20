# NIGHT Play — Agent 8: Real multiplayer, identity and co-op Jigsaw

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 8". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are 8IGHT Play Agent 8" (or "You are Agent 8" within the clearly identified 8IGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGE8TS.md`, `docs/agent-backlog.md`, `night-play/DESIG8.md`, merged current code, and **GitHub issue #459 including its latest comments**. Do not recite plans: inspect, implement, test and submit work. If returning from a previous agent chat, this WAVE 2 scope has priority.

**Execution:** Start from the *latest* `feature/party-online-rooms-20261019`, not your old first-wave branch; create a short-lived `agent/play-v2-88-topic`, inspect in-flight PRs to avoid duplicate changes, post [PLAY A#][CLAIM] on issue #459. For shared file edits obtain Agent 1's acknowledgement. Work `inspect → reproduce → code → Chromium real touch/keyboard play → screenshot/perf → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, score, collision, touch feel and appropriate difficulty; CI green alone is not done. Record issue messages and branches; no promise of autonomous work after session ends.

## Major Wave-2 implementation work

Do not call existing party-room frontend production-ready. Verify actual MSCC server-side signed RPC, account linking, durable room ownership and rejoin, cross-device hosting/joining, five-round voting and scored quizzes, separate guests and host. Correct WhatsApp initial identity followed by website rename so server/history/leaderboards use updated authoritative name. Finish true remote TTT/Checkers/Chess adapters where approved and cooperative Jigsaw with A5 model API; verify conflict resolution, concurrent moves, disconnects and replay. Do not infer server is deployed because two local browser contexts passed. Request authorization before protected backend release.

## Required real acceptance evidence

Evidence: host plus ≥2 real independent clients with authenticated backend, intentional disconnect/timeout/reconnect, synchronized votes/score/name update, room cleanup and privacy/security tests; record environment, backend commit and screenshot proof. If backend still undeployed, truthfully mark release BLOCKED.

## Work with other agents

Cooperate with A5 Jigsaw state and A6 board rules, A1 preview/back-end deployment approvals. Sensitive bot backend remains protected.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A8][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
