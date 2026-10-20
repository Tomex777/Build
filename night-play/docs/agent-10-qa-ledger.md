# Agent 10 — independent NIGHT Play real-browser QA ledger

**Updated:** 20 October 2026 (00:55 UTC)  
**Owner:** Agent 10, gameplay/visual QA · **GitHub issue:** #407  
**Source baseline:** `Tomex777/Build`, `feature/party-online-rooms-20261019`  
**Independent branch:** `agent10/night-play-qa-20261020`  
**File ownership:** `night-play/qa/agent10-deployed-gameplay.mjs`, this ledger and a uniquely named QA workflow. **No gameplay engines, common site shells, coordinator CI or shared central backlog modified.**  
**State:** QA work in progress; 12 tested browser interactions passed on the *fix preview*, but canonical release, other games and two-client authenticated multiplayer **not certified**.

## Executed, not simulated

Executed Playwright 1.56.1 against real external HTTPS deployments from a running Vercel Sandbox (`nightplay-agent10-qa-20261020`), Chromium 141 headless, with actual CDP `Input.dispatchTouchEvent` touch-start, move and release, physical-time waits, pointer movement, clicks and timed snapshots; no fake game-engine direct-state injection. Sampled running frames, took screenshots and **personally visually inspected** the Fruit Slice intact/cut/separated images plus Sky Jumper and Brick Breaker game screens. Source/release commit identifiers were resolved from Vercel deployment metadata.

**Tested candidate:** `https://night-play-combined-preview-p0uc189jp-tomex777.vercel.app` · READY deployment `dpl_NtQJvq4jSuBykGGvf62koi1groHf` · commit `7887b7360fddb8e93dbc830d5e5789ba2364b42b` (Fruit Slice PR #401).

| Area | Real interactions and observed result | Status |
| --- | --- | --- |
| Fruit Slicing five viewport sizes | 320×700, 360×800, 390×844, 412×915, 1280×800. No horizontal overflow, canvas stays within screen. | PASS |
| Fruit trajectories | Live frame sampling at deterministic seed: fruit climbs to logical **y≈166** on a 550px board; accessible upper-half slicing. | PASS on candidate |
| Fruit swipe scoring | CDP finger motion through empty upper edge leaves 0 points. Finger swipe through actual fruit earns 10, removes whole fruit, produces two genuine moving halves and 16 spray particles; pieces 79px apart 160ms later. Screenshots inspected. | PASS on candidate |
| Fruit loss/restart | Hands-off game lost after five real missed pieces. Click Play again → fresh run, 0 score/misses. | PASS |
| Runner hazards | Idle character collides with first low flyer, loses at score 54 and 0 passed; touch JUMP at flyer x≈191 survives and passes one flyer, score≈60; loss/restart works. | PASS |
| Brick Breaker | Real touchscreen drag moved paddle to x≈304; board tap launched ball; first collision destroyed only one brick, score 15, 41 bricks remain. Screenshot inspected. | PASS for first impact only |
| Sky Jumper | Held finger with 90 tracked platform-steering moves; climbed 486 height, registered 8–9 physical bounce transitions, did not die. Screenshot inspected. | PASS for tested early run |
| Space Shooter | Finger drag moved ship x=195→243; surviving animation produced enemies/projectiles and scored 20 on an enemy. | PASS for early run |
| Combined Games page | `/games` displays all five action links pointing to actual `/action/?game=...` routes; 390px page has no horizontal overflow. | PASS |
| Multiplayer two contexts | Two separate real browsers opened host screen and guessed invite. Unlinked host is correctly blocked with `Connect WhatsApp`. Invalid guest invite fails to connect. **Does not test server-authoritative room participation.** | BLOCKED by linked host and backend |

## Critical release discrepancy (P0)

The **canonical user-facing** `https://night-play-combined-preview.vercel.app/action/?game=slice` still returns the *old* trajectory: independent timed Chromium comparison using the same deterministic seed and viewport measured minimum **y≈400.5** (fruits barely leave the lower board). The READY fixed candidate reaches **y≈166.2**. The branch fix is real; the user-facing URL **has not yet been promoted**. PR #401 remains separate and unmerged. Do **not** call Fruit Slice released or mark it done based on this candidate.

**Owner/action:** Coordinator (Agent 1): review & integrate PR #401 without overwriting pending Agent 3/4 `action/games.js` changes, trigger full combined workflow, inspect screenshots, redeploy/promote, then run the independent QA script against the canonical alias and verify its Git SHA matches the tested integration.

## Other unverified gates

Actual physical Android touch responsiveness, diagonal/vertical cuts and bomb loss, sustained Brick Breaker level progression / full losing playthrough, 2048/Memory/Jigsaw/WHOT/Chess/Checkers/Piano Tiles/Pac-Man and multi-viewport behavior outside Fruit Slice remain **open**. A functional online NIGHT–NOMI quiz requires an authorized real host, two independent clients with separate auth/session state, an invite, votes, state reconciliation and reconnect validation; mocked party APIs are not release evidence. PR #415 explicitly remains live-backend blocked.

## Reproduction and next executable action

1. On this branch after `npm install --no-save playwright@1.56.1` and `npx playwright install --with-deps chromium`, run `NIGHT_PLAY_BASE_URL=https://night-play-combined-preview-p0uc189jp-tomex777.vercel.app node night-play/qa/agent10-deployed-gameplay.mjs` and verify **12/12** checks; screenshots + JSON saved in `night-play/visual-output/agent10/`.
2. Switch `NIGHT_PLAY_BASE_URL=https://night-play-combined-preview.vercel.app`; expect the **fruit arc assertion to FAIL** until promotion. Save the failing JSON/screenshots as regression proof, never silently skip.
3. On a new integrated deploy, repeat this script, then manually inspect full-size JPGs and play the remaining game modes, especially Brick Breaker sustained difficulty and Fruit Slice non-horizontal cuts. Report reproducible failures on each owning issue and retest after fixes.
4. Run/monitor `NIGHT Play Agent 10 Deployed Gameplay QA` GitHub Action on this branch for 14-day reviewable screenshot artifacts. A successful job is **not** a substitute for screenshot review or physical-device testing.

**Coordination messages posted:** issue #391 (game owner) and #399 (release integrator) on 20 October. This ledger is deliberately separate from `docs/agent-backlog.md`, which the coordinator owns. Keep QA ownership and honest status recorded there after review.
