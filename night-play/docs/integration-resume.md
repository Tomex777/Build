# NIGHT Play — Wave 2 INTEGRATION / RESUME runbook

**User intent (October 20, 2026):** existing ChatGPT agent chats may have stopped. Resuming one chat with "Continue" must lead to actual integration and bugfix work, not a new wave, a new backlog, or an unsupported claim of always-on activity. Root app: `night-play/` in `Tomex777/Build`. **Canonical integration:** `feature/party-online-rooms-20261019`. **Coordination:** https://github.com/Tomex777/Build/issues/459. Agent 1 is the integration owner. Agent 10 is independent QA.

## Verified baseline at runbook creation — NOT a live status guarantee

First ten-agent combined PR **#436 MERGED**, Wave 2 instructions PR #461 MERGED. At 20 October 2026 ~08:38 Africa/Lagos, **Wave 2 feature PRs remained OPEN** including #463 (coordinator Snake), #464 (UI), #465 (puzzles), #466 (Fruit), #467 (Bricks), #469 (cooperative Jigsaw), #470 (Chess), #471 (QA), #473 (runners), #477 (Piano Tiles). Previous Snake #435 overlaps #463. Re-check every PR's latest head, base, CI and diff; do not trust the list after this timestamp.

The combined-preview Vercel site has multiple immutable deployments and an alias. An available deployment or green first-wave CI does **not** mean the latest Wave 2 features are merged, live, or fully playable.

## AGENT 1 FIRST: integrate, don't expand

1. **Inspect reality.** Read Issue #459 latest comments, current canonical branch SHA, all open Wave 2 PRs, changed-file lists, Github Actions and test artifacts, and Vercel deployments. Make a compact PR merge ledger: `PR | head SHA | files | overlap | CI | real play QA | action`. If a PR is already merged/superseded, explicitly note it.
2. **Conflict graph first.** Compare shared-file overlaps, especially `night-play/action/games.js` (Fruit A2, Runner A3, Bricks A4), `night-play/arcade/games.js`, `night-play/index.html`, CSS, and Jigsaw model/network (A5/A8). Do not pick an arbitrary winning side or overwrite a whole file. Extract coherent modules or integrate **one game section at a time**, preserve all affected features, add targeted regression tests and seek specialist reviews via Issue #459.
3. **First concrete quick win:** examine PR #463 vs #435 Snake duplicates. Choose the better proven fix or combine safely; require first-swipe reliability on actual touch plus long-session tests. Then integrate if reviewer/tests support it. Avoid landing both duplicate fixes blindly.
4. **Sequence functional PRs:** land independent low-overlap patches first (subject to checks), then resolve same-file action-engine PRs A2/A3/A4 with isolated verified hunks, then Jigsaw A5/A8 coordinated API, Chess A6, Piano A7, UI A9. QA A10's PR #471 may be incorporated early as a truthful coverage reporter but a green launch/route check does **not** certify full rounds.
5. **One continuously playable branch:** after each small merge rerun `node night-play/selftest.mjs`, appropriate action/arcade/puzzle/chess/piano scripts and the site-wide Preview CI. Start real Chromium browser/touch game interactions; inspect screenshots rather than checking only automation exit status. Record actual game-over, victory, restart, difficulty and frame behavior. On CI failure, diagnose/fix before the next merge; **do not disable failing checks to get green**.
6. **Release separate from integration:** verify Vercel build commit SHA after preview deployment, smoke actual preview. Keep original production and protected MSCC backend unchanged without user authorization; online mode needs independently verified server-owned state, not just two local fake clients.
7. **Keep work moving during active session:** if CI is queued, inspect another PR's dependency graph or add independent tests rather than idle polling. When tools/time limit ends, persist exact last SHA, merged PRs, failed test, screenshots, pending approvals and **next executable command** in Issue #459 so a subsequent "Continue" can pick up the work.

## Agents 2–9: repair and deliver, not re-implement

Read own existing Wave 2 PR and latest CI. On "Continue", **do not create a fresh branch or rewrite approved games** if unfinished work already exists. Address real review feedback/CI/errors; rebase on latest integration carefully without losing others' work; test touched games from real inputs and supply precise `[PLAY A#][READY]` with changed files, source SHA, test results, screenshots, dependency on shared locks and merge readiness. If PR is merged, take a high-priority P0/P1 bug from Agent 10 instead of speculative new games. If another agent holds a shared lock, investigate defects/tests without simultaneous source modification.

## Agent 10: independent release gate

Resume QA PR #471, reconcile with the latest canonical site SHA and playwright dependencies. Complete real interactions and full rounds for **every linked live game**, not merely click each card. Test 320/360/390/412/1280 viewports plus real-phone input if accessible, score/win/lose/restart and long-session difficulty, latency and responsiveness. Record `PASS / FAIL / BLOCKED / NOT_TESTED` honestly. For multiplayer require actual remote clients and an authorized deployed backend; anonymous API=401 is not proof of live online. Send repros directly to owners and retest their merged fixes.

## GitHub message contract

```text
[PLAY A#][CLAIM|DESIGN|BLOCKED|READY|INTEGRATED|VERIFIED|HANDOFF]
Canonical SHA:
My PR/head SHA:
Changed files / conflict owner:
Actual CI + browser input and screenshot evidence:
Independent reviewer or next dependency:
Next executable action:
```

**Done means** all P0/P1 merged, proper site quality verified, latest immutable preview deployed and inspected, independent A10 QA passed, and user-approved promotion where required. No agent is automatically active unless the user or external runner launched it.
