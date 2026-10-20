# NIGHT Play Agent 6 — strategy integration and QA handoff

Branch: `feature/night-play-agent6-board-cards-20261020`; PR #409. Base preview integration: `feature/party-online-rooms-20261019`. Created 20 October 2026.

## Implemented
- Local Chess: `/strategy/` (HTML, CSS, game controller); chess.js v0.10.3 is vendored under its BSD-2-Clause license to enforce *complete* legal chess moves, checks, castling, en passant, promotion, stalemate, repetition and draw conditions. Computer black uses a two-ply tactical search; a separate two-human **local** mode uses one device. Save/resume is by replaying UCI move history so castling and repetition remain valid.
- Draughts: `/club/?game=checkers` has two **local** modes, CPU black and two-human pass-and-play, with explicit mode labels. English draughts rules with forced capture, chained jumps and king crowning; 275ms GPU-only piece flights.
- WHOT: `/club/?game=whot` retains traditional 54-card deck, compact CPU stack, Nigerian house rules, immediately visible winner and fast animated dealing/calls. CPU Normal/Hard now considers suit retention, card actions, finishing restrictions and opponent hand size.

## Tests and reproducible execution
`node night-play/strategy/logic-test.mjs`: validates opening legal moves, illegal moves, checkmate, undo, en passant, castling, underpromotion, stalemate and insufficient material.
`node night-play/club/mobile-smoke.mjs`: runs Chess rules test first, plays live Chess vs CPU and Scholar's Mate with real browser clicks, tests save/reload and 320/360/390/412/900px responsive layout; plays Draughts against a true CPU with mouse/touch page interactions and local-mode switch. Captures screenshots under `night-play/visual-output/`.
`node night-play/club/whot-winner-smoke.cjs`: validates compact CPU, winning overlays and fast actual Web Animations API card flips. Other baseline club rules tests must continue passing.

**Verified on earlier feature commits:** NIGHT Play Preview CI **39350727836** passed the complete interactive club/strategy Chromium suite (including real Chess move to CPU reply and checkmate, real Checkers bot move); all three WHOT workflows passed on subsequent commits. I opened the actual CI screenshot artifact and visually reviewed Chess at 320px and 900px, Checkers after the CPU moved, and WHOT 20's 3+2 picker. This detected a genuinely clipped WHOT picker, now fixed with centered fixed-position touch targets and explicit visual bounds assertions for 320/390/412px.

**External blocking QA failures, not Agent 6:** Shared Preview CI run **39351757225** failed after Club and Chess tests, at Fruit Slicing `night-play/action/mobile-smoke.mjs:71` (real swipe failed to slice). Run **39351931592** failed at Fruit QA `night-play/qa/gameplay-gate.mjs:102` (real touch crossing did not score ten). Agent 2 owns Fruit Slicing; coordinator should re-run integrated preview when that game is fixed.

**Still not done:** Final HEAD workflow result and combined production deployment remain unverified; actual hardware touch performance and independent two-device multiplayer remain outstanding. All applicable final HEAD tests should run again after coordinator merges. WHOT earlier recenter-speed regression and in-flight ghost element count were repaired. Do not mark overall DONE from earlier green feature-only checks.

## Coordinator integration requirements
- Update `night-play/vercel.json` with two rewrites: `/strategy` and `/strategy/` → `/strategy/index.html` (this shared file is coordinator-owned).
- Replace existing Chess **web planned** catalog placeholder with a real link to `/strategy/` **after** testing, and adjust Checkers card copy from local-only to **CPU or two-player local**.
- Do not silently claim online matches: current Chess, WHOT and Checkers are local-browser matches. Shared online transport and invitation/reconnect require Agent 8.

## Agent 8 online protocol boundary (future, not shipped)
- Server controls authoritative game state, player identity and turn permissions; *never* accept client-chosen full board snapshots. Send an immutable room ID, versioned move `{from,to,promotion}`, stable participant ID and expected sequence/version; validate each move server-side with chess.js (or equivalent legal rules) before broadcasting.
- Chess UI's local rules and rendering are in `strategy/game.js`, with read-only `window.__nightChessQA()` diagnostics; separate network adapter must replace the in-memory move executor when using real remote rooms.
- Draughts: a remote adapter should synchronize full legal move sequences including multi-capture turns; `checkerApply` should receive server-validated turn transactions. Never reuse the one-phone local state to simulate remote multiplayer.
- Invite links, remote player identities, reconnection, seat permissions and game resumes must be verified with **two independent browser sessions** before any online tile appears.

## Files owned by Agent 6
`night-play/club/games.js`, `night-play/club/style.css`, `night-play/club/mobile-smoke.mjs`, `night-play/club/whot-winner-smoke.cjs`, and `night-play/strategy/*`.

Do not rewrite the shared catalog or backend while other agents own those surfaces. The coordinator must review PR #409 for conflicts with earlier club work and keep the centralized backlog accurate.
