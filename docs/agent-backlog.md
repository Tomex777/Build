# NIGHT Play — durable multi-agent task ledger

**Recorded:** 2026-10-20, Agent 1 coordinator. **Status is factual, not a promise of agents running.**

## Integration baseline and verified state

- Repository: `Tomex777/Build`; active website integration PR #386 (`feature/party-online-rooms-20261019`, head `7231c4c6d7c62a9601875d26fc492b4eb49eefba` when inspected). Main is **not** the latest web branch.
- Deployed `night-play-combined-preview.vercel.app` resolves to Vercel deployment `dpl_4amnxRwVa1jsrqzHWQ7ijTjs6V1o` with commit `57e0b09aa213b15b1ca83666d5cde0002c525364` (READY); does **not** include PR #401 fruit launch velocities. Do not conflate deployed `READY` with latest source.
- PR #401, `fix/fruit-slice-higher-arcs-20261020`, head `7887b7360fddb8e93dbc830d5e5789ba2364b42b`: increases Fruit Slicing vertical launch speed; `NIGHT Play Preview CI` run `39348384258`, #573 **passed**; visual artifact `12232381826` available for review. **Not merged or visually approved**; test ensures apex high enough, but real separation frames/phone feel must be inspected. Existing action browser test also covers flyer collision and Brick Breaker single-brick impact; user-quality acceptance remains open.
- Frontend online room PR #386 and protected MSCC backend PR #387 both open; NOMI/native online deployment **not yet verified**. PR #382 identity work is not equivalent to a verified live server rename. Release backend only in an authorized protected batch.
- Project already has original `night-play/AGENTS.md`, `DESIGN.md`, `AGENT-BOARD.md`, `selftest.mjs`, game-specific browser tests, `.github/workflows/night-play-preview-ci.yml`. Preserve these.

Statuses: `QUEUED` = no launched session observed; `ACTIVE` = running session actually verified; `REVIEW` = PR submitted but end-to-end gate not passed; `BLOCKED` = documented external prerequisite; `DONE` = integrated + independently played + preview verified.

| Agent / priority | Issue or PR | Status | Branch (assigned, not necessarily created) | Test/evidence state | Blocker / next executable action |
| --- | --- | --- | --- | --- | --- |
| A1 P0 Lead coordination | #399; coordinator PR to follow | **ACTIVE this session** | `coord/night-play-agent1-20261020` | Repo/PRs/CI/deployed SHA inspected; no gameplay personally played yet | Commit root instructions/ledger and QA gate; review #401; coordinate integration without merging unverified code. |
| A2 P0 Fruit Slicing | #391; PR #401 | **REVIEW** | `fix/fruit-slice-higher-arcs-20261020` | Preview CI #573 PASSED; artifact #12232381826 **not yet visually inspected** | Inspect actual swipe/cut before/after frames, 320–412px, check scoring without intersection; replay after corrections; then coordinate merge. |
| A3 P0 Runners / Sky Jumper | #402 | **QUEUED** | proposed `agent3/night-play-runners-20261020` | Existing `action/real-play-corrections.mjs`/ `long-play.mjs` cover some Chromium cases; phone visuals not signed off | Launch distinct agent; reserve exclusive `action/games.js` edit window; test first unavoidable flyer and Sky platform physics. |
| A4 P0 Arcade | #403 | **QUEUED** | proposed `agent4/night-play-arcade-20261020` | Existing `action/mobile-smoke.mjs` + impact tests; game difficulty complaints unresolved | Reserve action file after A2/A3; play Brick Breaker for full stages, tackle shooter/Flappy. |
| A5 P1 Jigsaw / Memory / 2048 | #404 | **QUEUED** | proposed `agent5/night-play-puzzles-20261020` | Existing puzzle/Jigsaw Chromium tests, no independent QA verdict | Launch puzzle session; agree a Jigsaw state interface and lock with A8; test 6x5 defaults, 144-piece performance. |
| A6 P1 WHOT / Chess / Checkers | #397, #396 | **QUEUED** | proposed `agent6/night-play-board-20261020` | Existing `club/mobile-smoke.mjs`; no new results | Play/fix WHOT animation, hand count, draw; implement verified Chess legal moves and Checkers; coordinate online rules adapter with A8. |
| A7 P1 Additional games | #392, #393, #394, #395 | **QUEUED** | proposed `agent7/night-play-additional-20261020` | No verified new completed game in this wave | Start with complete Piano Tiles, then distinct games; inspect existing arcade Pac-Man before duplicating. |
| A8 P0/P1 NIGHT × NOMI | #405, #398; PRs #386/#387 | **BLOCKED for live release**, test work QUEUED | proposed `agent8/night-nomi-20261020` | Existing party-room smoke tests, but production cross-service/two-device verification missing | Arrange protected backend release approval and signed identity bridge, independently exercise host+2 guests, votes/results/reconnect; do not claim remote live until complete. |
| A9 P1 UI/UX | #406 | **QUEUED** | proposed `agent9/night-play-ui-20261020` | Existing visual-inspect artifacts, no whole-site audit verdict | Inspect all mobile routes, preserve locked Channels; send targeted CSS/brand fixes in separate PR. |
| A10 P0 independent QA | #407 | **QUEUED** | proposed `agent10/night-play-qa-20261020` | CI screenshot artifact exists but independent interpretation not done | Launch actual separate QA session; test touch/keyboard, challenge/loss/restart on 5 viewports, two real sessions online; return reproducible issues. |

## File ownership / concurrency locks

- Coordinator: shared `night-play/index.html`, `site-shell.css`, `game-shell.css`, `vercel.json`, preview workflow/QA registration and project instructions.
- **Action monolith lock:** `night-play/action/games.js` is shared across A2–A4. **One active editor at a time**, not three. Use non-overlapping tests, research, or new isolated modules while another agent edits; coordinate cherry-picks/reviews. PR #401 currently owns fruit arc patch.
- Jigsaw source lock: A5 gameplay ↔ A8 network synchronization must agree interface and ownership; never concurrent edit of same file.
- `night-play/arcade/` / `club/`: A4/A6/A7 own per-game sections by explicit coordinator assignment only; avoid concurrent conflicts.
- QA owns tests/evidence, can report bugs but does not silently rewrite another agent's implementation.

## Launch and continuation

**This interface cannot itself guarantee persistent execution of ten independent agents.** Starting the roles requires ten actual separate coding tasks / worktrees and a runner that manages them. Creating issues or branches alone does not constitute launch. Update the ledger to ACTIVE only on proof of a real executing session; no fictitious progress.

Next integration sequence: (1) verify fruit PR #401 visually and in actual gameplay; (2) Runner/Sky + Brick challenge correction; (3) finish/independently verify #386/#387 and NOMI; (4) correct existing puzzles/card games; (5) introduce playable new games; (6) connect routes only after QA; (7) verify combined Vercel SHA, avoid production promotion without authorization.

Each contributor must update: issue, owner, branch, last commit, tests with command and result, captured screenshots/artifacts, unresolved bugs, dependencies, precise next step. Never change status from merely observing a passing build.
