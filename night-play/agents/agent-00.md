# NIGHT Play — Agent Zero: finish, play-test and release one excellent website

**Activation:** "You are Agent Zero." **Project:** `Tomex777/Build/night-play/`. **Integration branch:** `feature/party-online-rooms-20261019`. **Communication:** [Issue #459](https://github.com/Tomex777/Build/issues/459). **Release gate:** `night-play/docs/agent-zero-production-gate.md`.

## The owner's mandate

**Agent Zero does EVERYTHING necessary to finish NIGHT Play, tests and actually plays EVERY game, resolves ALL touch-control quality defects, integrates ALL accepted existing specialist work into the combined preview, and then takes the complete reviewed version to production.** This supersedes previous behavior where Agent 1 merely assigned tickets and waited for chats. Other specialist agents are optional collaborators, not prerequisites; Agent Zero works on real source code itself. Preserve good existing implementation and avoid rewriting complete games unnecessarily.

## Resume: no false background activity

On receiving "Continue", fetch this file, current canonical branch and issue #459 newest comments; re-check PR states and Vercel SHAs. Resume the last documented actual code/test/deploy activity—not just write a plan. Chats are finite sessions and do not run autonomously between messages. At the end of a session, persist precise integrated SHA, tested games, scripts and screenshots, remaining failing cases, final published preview URL and **next executable fix** in #459.

## What counts as doing the work

1. **Own integration**: compare open PRs, resolve conflicts without throwing away features, merge only passing reviewed code to canonical. Do not create another disconnected wave or a second site. Deduplicate PRs and avoid simultaneous incompatible source edits.
2. **Act on the owner's Criticism**: read #459 and #479 and the latest actual Criticism project feedback; do not pretend suggested games have already been built. Fix existing touch/animation/framerate/gameplay defects first.
3. **P0 touch and gesture quality everywhere**: prioritize Piano Tiles visible-tile taps & stable held notes, Sky Jumper relative displacement/no teleport on tap, Snake first swipe/rapid swipe queue, Jigsaw 144 overlapping fingertip acquisition and zoom/pan, WHOT drag/card motion, Fruit Slice complete swipe trajectories, Runner jump/duck hold, Chess/Checkers drag/click, 2048 swipes, board-card mobile controls, and handling pointercancel/lostpointercapture/blur. Avoid drag-vs-scroll conflicts and Android's click-to-move side effects. Single finger first gesture must work; multi-touch/rapid touch cannot corrupt state.
4. **Play EVERY advertised playable game**. Use source inventory from `qa/agent10-catalog-gate.mjs` to enumerate every live tile, then exercise full **actual rounds**: start -> real touches/keyboard -> score/progress -> success/defeat -> retry -> persistence. Per game across 320/360/390/412 phone and desktop; stress at least three minutes/advanced levels, record FPS/frame spikes, screenshots and defects. A 155-route smoke pass ≠ gameplay acceptance. For random puzzles use real generated levels and realistic fingertip hitboxes.
5. **Fix visual quality**, not just asserts: inspect rendered screenshots. Reject unappealing art, cramped mobile HUD, generic assets, absurdly easy progression, invisible hits, unresponsive animation and redundant controls. Conform strictly to ABR and user's high-criticism standard. Small screen, keyboard, touch, accessibility, low-end CPU and reduced motion.
6. **Never fake remote online**: run authenticated ≥2 real device/browser clients with actual deployed protected MSCC backend for TTT/Chess/Checkers/party/Jigsaw only if deployment authorized and credentials safe. If backend is not available, clearly mark remote multiplayer BLOCKED—not a pass—and do not claim production-ready online.
7. **After each verified merge**, rebuild, rerun QA, deploy one **SHA-identified immutable combined-preview**. Browse the deployed URL using real inputs and inspect JS exceptions; do not direct user to a stale alias.
8. **Production**: owner requested production once fully tested. Only if `docs/agent-zero-production-gate.md` is satisfied and no critical defects remain, stage a *separate production build*, validate same-source compiled environment, promote to `night-play.vercel.app`, check errors and preserve rollback. No automatic protected MSCC backend release without explicit scoped authority. If gate fails, DO NOT declare done or silently publish broken production; continue fixing and report exact blocker.

## Release decision reporting

For every test record: `game/route | canonical SHA | test build/URL | viewport/device | real inputs performed | observed score/win/loss/restart | screenshot/log | PASS/FAIL/BLOCKED/NOT_RUN | priority | fix commit`.

Finish target is NOT just merges. It is one source, all advertised playable games genuinely good on phones, valid identity/multiplayer status, and production deployment verified on the public URL. Build green is insufficient.
