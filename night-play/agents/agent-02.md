# NIGHT Play — Agent 2: Fruit Slicing + Snake gesture reliability

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 2". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are NIGHT Play Agent 2" (or "You are Agent 2" within the clearly identified NIGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGENTS.md`, `docs/agent-backlog.md`, `night-play/DESIGN.md`, current merged game code, and **GitHub issue #459 including latest comments**. Do not just recite a plan: implement and test. If returning from a previous agent chat, WAVE 2 has priority.

**Execution:** Start from current `feature/party-online-rooms-20261019`, not your old first-wave branch; create `agent/play-v2-02-touch-arcade`, inspect open PRs and post [PLAY A2][CLAIM] on issue #459. For shared files receive Agent 1 approval. Work `inspect → reproduce → code → Chromium touch/keyboard play → screenshot/performance → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, scoring, collision and real touch feel; passing CI alone is not done. Record exact work, blockers and next action. No promises of autonomous work after session ends.

## Major Wave-2 implementation work

1. Play real Fruit Slice for multiple full rounds, not just one swipe: check high enough apex, reachable fruit, blade contact matching actual swipe, no scoring false positives, genuine two-half cut direction, animations, combos, missed fruit/lives, difficulty and frame pacing. Repair poor feel, performance and broken edge cases rather than cosmetic tweaks.
2. Review PR #435 Snake Xenzia lost swipe: ensure first valid gesture registers (not requiring second slide) even after consecutive fast input, with correct queued turns and no 180° reversal; personally drive touchscreen at real board speed.
3. Unify reliable pointer/touch handling patterns when justified without changing other game logic behind their owners. No free score / fake slicing.
4. Add long-session tests (≥3 minutes), multi-gesture input and 320–412px screen evidence.

## Required real acceptance evidence

Evidence: recording/screenshots and tests for slices/misses/combo/game over/restart, wrong-swipe rejection and Snake 4 rapid strokes before a grid tick. No stutter or lost deliberate strokes at mobile sizes, no multiple reload controls.

## Work with other agents

Coordinate a short exclusive edit window for `night-play/action/games.js` with A1/A3/A4; Snake is separately owned and must not accidentally change unreleased logic.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A2][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
