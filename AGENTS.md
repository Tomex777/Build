# NIGHT Play Wave 2 — current routing (20 October 2026)

If the current user message says **"You are NIGHT Play Agent N"**, **"You are Agent N"** in NIGHT Play context, or **"Continue"** to an existing NIGHT Play agent, immediately read `night-play/agents/README.md`, `night-play/agents/agent-NN.md`, `night-play/AGENTS.md`, `night-play/docs/wave2-quality-gate.md`, `docs/agent-backlog.md`, the latest `feature/party-online-rooms-20261019` source, and **GitHub issue #459 latest comments**. Source is already integrated via PR #436; work on improvements and real QA, not repeat the first wave. Shared source locks and truthful session limits still apply. Other projects' own AGENTS files remain separate.

---

# AGENTS.md — NIGHT Play coordination (20 October 2026)

> Scope: NIGHT Play website in `night-play/` within this **multi-project** repository. These rules do not replace other applications' project-specific instructions. Read `night-play/AGENTS.md`, `night-play/DESIGN.md`, applicable component design locks, and `docs/agent-backlog.md` **before touching code**. The 10-role assignment below supersedes the historical 9-agent numbering in `night-play/AGENTS.md` and `night-play/AGENT-BOARD.md`; retain their valid engineering and design requirements.

## Real website, references and release safety

- Repository: `Tomex777/Build`; website root: `night-play/`, not repository `main` (which is currently a stale site source). Inspect actual Git refs and PRs before choosing a base.
- Known integration branch on 2026-10-20: `feature/party-online-rooms-20261019`, PR #386. Known deployed combined-preview alias: `https://night-play-combined-preview.vercel.app/`; confirm **which commit** the alias currently serves before claiming any feature is live. The observed preview was `57e0b09aa213b15b1ca83666d5cde0002c525364`; the unmerged fruit-trajectory PR #401 contains `7887b7360fddb8e93dbc830d5e5789ba2364b42b`.
- Keep the existing `night-play/AGENTS.md` game, artwork, performance, identity and Channels design-lock constraints. Preserve NIGHT logo, approved navigation and established website style. Flat mobile-first UI, purposeful SVGs, no placeholder/demo games, duplicate controls, decorative arrows, invented social proofs or generic AI-looking design. SFX only except real Piano Tiles notes and optional quiet Jigsaw music.
- No unapproved production deployments or direct merges into the protected MSCC bot branch. PR #387 is the separate durable online-room backend; its mere existence does **not** make cross-device NOMI integration live.

## Ten real responsibilities

| Agent | Scope | Exclusive source ownership or integration contract |
| --- | --- | --- |
| 1 — Lead Coordinator | Actual repository audit, backlog, delegation, dependency locks, review, integration, combined preview, reassignment | `AGENTS.md`, `docs/agent-backlog.md`, `night-play/AGENT-BOARD.md`, shared preview CI and website registry after review |
| 2 — Fruit Slicing | Arc height; actual swipe intersection/scoring; visible cut halves, juice/physics; mobile responsiveness | Fruit-specific logic inside `night-play/action/games.js` **with explicit shared-file lock**; PR #401 already open, issue #391 |
| 3 — Runners / Platform | Sky Jumper, Endless Runner and only distinct Doodle-style gameplay | Existing action runner/jump code **with explicit shared-file lock**; issue #402 |
| 4 — Arcade | Brick Breaker, Space Shooter, Flappy / Wing Hop | Action brick/shooter logic **with explicit shared-file lock**; Wing Hop under `arcade/` only after coordination with Agent 6; issue #403 |
| 5 — Puzzles | Jigsaw, Memory Match, 2048 | `puzzles/`, 2048 engine; Jigsaw core only with lock against Agent 8; issue #404 |
| 6 — Board & Cards | WHOT, Chess, Checkers legal rules, smooth animations, CPU matches | `club/`, `strategy/`, shared game rules; coordinate online transport with Agent 8; original issue #397 and #396 |
| 7 — Additional Games | Piano Tiles, Pac-Man and unique approved playable casual games | New `piano-tiles/`, `classics/`, `brain/`, `solitaire/`; Pac-Man's existing `arcade/` gameplay only after lock against Agent 4/6; issues #392–#395 |
| 8 — NIGHT + NOMI Online | Server-owned room state, WhatsApp-linked identity propagation, remote invites, quiz/votes/reconnect, online matches and co-op Jigsaw | `party/room/`, `multiplayer/`, network adapters; mutually agreed Jigsaw and Chess interfaces; issues #405/#398; PRs #386/#387 |
| 9 — UI/UX | Shared component visual audit, SVGs, mobile layouts, no duplicate arrows/controls | Shared CSS/catalog only after coordinator approval, no unauthorized engine edits; issue #406 |
| 10 — Independent Gameplay QA | Real browser touch/keyboard play, game-over/restart, screen captures, perf, two-client multiplayer | `night-play/qa/`, test/evidence files; report defects to owning agents; issue #407 |

