# NIGHT Play — Agent 4: Brick Breaker, Shooter and Wing Hop depth

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 4". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are 4IGHT Play Agent 4" (or "You are Agent 4" within the clearly identified 4IGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGE4TS.md`, `docs/agent-backlog.md`, `night-play/DESIG4.md`, merged current code, and **GitHub issue #459 including its latest comments**. Do not recite plans: inspect, implement, test and submit work. If returning from a previous agent chat, this WAVE 2 scope has priority.

**Execution:** Start from the *latest* `feature/party-online-rooms-20261019`, not your old first-wave branch; create a short-lived `agent/play-v2-44-topic`, inspect in-flight PRs to avoid duplicate changes, post [PLAY A#][CLAIM] on issue #459. For shared file edits obtain Agent 1's acknowledgement. Work `inspect → reproduce → code → Chromium real touch/keyboard play → screenshot/perf → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, score, collision, touch feel and appropriate difficulty; CI green alone is not done. Record issue messages and branches; no promise of autonomous work after session ends.

## Major Wave-2 implementation work

Make the games satisfying over long sessions, not trivial first levels. Brick Breaker: paddle/spin reflection, complete stages and increasing challenge, fair collision, no automatic easy win, visually pleasing effects, fail/lives/restart. Space Shooter: believable hazards, enemy AI/patterns, collisions, pickups or progression as appropriate, responsive fire and drag, meaningful score and real death. Wing Hop/Flappy: responsive tap physics, asset quality, fair obstacle gaps and escalating speed. Avoid art-only upgrades without playable difficulty.

## Required real acceptance evidence

Evidence: full level/boss or staged sessions, expected misses/collisions, long-session FPS and gameplay recordings, true win/loss/restart for all assigned games and 320/360/390/412px devices.

## Work with other agents

Only touch action monolith with A1 lock (A2/A3); coordinate arcade file with A7 and A9.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A4][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
