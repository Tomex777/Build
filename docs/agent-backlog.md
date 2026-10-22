# NIGHT Play — durable multi-agent task ledger

**Recorded:** 2026-10-20, Agent 1 coordinator. **Status is factual, not a promise of agents running.**

## Integration baseline and verified state

- Repository: `Tomex777/Build`; active website integration PR #386 (`feature/party-online-rooms-20261019`, head **`47839a933119de513f7e38c4907e6bb1c7b26f1d`** after integrating #401 and #412). Main is **not** the latest web branch.
- **Historical alias:** `night-play-combined-preview.vercel.app` served deployment `dpl_4amnxRwVa1jsrqzHWQ7ijTjs6V1o` at commit `57e0b09aa213b15b1ca83666d5cde0002c525364` when inspected; it must not be assumed to auto-update. **Verified new isolated preview:** `https://night-play-combined-preview-g255rboee-tomex777.vercel.app` deployment `dpl_JBXYPJ9LkXLRDBQJTvrwm3nWi57L` READY at `47839a933119de513f7e38c4907e6bb1c7b26f1d`. Fetched actual `/action/games.js` from this deployment (200) and confirmed `vy:rand(-970,-880)` is shipped. Keep preview's unique URL separate from production alias until the full gate is signed off.
- PR #401, `fix/fruit-slice-higher-arcs-20261020`, head `7887b7360fddb8e93dbc830d5e5789ba2364b42b`: increases Fruit Slicing vertical launch speed; `NIGHT Play Preview CI` run `39348384258`, #573 **passed**; visual artifact `12232381826` available for review. **MERGED into web integration** as `9ef99469ec29638b6bdfb017c342bf0c80717b15`; CI evidence and actual screenshots reviewed by coordinator (fruit halves, runner death/jump, brick impact), but physical phone quality, varied cuts and independent Agent 10 QA remain open. Existing action browser test also covers flyer collision and Brick Breaker single-brick impact; user-quality acceptance remains open.
- Frontend online room PR #386 and protected MSCC backend PR #387 both open; NOMI/native online deployment **not yet verified**. PR #382 identity work is not equivalent to a verified live server rename. Release backend only in an authorized protected batch.
- Coordinator instructions & five-viewport QA PR #412 **MERGED** as `47839a933119de513f7e38c4907e6bb1c7b26f1d`. Cross-viewport Chromium CI run #593 (`39350041766`) **PASSED** with artifact `12233151136`: on widths 320/360/390/412/1280 actual swipes cut a fruit for 10 points and two separated halves, false touch paths scored 0, Memory Match 6×5 showed zero horizontal overflow; unattended Runner died and restart worked. Follow-up PR #420 tests **fruit upper-half apex** on this integrated source; record its result after CI completes.
- Project already has original `night-play/AGENTS.md`, `DESIGN.md`, `AGENT-BOARD.md`, `selftest.mjs`, game-specific browser tests, `.github/workflows/night-play-preview-ci.yml`. Preserve these.

Statuses: `QUEUED` = no launched session observed; `ACTIVE` = running session actually verified; `REVIEW` = PR submitted but end-to-end gate not passed; `BLOCKED` = documented external prerequisite; `DONE` = integrated + independently played + preview verified.

| Agent / priority | Issue or PR | Status | Branch (assigned, not necessarily created) | Test/evidence state | Blocker / next executable action |
| --- | --- | --- | --- | --- | --- |
| A1 P0 Lead coordination | #399; merged PR #412; QA PR #420 | **ACTIVE this session** | `coord/night-play-agent1-20261020` | Inspected actual PR #401 screenshots; CI #593 verified real five-viewport Chromium gameplay; verified deployed source contains higher arcs | Review PR #420 combined apex-gate CI and screenshots; keep full physical-device QA, specialists and NOMI backend work open. |
| A2 P0 Fruit Slicing | #391; PR #401 | **REVIEW** | `fix/fruit-slice-higher-arcs-20261020` | Preview CI #573 PASS; artifact #12232381826 visually reviewed; merged into web integration; combined CI #420 underway | Confirm upper-half fruit apex with real swipe in PR #420 and preview frames; Agent 10 independent touch/long-session QA remains; no physical-device completion claim. |
| A3 P0 Runners / Sky Jumper | #402 | **QUEUED** | proposed `agent3/night-play-runners-20261020` | Existing `action/real-play-corrections.mjs`/ `long-play.mjs` cover some Chromium cases; phone visuals not signed off | Launch distinct agent; reserve exclusive `action/games.js` edit window; test first unavoidable flyer and Sky platform physics. |
| A4 P0 Arcade | #403 | **QUEUED** | proposed `agent4/night-play-arcade-20261020` | Existing `action/mobile-smoke.mjs` + impact tests; game difficulty complaints unresolved | Reserve action file after A2/A3; play Brick Breaker for full stages, tackle shooter/Flappy. |
| A5 P1 Jigsaw / Memory / 2048 | #404 | **QUEUED** | proposed `agent5/night-play-puzzles-20261020` | Existing puzzle/Jigsaw Chromium tests, no independent QA verdict | Launch puzzle session; agree a Jigsaw state interface and lock with A8; test 6x5 defaults, 144-piece performance. |
| A6 P1 WHOT / Chess / Checkers | #397, #396 | **QUEUED** | proposed `agent6/night-play-board-20261020` | Existing `club/mobile-smoke.mjs`; no new results | Play/fix WHOT animation, hand count, draw; implement verified Chess legal moves and Checkers; coordinate online rules adapter with A8. |
| A7 P1 Additional games | #392, #393, #394, #395 | **QUEUED** | proposed `agent7/night-play-additional-20261020` | No verified new completed game in this wave | Start with complete Piano Tiles, then distinct games; inspect existing arcade Pac-Man before duplicating. |
| A8 P0/P1 NIGHT × NOMI | #405, #398; PRs #386/#387 | **BLOCKED for live release**, test work QUEUED | proposed `agent8/night-nomi-20261020` | Existing party-room smoke tests, but production cross-service/two-device verification missing | Arrange protected backend release approval and signed identity bridge, independently exercise host+2 guests, votes/results/reconnect; do not claim remote live until complete. |
| A9 P1 UI/UX | #406 | **QUEUED** | proposed `agent9/night-play-ui-20261020` | Existing visual-inspect artifacts, no whole-site audit verdict | Inspect all mobile routes, preserve locked Channels; send targeted CSS/brand fixes in separate PR. |
| A10 P0 independent QA | #407 | **QUEUED** | proposed `agent10/night-play-qa-20261020` | Coordinator inspected screenshot artifact #12232381826 and #593 automated real touch; independent Agent 10 session has NOT started | Launch actual separate QA session; test touch/keyboard, challenge/loss/restart on 5 viewports, two real sessions online; return reproducible issues. |