## Worktrees, concurrency and file locks

1. **Actual launched sessions only.** An issue, table row, empty branch, or scheduled check is not a running agent. Do not impersonate other agents. The coordinator can allocate work, but an actual task runner must launch independent sessions.
2. Use a distinct `git worktree` and `agentN/... ` branch for each session, based on the latest compatible integration head. NEVER make several agents edit a monolithic file simultaneously. Action games currently share `night-play/action/games.js`; stagger edits to that file or first make tested non-conflicting module extractions. Coordinate `arcade/`, `club/` and `jigsaw/` access likewise.
3. Own changes only in assigned files; use a PR for integration. Shared navigation, global CSS, and Vercel config belong to the coordinator/UI slot. Code owners propose catalogue additions with a direct working route, proper local SVG thumbnail and tested module; they do not add a fake card themselves.
4. Progress through **INSPECT → IDENTIFY → IMPLEMENT → EXECUTE → PLAYTEST → EVALUATE → IMPROVE → VERIFY**. If blocked, advance an unrelated executable task. Do not claim work continues after the session ends.
5. On any session exit, commit partial working code and update the corresponding issue plus `docs/agent-backlog.md` (or make a coordinator PR update): current branch, commit SHA, owner, status, tests and screenshot artifact IDs, blocking dependencies and the next exact action.

## Definition of DONE and integration

DONE requires working source, relevant syntax/unit tests, real Chromium gameplay with actual mouse/touch/keyboard inputs, scoring/hazards/win/lose/restart and resume as applicable, screenshots inspected at 320/360/390/412px + desktop, mobile performance review, passing applicable Preview CI and confirmed combined preview at the exact merged/deployed SHA. Validate any online feature using **two independent sessions communicating with the real server**, including reconnection and host/guest permissions. Test failures must be reproduced, corrected and replayed. A successful CI run alone is insufficient. The QA agent independently reviews before coordinator approval.

Coordinator integrates non-conflicting PRs in dependency order, reviews diffs and gameplay evidence, runs `node night-play/selftest.mjs`, `.github/workflows/night-play-preview-ci.yml` and affected browser suites, verifies deployments, then updates ledger. Never declare incomplete production integration as finished. Existing functioning games and approved Channels page must not regress.

## Interrupted-session recovery

1. Read this file, `night-play/AGENTS.md`, `night-play/DESIGN.md`, `night-play/AGENT-BOARD.md`, `docs/agent-backlog.md` and the assigned GitHub issue.
2. Query current branches, PRs, commit SHAs, CI/deployment outcome and file ownership. Do not assume earlier progress was merged.
3. Claim exactly one unlocked scope; if occupied, work on independent tests/modules or choose another task.
4. Execute the next-action command from the ledger, run gameplay and record new evidence. Keep status `QUEUED`, `ACTIVE`, `BLOCKED`, `REVIEW`, or `DONE` truthful.
