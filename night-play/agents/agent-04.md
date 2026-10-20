# NIGHT Play — Agent 4: Brick Breaker, Shooter and Wing Hop depth

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 4". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are NIGHT Play Agent 4" (or "You are Agent 4" within the clearly identified NIGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGENTS.md`, `docs/agent-backlog.md`, `night-play/DESIGN.md`, current merged game code, and **GitHub issue #459 including latest comments**. Do not just recite a plan: implement and test. If returning from a previous agent chat, WAVE 2 has priority.

**Execution:** Start from current `feature/party-online-rooms-20261019`, not your old first-wave branch; create `agent/play-v2-04-arcade`, inspect open PRs and post [PLAY A4][CLAIM] on issue #459. For shared files receive Agent 1 approval. Work `inspect → reproduce → code → Chromium touch/keyboard play → screenshot/performance → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, scoring, collision and real touch feel; passing CI alone is not done. Record exact work, blockers and next action. No promises of autonomous work after session ends.

## Major Wave-2 implementation work

Make the games satisfying over long sessions, not trivial first levels. Brick Breaker: paddle/spin reflection, complete stages and increasing challenge, fair collision, no automatic easy win, visually pleasing effects, fail/lives/restart. Space Shooter: believable hazards, enemy AI/patterns, collisions, pickups or progression as appropriate, responsive fire and drag, meaningful score and real death. Wing Hop/Flappy: responsive tap physics, asset quality, fair obstacle gaps and escalating speed. Avoid art-only upgrades without playable difficulty.

## Required real acceptance evidence

Evidence: full level/boss or staged sessions, expected misses/collisions, long-session FPS and gameplay recordings, true win/loss/restart for all assigned games and 320/360/390/412px devices.

## Work with other agents

Only touch action monolith with A1 lock (A2/A3); coordinate arcade file with A7 and A9.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A4][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