## File ownership / concurrency locks

- Coordinator: shared `night-play/index.html`, `site-shell.css`, `game-shell.css`, `vercel.json`, preview workflow/QA registration and project instructions.
- **Action monolith lock:** `night-play/action/games.js` is shared across A2–A4. **One active editor at a time**, not three. Use non-overlapping tests, research, or new isolated modules while another agent edits; coordinate cherry-picks/reviews. PR #401 was integrated; preserve its high-arc velocity unless real tests justify changes.
- Jigsaw source lock: A5 gameplay ↔ A8 network synchronization must agree interface and ownership; never concurrent edit of same file.
- `night-play/arcade/` / `club/`: A4/A6/A7 own per-game sections by explicit coordinator assignment only; avoid concurrent conflicts.
- QA owns tests/evidence, can report bugs but does not silently rewrite another agent's implementation.

## Launch and continuation

**This interface cannot itself guarantee persistent execution of ten independent agents.** Starting the roles requires ten actual separate coding tasks / worktrees and a runner that manages them. Creating issues or branches alone does not constitute launch. Update the ledger to ACTIVE only on proof of a real executing session; no fictitious progress.

Next integration sequence: (1) conclude PR #420 upper-half actual-apex QA and mobile screenshot inspection; (2) Runner/Sky + Brick challenge correction; (3) finish/independently verify #386/#387 and NOMI; (4) correct existing puzzles/card games; (5) introduce playable new games; (6) connect routes only after QA; (7) verify combined Vercel SHA, avoid production promotion without authorization.

Each contributor must update: issue, owner, branch, last commit, tests with command and result, captured screenshots/artifacts, unresolved bugs, dependencies, precise next step. Never change status from merely observing a passing build.


## WAVE 2 — status reset and product-completion priorities (2026-10-20)

**This section supersedes first-wave QUEUED/ACTIVE interpretations above.** Ten-specialist combined PR #436 is **MERGED** into `feature/party-online-rooms-20261019`; see GitHub PR #436 and the successful integrated gameplay checks. Live preview deployment is ready, but verify its git SHA before making claims. PR #435 Snake Xenzia swipe-reliability remains open. Hosted multiplayer and protected MSCC server deployment cannot be assumed production-ready. User's mandate: **large improvements in real play quality**, not simply more feature names.

**Wave 2 communication and work claims:** [Issue #459](https://github.com/Tomex777/Build/issues/459). Role briefs: `night-play/agents/agent-01.md` through `agent-10.md`. Agents take next unblocked quality items and keep integrated website working; each work item requires source PR, full-game playtest, screenshots/recording, responsive viewports, release gate, and merged SHA. Shared-file locks continue. The agent roster is task allocation, not evidence 10 sessions continue running after chats terminate.

| Agent | Immediate next action | Release gate |
| --- | --- | --- |
| A1 | Coordinator and release integrity | Gameplay interaction + merged integration evidence, not build-only |
| A2 | Fruit Slicing + Snake gesture reliability | Gameplay interaction + merged integration evidence, not build-only |
| A3 | Runners, Sky Jumper and platform challenge | Gameplay interaction + merged integration evidence, not build-only |
| A4 | Brick Breaker, Shooter and Wing Hop depth | Gameplay interaction + merged integration evidence, not build-only |
| A5 | Jigsaw cooperative-ready puzzles and 2048 | Gameplay interaction + merged integration evidence, not build-only |
| A6 | WHOT, Chess and Checkers: credible opponents | Gameplay interaction + merged integration evidence, not build-only |
| A7 | Piano Tiles, Maze Chase and catalogue quality | Gameplay interaction + merged integration evidence, not build-only |
| A8 | Real multiplayer, identity and co-op Jigsaw | Gameplay interaction + merged integration evidence, not build-only |
| A9 | Product-wide visual overhaul and accessibility | Gameplay interaction + merged integration evidence, not build-only |
| A10 | Independent full-catalog long-session gameplay QA | Gameplay interaction + merged integration evidence, not build-only |
