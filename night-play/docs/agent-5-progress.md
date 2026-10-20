# NIGHT Play — Agent 5 Puzzle Workstream

Updated 2026-10-20. Owned by Agent 5. Root `AGENTS.md` and `docs/agent-backlog.md` are cross-agent coordinator-owned files; do not overwrite other agents' versions.

## Environment and branch

- Repository: `Tomex777/Build`.
- Integration target observed: `feature/night-play-ludo-motion-polish-20261014`. The NIGHT Play website is under `night-play/`; repository default `main` is NOT the integrated web app.
- Isolated branch: `agent5/night-play-puzzle-quality-20261020`.
- Puzzle ownership: `night-play/jigsaw/**`, `night-play/puzzles/memory.js`, Memory rules/styles, the existing 2048 in `night-play/index.html`. Coordinate edits to the monolithic index with Agent 1 and Agent 9 before integration.
- Existing Jigsaw test paths: `night-play/jigsaw/mobile-smoke.mjs`, `board-logic-test.mjs`, `difficulty-audit.mjs`. Existing Memory tests: `night-play/puzzles/mobile-smoke.mjs`, `logic-test.mjs`.

## Live task ledger

| Task | Owner | Status | Test / evidence | Blocker and next executable action |
|---|---|---|---|---|
| Preserve 24/48/144-piece responsive Jigsaw and no full repaint per drag | 5 | Existing implementation inspected, no blind refactor | New Action runs real touch Jigsaw mobile smoke | Confirm Action result and screenshot artifact; inspect regressions and fix only demonstrated bugs |
| Fix Memory Match accessibility for all five themes | 5 | Implemented on isolated branch, awaiting browser CI | `night-play/agent5-puzzle-playtest.mjs`: actual touch tap, mismatch, match, reload, full 15-pair win and spoken face labels | Review browser CI and evidence; fix any failures |
| Maintain Memory default 6×5, size preference and mobile grid | 5 | Existing implementation retained; audit in progress | Existing `puzzles/mobile-smoke.mjs` checks 4×4 to 6×10, screenshots and no overflow; new test captures 390 and 320px | Rerun tests in Action |
| 2048 win and loss feedback with 4×4/5×5/6×6 retained | 5 | Implemented on isolated branch, awaiting browser CI | Real keyboard and CDP touch swipe, win at 2048, undo, dead board, high tiles and invalid-save test | Review run, screenshots and improve as needed |
| Branch playtest integration | 5 | In progress | `.github/workflows/night-agent5-puzzle-playtest.yml` uploads gameplay screens | Record run URL, status and merge outcome |
| All other games, account and multiplayer | Agents 1–4 and 6–10 | Out of scope for Agent 5 | Coordinator-owned | Do not touch their files |

## Changes staged

- `night-play/puzzles/memory.js`: an opened card now announces its theme-specific actual face (e.g., Lion or Amara), while face-down cards remain private. Match labels update without recreating the board.
- `night-play/index.html`: 2048 announces winning at 2048, recognizes a loss even on resumed unsolvable boards, preserves undo, rejects invalid save tiles/scores and renders values above 2048 legibly.
- `night-play/agent5-puzzle-playtest.mjs`: standalone real browser game interactions with desktop keyboard and CDP mobile touchscreen. Saves 390px win/loss and 320px layout images.
- `.github/workflows/night-agent5-puzzle-playtest.yml`: pinned browser tests plus existing Jigsaw touch QA; artifact upload even if tests fail.

## Definition of done

Implementation and syntax checks are not completion. Required: isolated branch CI green; actual game inputs at phone viewport; score + win/loss/undo + persistence verified; 24–144 Jigsaw behavior and mobile drag examined; screenshots viewed; changes reviewed and merged by Agent 1; full combined website retested. If missing, describe as unverified/pending.

## Recovery instructions

1. Read this ledger and the coordinator's current `AGENTS.md` + `docs/agent-backlog.md` before work.
2. Inspect current branch and integration target commits; avoid overlapping another agent's active modifications.
3. Review latest Action for `night-agent5-puzzle-playtest.yml`, read any failed job logs, then examine screenshots from the artifact.
4. Make focused corrections on this branch, rerun the hands-on tests, and record run/commit/defect evidence here.
5. Open/update a PR targeting the actual integrated NIGHT Play branch; do not silently merge without coordinator review.
