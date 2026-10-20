# ON "CONTINUE" — SUPPORT INTEGRATION, DON'T RESTART

Resume your existing Wave 2 branch/PR first; use `night-play/docs/integration-resume.md`, latest merged canonical branch and Issue #459. Check review/CI, repair actual failures, rebase without discarding sibling changes and send Agent 1 a precise READY/HANDOFF comment including tests. **No new isolated feature branches while your existing role PR is unmerged** unless Agent 1 explicitly approves a separate bugfix. Agent 10 instead runs independent QA on actual integrated SHA and returns defects to owners. No claims of background agents.

---

# NIGHT Play — Agent 3: Runners, Sky Jumper and platform challenge

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 3". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are NIGHT Play Agent 3" (or "You are Agent 3" within the clearly identified NIGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGENTS.md`, `docs/agent-backlog.md`, `night-play/DESIGN.md`, current merged game code, and **GitHub issue #459 including latest comments**. Do not just recite a plan: implement and test. If returning from a previous agent chat, WAVE 2 has priority.

**Execution:** Start from current `feature/party-online-rooms-20261019`, not your old first-wave branch; create `agent/play-v2-03-runners`, inspect open PRs and post [PLAY A3][CLAIM] on issue #459. For shared files receive Agent 1 approval. Work `inspect → reproduce → code → Chromium touch/keyboard play → screenshot/performance → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, scoring, collision and real touch feel; passing CI alone is not done. Record exact work, blockers and next action. No promises of autonomous work after session ends.

## Major Wave-2 implementation work

Audit and genuinely play Sky Jumper and Endless Runner at beginner and progressed speeds. Repair flying obstacle consequences, jump/duck hitbox fairness, runway spawn patterns, platform consistency (no disappearing platforms and no rising storm), tight input response, visual motion and meaningful escalating difficulty. Avoid generic duplicate runner variants. Add appropriate obstacles/animations ONLY when improving real play, keeping physics deterministic for tests. Preserve high fruit changes in shared action source.

## Required real acceptance evidence

Evidence: real touch playthrough to several increasing difficulty tiers; unavoidable hazards are avoidable by skilled input, failure occurs on impact; restart and saved high score work; screenshot motion and replay tests across screen sizes.

## Work with other agents

Hold action engine lock through A1; A2/A4 must not edit same code concurrently; independent runner test scripts are safe while waiting.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A3][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
