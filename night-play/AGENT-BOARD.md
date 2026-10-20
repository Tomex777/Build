# NIGHT Play — ten-agent development board

**20 October 2026 · Owner: Agent 1 Lead Coordinator**

Source of truth: [Root engineering contract](../AGENTS.md) · [Durable task ledger](../docs/agent-backlog.md) · [NIGHT Play instructions](AGENTS.md) · [Approved design system](DESIGN.md). GitHub repo: `Tomex777/Build`. Website integration: `feature/party-online-rooms-20261019` / PR #386. Combined preview: `https://night-play-combined-preview.vercel.app/` (checked serving `57e0b09`, NOT the unmerged fruit fix).

**Truthful execution state:** The only session we know is actively executing is Agent 1 coordinating this branch. Other rows are not active agents merely because a task issue exists. Start an **actual independent coding task/worktree** for each row; then update the ledger. Nothing here spawns persistent workers.

| Agent | Current factual status | Assignment / tickets | Scope |
| --- | --- | --- | --- |
| **1 — Lead Coordinator** | ACTIVE (current session) | [#399](https://github.com/Tomex777/Build/issues/399) | Audit, root rules/backlog, ownership locks, integration review, preview SHA and release gate |
| **2 — Fruit Slicing** | REVIEW, CI pass; **not merged** | [#391](https://github.com/Tomex777/Build/issues/391), [PR #401](https://github.com/Tomex777/Build/pull/401) | Fix trajectories and visually inspect real cutting; screenshots must be examined |
| **3 — Runners** | QUEUED | [#402](https://github.com/Tomex777/Build/issues/402) | Sky Jumper and Endless Runner collision, physics and challenge |
| **4 — Arcade** | QUEUED | [#403](https://github.com/Tomex777/Build/issues/403) | Brick Breaker difficulty, Space Shooter and Wing Hop/Flappy polish |
| **5 — Puzzles** | QUEUED | [#404](https://github.com/Tomex777/Build/issues/404) | Jigsaw, Memory Match 6×5, 2048 |
| **6 — Board & Cards** | QUEUED | [#397](https://github.com/Tomex777/Build/issues/397), [#396](https://github.com/Tomex777/Build/issues/396) | WHOT, Chess and Checkers (real CPU + separately transportable legal rules) |
| **7 — Additional Games** | QUEUED | [#392](https://github.com/Tomex777/Build/issues/392)–[#395](https://github.com/Tomex777/Build/issues/395) | Piano Tiles, existing Pac-Man audit, new distinct arcade/puzzle/card games |
| **8 — NIGHT + NOMI** | BLOCKED for live release; QUEUED for isolated tests | [#405](https://github.com/Tomex777/Build/issues/405), [#398](https://github.com/Tomex777/Build/issues/398), PRs [#386](https://github.com/Tomex777/Build/pull/386)/[#387](https://github.com/Tomex777/Build/pull/387) | Hosted invitation links, real server rooms, votes/results/reconnect and linked identity |
| **9 — UI/UX** | QUEUED | [#406](https://github.com/Tomex777/Build/issues/406) | Full website polish, SVG icons, lock approved Channels page |
| **10 — Independent QA** | QUEUED | [#407](https://github.com/Tomex777/Build/issues/407) | Actually play every game, inspect screenshots/recordings, reproduce/fix/replay cycle |

## Immediate concrete gate

1. PR #401 / commit `7887b7360fddb8e93dbc830d5e5789ba2364b42b`: the `NIGHT Play Preview CI` run `39348384258` passed and produced [screenshot artifact #12232381826](https://github.com/Tomex777/Build/actions/runs/39348384258). **No Agent 10 visual sign-off yet; not in combined preview.** Do not falsely mark fruit done.
2. The Runner flight and Brick Breaker impact tests already exist, but the complaints concern player-visible ease/animation. A new hand-controlled playthrough and difficulty assessment are necessary.
3. PR #386's room client and PR #387's MSCC service are still separate unmerged layers. Server-backed NOMI flow requires protected release approval and isolated browser host/guest tests; pass-and-play is not online.
4. Independent game agents may implement new `piano-tiles/`, `brain/`, `classics/`, `solitaire/` modules while the action monolith is locked. Never add catalog cards before each module is actually verified.

## File conflict prevention

- The action engine is presently **one** file: `action/games.js`. Agents 2, 3 and 4 acquire that file one at a time; parallel new-game/test work is fine, concurrent writes are not.
- Agent 5 and 8 coordinate Jigsaw piece-state/API contracts; Agent 6 and 8 coordinate chess move-validation contracts.
- Agent 1 owns catalog and deployment integration; Agent 9 must agree CSS changes before editing shared files.
- Keep each contributor's own branch/worktree and PR. No one can claim a detached task is running without a real runner/session.

## Resume contract

At entry read the root `AGENTS.md`, `docs/agent-backlog.md` and your GitHub issue. At exit provide branch/commit, tests **actually run**, artifact IDs, known blockers and next executable command. Do not stop on builds. The definition of DONE in the root file applies.

### Archived allocation note

The pre-20-October setup had nine old agent slots and [issues #391–#399](https://github.com/Tomex777/Build/issues?q=is%3Aissue+391..399). Their titles use **historical numbering**. This board supersedes that numbering without deleting the tickets or their approved feature specifications. The additional-game issues are re-grouped under Agent 7.
