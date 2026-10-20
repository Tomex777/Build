# NIGHT Play — Agent 7: Piano Tiles, Maze Chase and catalogue quality

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 7". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are 7IGHT Play Agent 7" (or "You are Agent 7" within the clearly identified 7IGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGE7TS.md`, `docs/agent-backlog.md`, `night-play/DESIG7.md`, merged current code, and **GitHub issue #459 including its latest comments**. Do not recite plans: inspect, implement, test and submit work. If returning from a previous agent chat, this WAVE 2 scope has priority.

**Execution:** Start from the *latest* `feature/party-online-rooms-20261019`, not your old first-wave branch; create a short-lived `agent/play-v2-77-topic`, inspect in-flight PRs to avoid duplicate changes, post [PLAY A#][CLAIM] on issue #459. For shared file edits obtain Agent 1's acknowledgement. Work `inspect → reproduce → code → Chromium real touch/keyboard play → screenshot/perf → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, score, collision, touch feel and appropriate difficulty; CI green alone is not done. Record issue messages and branches; no promise of autonomous work after session ends.

## Major Wave-2 implementation work

Polish real playable Piano Tiles with large portrait four-lane falling tiles, actual note timing/melody (not decorative background audio), long notes/holds, misses, tempo progression and good phone touch latency; no tiny tiles/lines. Improve Maze Chase/Pac-Man rules: ghost logic, power pellets, wrap tunnels, lives, level progression and pixel-perfect control. After existing games are genuinely polished, implement one **distinct** approved game with complete rules/skills, original assets, save/win/lose/restart; no empty catalog tile or reskinned duplicate. Test every added gameplay route deeply.

## Required real acceptance evidence

Evidence: play two full Piano Tiles tracks including holds/late/early taps, complete Maze Chase level and demonstrate ghost chase/death/pellets, and full loop of any newly added game, with screenshot + actual touch logs.

## Work with other agents

Coordinate arcade shared files with A4; avoid global catalog edits without A1.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A7][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
