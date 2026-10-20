# AGENTS.md — NIGHT Play web engineering team

These instructions apply to `night-play/` and all subdirectories. They are a work agreement for coding agents, **not an always-running execution service**.

## Mission and entry point

Develop the **actual NIGHT Play website** in `Tomex777/Build/night-play/`. Do not create a disconnected demo, a fake game directory, a separate Android app, or a mock site. Last inspected integration branch is `feature/party-online-rooms-20261019` (PR #386), and combined preview is `https://night-play-combined-preview.vercel.app/`. Before work, inspect the current branch, site routes and open PRs because HEAD may change. `main` is **not** the up-to-date NIGHT Play site branch.

Read `night-play/DESIGN.md`, relevant `night-play/*/DESIGN*.md`, existing engines, self-tests, and the issue assigned to you. The locked Channels design and NIGHT brand mark are not to be redesigned.

## Current assignments: 10-role roster (20 October 2026)

**The root `AGENTS.md` and `docs/agent-backlog.md` are authoritative for the CURRENT ten-agent numbering.** The original nine-agent issues #391–#399 remain valid historical tickets; their **issue titles do not reflect this new roster**. A role is not RUNNING until an actual independent task starts. Creating a ticket is not an agent launch.

| Agent | Specialty | Primary issue / owned path | Shared-file lock |
| --- | --- | --- | --- |
| **1** Lead Coordinator | Merge gate / catalog / release / backlog | #399; `night-play/index.html`, shared shell + CI, root instructions/ledger | Coordinates all shared files |
| **2** Fruit Slicing | Higher arcs, actual swipe, two visible physics halves | #391 / PR #401; fruit section of `action/games.js` | Exclusive action monolith window |
| **3** Runners | Sky Jumper, Endless Runner, distinct Doodle-style | #402; runner/jump section of `action/games.js` | Exclusive action monolith window |
| **4** Arcade | Brick Breaker, shooter, Flappy | #403; brick/shooter action and Wing Hop arcade | Lock with Agents 2/3, 6/7 |
| **5** Puzzles | Jigsaw, Memory Match, 2048 | #404; `puzzles/`, 2048; Jigsaw game model after agreement | Jigsaw lock with Agent 8 |
| **6** Board & Card | WHOT, Checkers, Chess CPU/rules | #397/#396; `club/` / `strategy/` | Coordinate Chess transport with Agent 8 |
| **7** Additional games | Piano Tiles, Pac-Man and unique casual games | #392–#395; `piano-tiles/`, `classics/`, `brain/`, `solitaire/` | Pac-Man edits coordinate with Agent 4 |
| **8** NIGHT + NOMI | Real rooms, host links, identity, votes, reconnection, co-op | #405/#398; `party/room/`, `multiplayer/`, approved adapters | Backend protected; sync interface with Agents 5/6 |
| **9** UI/UX | SVG, responsive site audit, consistency | #406; shared CSS / agreed shared components | Coordinator approval first |
| **10** Independent QA | Real cross-viewport gameplay and 2-client verification | #407; `qa/`, test + screenshot scripts | Send defect to owner, don't overwrite engine |

**Action-engine concurrency:** Fruit, Jump, Runner, Bricks, and Shooter currently coexist in `night-play/action/games.js`. Agents 2–4 **must not edit this one file concurrently**. They can work in parallel on independent scripts/tests, unshared modules, rules, and reviews, then acquire the file lock one at a time. Keep real Git worktrees and separate branches; no ghost parallel agents.

Specialists must not independently edit `night-play/index.html`, `night-play/game-shell.css`, or `night-play/vercel.json` merely to add catalog tiles. Provide working routes, tested modes and original SVG thumbnails; Agent 1 integrates only after gameplay/visual review. Follow existing approved Channels visuals and never reintroduce one-phone "online" modes.

## Continuous-work execution contract

- **Act, don't merely describe what should be done.** Read actual source, change code, launch tests, inspect the resulting game, fix failures, repeat.
- Follow **inspect → implement → play → screenshot → diagnose → fix → replay → CI → PR**. A successful build, passing syntax check, deployment submission or one screenshot is **not** completion.
- If an operation is running, poll/check it to an actual terminal outcome within reasonable bounds. When waiting, advance independent work instead of issuing idle status messages. Avoid endless polling; record external blockers precisely.
- If you encounter a bug, reproduce it, trace its cause, repair it and test the correction. Do not replace a hard gameplay feature with a no-op, disabled collision, fake score, or simplified placeholder to make checks pass.
- If a subtask passes, immediately begin the next scoped subtask until every assigned acceptance criterion is satisfied or a genuine execution/approval limit is reached.
- Session limits are real: before terminating, commit working changes and leave an accurate state note on your issue: branch, commit, paths, tests, failed tests, blockers, and **the next command/action**. Never claim to continue running after the session actually ends.
- Coordinator (Agent 1) reviews completed specialist PRs, assigns the next ready task, keeps issues accurate and checks preview deployments. **No automated delegation is presumed**: a human or configured runner must actually launch/resume each coding session.

## Mandatory website and visual rules

- Apply NIGHT website building rules: minimal flat, polished design; appropriate purposeful color; proper reusable SVG icons/art; no generic AI-generated look, redundant actions, pointless labels, decorative navigation arrows, clashing controls or one-phone substitutes for online play.
- Use the established NIGHT logo and game screen systems. Respect all approved existing functionality and frozen Channels design.
- The game must really be *playable*: game physics, hitboxes, win/lose conditions, scoring, restart, pause, saved progress where relevant, and ergonomic mobile controls. Test challenge/difficulty beyond a few easy seconds. No known auto-win bugs or invulnerable hazards.
- Verify swipe/drag follows the finger; no full-canvas performance spikes on every pointer event if avoidable; respect reduced motion. Test keyboard/mouse **and touch**.
- No general background game music; sound effects only. **Exceptions:** Piano Tiles' notes/melodies are its core mechanics; Jigsaw may offer optional quiet soothing music. Do not add redundant volume sliders.
- Use original, properly licensed, or already-approved assets; never silently hotlink unlicensed art.
- Honest identity, multiplayer, and data: WhatsApp linked name starts as canonical identity, web changes propagate to true game data; do not fabricate global ranks or linked account status. Real online multiplayer must involve separate remote devices/contexts and shared verified state; pass-and-play is **not** online.
- For Jigsaw: default 24, medium 48, maximum 144; large thin-bordered board; scatter pieces around it, avoid permanent answer ghosts and bottom trays; bounded hints and performant drag.
- Traditional Nigerian WHOT rules/card forms and non-laggy short motion; never hide gameplay defects with superficial labels. Ludo remains hidden until the requested exact Naija board and dice are achieved.

## Proof of completion

For each game, include (1) direct playable route, (2) source and commit, (3) real browser interaction tests with key screenshots/recording frames, (4) viewport results at least 320, 360, 390, 412 and desktop, (5) playthrough of winning, losing, restart and edge cases, (6) performance assessment on a mobile-appropriate setup, (7) green relevant GitHub Actions run, (8) any honestly documented limitations. Use and extend the existing `night-play/action/*smoke*.mjs`, `night-play/action/real-play-corrections.mjs`, `night-play/arcade/mobile-smoke.mjs`, `night-play/club/mobile-smoke.mjs`, `night-play/jigsaw/mobile-smoke.mjs`, etc.

The critical combined workflow is `.github/workflows/night-play-preview-ci.yml`; key commands include `node night-play/selftest.mjs` and the affected real-Chromium gameplay scripts. Browser screenshots in CI are review evidence, not a substitute for checking them. Record unexpected failures even if unrelated.

Do not merge unreviewed work to a protected or published branch and do not deploy backend changes/real multiplayer claims without an end-to-end two-client test. Promote only when authorized and verified. A published URL must refer to the new tested commit, not an older preview.

## Current Fruit Slice / runner / bricks caution (19 October 2026)

The active combined-preview branch includes source claiming two moving fruit halves, juice particles, low-flying runner collision, and per-brick impact tests; Preview CI ran successfully for commit `57e0b09aa213b15b1ca83666d5cde0002c525364`. This establishes **test coverage**, not that the player's visual complaint has been resolved. Agent 2 and independent Agent 10 must inspect real rendered gameplay rather than mark this issue completed from the existing tests alone.

## Coordination and status

See `docs/agent-backlog.md`, `night-play/AGENT-BOARD.md` and the current roster issues #402–#407 alongside historical #391–#399. Status language:
- `QUEUED`: issue documented, no running agent proven.
- `ACTIVE`: a real Codex/agent session was launched and is executing.
- `BLOCKED`: exact external dependency/action needed is recorded.
- `REVIEW`: implementation PR exists and evidence supplied.
- `DONE`: verified/merged/tested and preview verified (if relevant).

Never mark something `RUNNING` just because it has an issue; never mark `DONE` because it has a thumbnail or source file.
