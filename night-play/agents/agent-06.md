# NIGHT Play — Agent 6: WHOT, Chess and Checkers: credible opponents

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 6". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are NIGHT Play Agent 6" (or "You are Agent 6" within the clearly identified NIGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGENTS.md`, `docs/agent-backlog.md`, `night-play/DESIGN.md`, current merged game code, and **GitHub issue #459 including latest comments**. Do not just recite a plan: implement and test. If returning from a previous agent chat, WAVE 2 has priority.

**Execution:** Start from current `feature/party-online-rooms-20261019`, not your old first-wave branch; create `agent/play-v2-06-cards-board`, inspect open PRs and post [PLAY A6][CLAIM] on issue #459. For shared files receive Agent 1 approval. Work `inspect → reproduce → code → Chromium touch/keyboard play → screenshot/performance → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, scoring, collision and real touch feel; passing CI alone is not done. Record exact work, blockers and next action. No promises of autonomous work after session ends.

## Major Wave-2 implementation work

Play genuine full WHOT rounds against AI: eliminate sluggish card motion, CPU fan overflow, inaccurate hand counts, illegal draws such as "pick 20", wrong winner displays or missing game over. Maintain Nigerian/traditional WHOT rules. For Chess, verify all legal cases (promotion, castling, en-passant, checks/draws, save/resume) and meaningful AI; distinguish offline bot/local from truly online remote. Checkers: legal capture chains/kings, smooth ~300 ms movement, bot difficulty, online protocol interface for A8. Do not label same-phone pass-and-play online.

## Required real acceptance evidence

Evidence: record complete WHOT and Chess/Checkers matches (including forced captures, special chess rules), score and winner, restart, animation frame timings and no stalling on mobile.

## Work with other agents

Own `club/` and `strategy/`; A8 owns online transport, not chess legality; A1 owns route integration.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A6][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
