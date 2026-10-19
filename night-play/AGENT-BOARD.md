# NIGHT Play — multi-agent game expansion board
**Updated:** 2026-10-19 · **Repository:** [Tomex777/Build](https://github.com/Tomex777/Build) · **Actual site:** [combined preview](https://night-play-combined-preview.vercel.app/) · **Current website source:** `feature/party-online-rooms-20261019` (PR #386) · **Instruction file:** [AGENTS.md](AGENTS.md)

**Important:** All entries below are **QUEUED**, not currently running. Creating this board/GitHub issues does not spawn persistent agents. Start a separate Codex cloud task/session for each issue, with one assigned scope/branch. Each agent updates its issue and opens an independent PR. Only Agent 09 updates this central board and common game catalog.

| Agent | Status | Work item | GitHub issue | Exit condition |
| --- | --- | --- | --- | --- |
| 01 | QUEUED | Fruit Slice *visible* splitting, Runner obstacle consequences, Brick Breaker difficulty | [#391](https://github.com/Tomex777/Build/issues/391) | All three actually played and proven with before/after screenshots |
| 02 | QUEUED | Piano Tiles (commercial long tile + holds) | [#392](https://github.com/Tomex777/Build/issues/392) | New playable routed module with sound/touch accuracy |
| 03 | QUEUED | Pong, Frog Crossing, Asteroids | [#393](https://github.com/Tomex777/Build/issues/393) | 3 genuinely independent games and browser tests |
| 04 | QUEUED | Word Search, Nonogram, Sokoban, Pipe Connect | [#394](https://github.com/Tomex777/Build/issues/394) | 4 puzzle engines with valid levels and touch tests |
| 05 | QUEUED | Klondike Solitaire, Spider, Mahjong Solitaire | [#395](https://github.com/Tomex777/Build/issues/395) | 3 rule-complete games, win/undo, touch tests |
| 06 | QUEUED | Chess, Reversi, Battleship | [#396](https://github.com/Tomex777/Build/issues/396) | 3 rule-complete games, true CPU play, no mislabeled online |
| 07 | QUEUED | Polish Wing Hop/Flappy, Maze Chase/Pac-Man, Tetris-style Block Drop, WHOT, Draughts | [#397](https://github.com/Tomex777/Build/issues/397) | Multiple real playthroughs, corrected feel, no animation lag |
| 08 | QUEUED | Actual online matches + cooperative Jigsaw | [#398](https://github.com/Tomex777/Build/issues/398) | 2 separate browsers see the same remotely synchronized match |
| 09 | QUEUED | Coordinator, website integration, shared UI, Playwright/CI, verified previews | [#399](https://github.com/Tomex777/Build/issues/399) | Verified new routes visible in actual NIGHT Play game library |

## Immediate priority / independent launch plan

1. **Agent 01 first:** verify/fix the exact Fruit Slice complaint; recent tests on commit `57e0b09` pass but user's expected visual slicing quality needs independent real visual assessment. In the same owned action engine, fix Runner and Brick Breaker after fruit.
2. **Agents 02–06 in parallel:** build new self-contained modules; do not edit shared `night-play/index.html`. Ship one playable game, run tests, commit it, then progress to the next game in that issue.
3. **Agent 07 in parallel:** owns existing `arcade/` and `club/` only. Keep user-approved animation constraints, original gameplay and proper mobile controls.
4. **Agent 08 in parallel:** owns remote multiplayer and co-op Jigsaw. Coordinate with the Chess author before sharing game state.
5. **Agent 09 starts alongside everybody:** protect the official NIGHT website look; integrate only games proven playable, run previews and record one definitive test/commit per deployed wave.

## Shared acceptance criteria

- No tiles for unfinished games. No demo UI described as complete.
- Real gameplay interaction in Chrome/Chromium; inspect actual visual output and difficulty.
- At minimum phone viewports 320, 360, 390, 412 + desktop.
- Original/rights-cleared artwork and SVG thumbnails only; no redundant UI, no arrow clutter; SFX-only except Piano Tiles and optional quiet Jigsaw track.
- Full relevant CI and game-specific tests, followed by actual replay to verify fixes.
- One owner per source file. Base each agent branch on the latest integrated website head and open a PR for Agent 09. Update this board only after factual status changes.

## Next continuation instruction for each coding session

> Read `night-play/AGENTS.md` and the exact GitHub issue assigned to you. Inspect the *current* NIGHT Play code, change the real website repository, actually play each game with touch and keyboard, inspect screenshots, fix issues, run tests and open a PR. Do not stop when a build starts or a single test passes. Continue through the scoped issue, and if a hard execution limit ends your session, commit work and leave a precise, honest resumable handoff on the issue. Do not call your issue DONE until every assigned acceptance criterion is verified.
