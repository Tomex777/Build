# NIGHT Play — Agent 9: Product-wide visual overhaul and accessibility

**Repository:** `Tomex777/Build`, source `night-play/`. **Integration:** `feature/party-online-rooms-20261019`. **Chat route:** "You are NIGHT Play Agent 9". **Live collaboration:** [GitHub issue #459](https://github.com/Tomex777/Build/issues/459).

**WAVE 2 (2026-10-20):** This brief replaces the *first task's priority*, not previously approved ABR/design or unfinished technical requirements. PR **#436 was merged** into canonical `feature/party-online-rooms-20261019`; the first ten-agent code set is combined and the Play Preview + Ten-Agent Integration Gameplay CI passed. **Do not create duplicates of completed games.** Snake swipe bug is open in PR #435. Online-party backend must be independently verified; combined preview is not proof of production multiplayer.

**Activation:** User says "You are NIGHT Play Agent 9" (or "You are Agent 9" within the clearly identified NIGHT Play chat). Read this brief and `night-play/agents/README.md`, `night-play/AGENTS.md`, `docs/agent-backlog.md`, `night-play/DESIGN.md`, current merged game code, and **GitHub issue #459 including latest comments**. Do not just recite a plan: implement and test. If returning from a previous agent chat, WAVE 2 has priority.

**Execution:** Start from current `feature/party-online-rooms-20261019`, not your old first-wave branch; create `agent/play-v2-09-ux`, inspect open PRs and post [PLAY A9][CLAIM] on issue #459. For shared files receive Agent 1 approval. Work `inspect → reproduce → code → Chromium touch/keyboard play → screenshot/performance → fix → retest → PR → next task`. PRs target canonical integration, not main. Demonstrate win/loss/restart, scoring, collision and real touch feel; passing CI alone is not done. Record exact work, blockers and next action. No promises of autonomous work after session ends.

## Major Wave-2 implementation work

Conduct serious full-site UI audit, not superficial recoloring. Keep approved Landing/Home and Channels identity, NIGHT logo and core style; improve game cards/thumbnails with original SVG, cohesive navigation, usable settings, correct fullscreen mobile safe areas, touch-sized controls, focus rings, readable text, smooth tab transitions and no redundant Home/reload/icon arrows. Audit all routes including Games, Profile, Updates, party rooms, stories and actual game screens; disable/repair empty links. Design at least two deliberate before/after flows reviewed with screenshot evidence. Respect user ABR/EBR: flat refined visuals without generic template look; no filler, no unapproved music or redundant sliders.

## Required real acceptance evidence

Evidence: before/after screenshots across 320/360/390/412 and desktop, tested navigation/tab/keyboard/touch, accessibility checks, no horizontal overflow and no regression of locked Channels layouts.

## Work with other agents

A1 owns shared CSS/menu/index files and gate. Coordinate each site shell patch; engine owners keep game logic.

**First executable action:** Inspect current merged game code and existing tests, read issue #459 latest comments, post [PLAY A9][CLAIM] and create a branch from current integration. Implement the highest-impact unblocked defect, run it and play it. Create PR with evidence. When done claim the next task, not just a new status report.
