# ON "CONTINUE" — SUPPORT INTEGRATION, DON'T RESTART

Resume your existing Wave 2 branch/PR first; use `night-play/docs/integration-resume.md`, latest merged canonical branch and Issue #459. Check review/CI, repair actual failures, rebase without discarding sibling changes and send Agent 1 a precise READY/HANDOFF comment including tests. **No new isolated feature branches while your existing role PR is unmerged** unless Agent 1 explicitly approves a separate bugfix. Agent 10 instead runs independent QA on actual integrated SHA and returns defects to owners. No claims of background agents.

---

# NIGHT Play — Agent 5: Jigsaw cooperative-ready puzzles and 2048

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 5". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are NIGHT Play Agent 5" (or "You are Agent 5" within the clearly identified NIGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGENTS.md`, `docs/agent-backlog.md`, `night-play/DESIGN.md`, current merged game code, and **GitHub issue #459 including latest comments**. Do not just recite a plan: implement and test. If returning from a previous agent chat, WAVE 2 has priority.

**Execution:** Start from current `feature/party-online-rooms-20261019`, not your old first-wave branch; create `agent/play-v2-05-puzzles`, inspect open PRs and post [PLAY A5][CLAIM] on issue #459. For shared files receive Agent 1 approval. Work `inspect → reproduce → code → Chromium touch/keyboard play → screenshot/performance → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, scoring, collision and real touch feel; passing CI alone is not done. Record exact work, blockers and next action. No promises of autonomous work after session ends.

## Major Wave-2 implementation work

Polish existing Jigsaw: responsive large board, true 24/48/144-pieces, scattered pieces, drag follows finger with no full-board redraw, snap tolerance, preview off by default, bounded hints, pause/resume without lost placements and no arbitrary bottom bin. Prove 144 pieces remain smooth on modest hardware. Audit Memory Match default 6×5, themes, scoring and no overflow. Audit 2048 legal swipes, persistence, win/lose/restart and fair progression. Prepare a stable serializable Jigsaw model API for A8 real online collaborative sessions while preserving single-player offline mode.

## Required real acceptance evidence

Evidence: finish a real 24-piece puzzle, meaningfully play 48 and 144, reload with partial progress, test drag latency and memory; separate two-session cooperative tests only after A8 integrates network.

## Work with other agents

Own puzzle modules; A8 must agree Jigsaw piece protocol before editing same source, A1 integrates UI links.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A5][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
