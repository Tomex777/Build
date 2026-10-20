# NIGHT Play — Agent 3 runners/platforms handoff
Updated: 2026-10-20

- **Owner:** Agent 3 — Sky Jumper, Endless Runner, Doodle Jump-style mechanics.
- **Repository:** Tomex777/Build, NIGHT Play source in `night-play/` (not repository `main`).
- **Integration base:** `feature/party-online-rooms-20261019` / PR #386.
- **Isolated branch:** `feature/night-play-agent3-runners-20261020`.
- **Implementation PR:** #414 — https://github.com/Tomex777/Build/pull/414.
- **Files owned/touched:** `night-play/action/games.js` (only jump and runner engines plus their shared pointer handlers), `night-play/action/long-play.mjs`, `night-play/action/agent3-play.mjs`.
- **Dependencies/conflicts:** Agents 2 and 4 may also edit `action/games.js` for fruit/slicing and bricks/shooter. Merge only the independent ranges, preserve all game engines, and replay all five action games. Coordinator owns common navigation/AGENTS.md/backlog, not this agent.
- **Completed source changes:** Existing Sky Jumper is the Doodle-style game, so no copy was added. First five landing platforms are stationary and wide; first-step spacing softened; bounce and keyboard steering tuned; later moving platforms kept. No disappearing platform/TTL or rising storm; no duplicate height HUD. Runner now has proper tap/up-swipe jump, down-swipe temporary slide, hold-to-slide, swept obstacle crossing checks, barrel variety, animated wings and sprint, preserved low-flyer/glider consequences, progressive acceleration.
- **Simulation verified:** Full modified JS syntax compiled in V8. Extracted real runner engine stepped at 60fps: idle flyer death PASS, timed jumper survives flyer PASS, sliding into low flyer dies PASS, upright player hit by high glider PASS, ducking player clears glider PASS, barrel collision PASS. Extracted Sky Jumper engine: seeds 0.18, 0.5, 0.82 all bounced 18+ times in 16 seconds, climbed 1,375–1,570 height units with deterministic assisted steering, and did not die. Tests are deterministic engine simulations, not visual browser proof.
- **Browser test added:** `node night-play/action/agent3-play.mjs`; runs real Chrome browser with swipe/tap through touch events, keyboard collisions, game-over/restart and screenshots at 320/360/390/412/1280 widths. This is invoked via `night-play/action/long-play.mjs` in the existing NIGHT Play Preview CI workflow.
- **Build/CI:** GitHub Actions run 39349868187 was triggered by PR #414. Update this line with its final result; a running job is not a pass.
- **Not yet complete:** inspect all generated screenshot PNG/JPG artifacts, personally play mobile/desktop interactions with real Chromium, fix any failed browser tests and confirm integration into combined preview. Do not mark DONE before all gates pass.
- **Next executable actions:** open PR #414 → inspect Night Play Preview CI run 39349868187; inspect step named “Actually play the jumping game with touch movement for extended rounds” (includes Agent 3 suite). Download `night-play-mobile-design-review` screenshots if upload succeeded. If tests fail, patch same agent branch and rerun; coordinate action/games.js merge with other owners. Do not claim deployed.
