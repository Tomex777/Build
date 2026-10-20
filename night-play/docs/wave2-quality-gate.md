# NIGHT Play — Wave 2 quality gate (no fake DONE)

**Integration source:** `feature/party-online-rooms-20261019`, first-wave ten-agent code combined via merged PR #436. **Live coordination:** [issue #459](https://github.com/Tomex777/Build/issues/459). **Independent release gate:** A10. **Release & deployment owner:** A1. Do not promote an old alias or protected MSCC backend without user authorization.

## One measurable pass/fail record for EVERY playable game

Actual Playwright/CDP touch and keyboard/mouse execution on a browser build identified by **exact Git SHA**. For each game record its route and steps to: start; perform actual meaningful inputs; encounter challenge; score; collide/hit/lose; win or finish if applicable; restart; replay with improved difficulty; verify UI legibility and touch tracking; save/resume where applicable. Capture screenshot/recording + console/uncaught exceptions + viewport and browser version. **If a scenario is not run, record NOT TESTED, not PASS.**

Supported viewport minimum: 320, 360, 390, 412 phone widths and 1280 desktop; verify Android phone touch separately when accessible. Test at least one **long session (three minutes or multiple full stages)** for each fast-action game and **full games** for board/card titles. Check frame pacing, input latency, score integrity, low-end device responsiveness, clipping, and accessibility/reduced motion.

### Target areas and unacceptable shortcuts

| Feature | Minimum functional quality |
| --- | --- |
| Snake Xenzia | First valid touch swipe registers, rapid legal queued swipes execute in order; no random double-swipe bug; cannot reverse 180°, clear death/restart |
| Fruit Slice | Fruit arcs reach usable upper area; true intersection-only scoring; real split-half trajectory for different cut angles; misses/lives/combo and difficulty work |
| Endless Runner / Sky Jumper | Fair hazards and hitboxes; needed jump/duck; platforms stable; escalating but survivable challenge; real defeat/restart |
| Brick Breaker / Shooter / Wing Hop | Multiple progressively harder stages, responsive controls and visually grounded collision, game-over and restart, meaningful skill |
| Jigsaw | 24/48/144 genuine pieces, responsive board, scattered pieces, stable resume; no whole-canvas redraw stutters; co-op only labeled online with server proof |
| Memory Match / 2048 | Real rules, no phantom matches/moves, responsive board, real win/loss and persistence |
| WHOT / Chess / Checkers | Correct game rules and legal moves; smooth opponent hand/CPU animation; **real** AI bot or honest local two-player; separate server verification for online |
| Piano Tiles / Maze Chase | Timed four-lane notes, melody and misses/holds; real maze ghosts/pellets/lives, progressive difficulty |
| NIGHT + NOMI multiplayer | Host with verified WhatsApp identity, shared invite room, >=2 remote guests/independent browsers with server state, reconnection and authoritative profile name changes; no false online claims |
| General site | Professional responsive UI, original SVG assets/thumbnails, no redundant controls, no broken menu links/overflow, locked Channels unaffected, appropriate SFX/no unrelated music |

## Release decisions

- **P0:** crash, blank route, game not playable, incorrect multiplayer authority, broken save/identity, touch frequently lost, security/privacy problem. Block promotion.
- **P1:** serious animation lag, trivially broken difficulty, unfair collision, unfinished graphics, bad mobile overflow. Fix before declaring polished.
- **P2:** nonessential additional games, decorative redesigns and optional flair **after P0/P1**.

A1 must report **source integrated**, **preview deployed**, **independently interacted**, **owner approved** separately. A green build/PR is an intermediate milestone, not completion. Neither chats nor GitHub issues create an always-on autonomous process.