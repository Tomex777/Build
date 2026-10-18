# WHOT reference study — 16 October 2026

This is a **source-level investigation**, not permission to import another game's assets. All reference repositories remain in their original locations, and this branch contains **no copied third-party application code or artwork**. GitHub Actions clones the public sources transiently for repeatable inspection.

## GitHub apps/games examined

| Reference | Source | Animation observations | Decision |
| --- | --- | --- | --- |
| **AkpetiTrust/whot** (React Whot; online and vs CPU) | https://github.com/AkpetiTrust/whot | The React app wraps the board in a `Flipper` and each card in `Flipped` (`react-flip-toolkit`), so a card keeps its visual identity as game state changes. Faces rotate with CSS `perspective:500px` and `rotateY(180deg)` over 600ms. CPU starts its next move after ~500ms. Game-over confetti. | **Best real UI/animation reference.** Adopt layout continuity/FLIP concept (independent implementation). *Our* target interaction is much quicker: 275–330ms flights and 150ms CPU pause. Source has no `LICENSE` file; don't copy application assets/code. |
| **Victor-Abidoye/Whot** (jQuery UI browser Whot) | https://github.com/Victor-Abidoye/Whot | Overlapping cards/fan, upward hover that spreads adjacent cards, drag/drop interactions, 2-second `rolling` flip. | Useful physical hand idea; 2-second flips are too slow for NIGHT Play. No repo license file visible. |
| **aekimena/Naija-whot-game** (vanilla JS Whot) | https://github.com/aekimena/Naija-whot-game | Horizontal hand-strip with scrolling; green/red feedback classes; repeated DOM rebuilding after card moves; basic 500ms transform transition on scroll container. | Real game, but UI movement isn't polished. Avoid full DOM rebuild if it causes ghosting or touch target jumps. No explicit license located. |
| **Riocodex/whot** (vanilla JS) | https://github.com/Riocodex/whot | Uses image assets and sound. Moves image DOM nodes between hands/market; CPU waits 1000ms. No real card-flight animations in the main JS. | Sound cue inspiration only; 1-second CPU pause is too long. No explicit license located. |
| **CodebyOmar/whot-app** (Vue) | https://github.com/CodebyOmar/whot-app | Play view only imports a layout; no gameplay motion engine to study there. | Not a useful animation benchmark. |
| **mykeels/whot** (MIT-licensed TypeScript engine) | https://github.com/mykeels/whot | A real rules/game API; not a visual game. | Good independent rules reference, not motion inspiration. |

## What NIGHT Play should actually do

- Fast composited card flight, with a **real source and real final destination**. A card drawn from the market must arrive at its own **new** hand slot; not appear to fly onto the last existing card while another card pops into existence afterward.
- True FLIP positioning for remaining cards when a card is played or drawn, preserving centered and tappable cards.
- No blocked interaction caused by an artificially long flourish. Motion duration is currently **300ms play / 330ms CPU / 275ms draw / 150ms pre-CPU**, plus quick hand-settle.
- A single meaningful flip when a card goes from back to face. Avoid long double-animations on market draws.
- Keep winner inside the table, with animation ending promptly and rematch immediately actionable.
- Do **not** import third-party images, sounds, UI code or branding without an adequate license/permission.

## Source method

Read each repository's original gameplay/UI/CSS files; inspect release records before offering an APK. No examined Whot-specific repo above supplied a GitHub Release APK as of this study. Existing game-web-app source can be downloaded as a ZIP or cloned, but a source repository isn't automatically an installable Android APK. A separate GitHub Action clones a few small public sources in a throwaway runner, runs source checks, writes a summary to the workflow log and deletes the clones with the runner. No materials are copied into NIGHT Play's shipped files.

## Visual-parity update (16 October)

We built the upstream React app only in a temporary GitHub Actions runner and
measured the *actual rendered gameplay* after its preloader finished.

| Element | Source mobile 390×844 | Source desktop 1280×844 |
| --- | --- | --- |
| Opponent 5-card hand | x30 y80, 330px wide | x315 y60, 650px wide |
| Market back | x72.5 y222, 80×105 | x465 y263, 100×138 |
| Face-up played pile | x237.5 y222, 80×105 | x715 y263, 100×138 |
| Player hand | x30 y357, 330px wide | x315 y451, 650px wide |
| Card appearance | Flat ivory, solid wine shapes, black small ranks | Same at larger dimensions |
| Hand scrolling | Horizontal, partially visible next card | Horizontal within 650px |

NIGHT's combined-preview WHOT now renders these positions within 2.5 CSS
pixels, including five actual face-down CPU cards, two independently aligned
market/played cards, and a horizontal player hand. Small remaining hands center
themselves as a NIGHT accessibility enhancement. The woodgrain is our own
procedural SVG, rather than the upstream game's unlicensed photographic asset.
The 3D turning-card and hand-recentering animations are independent, including
more continuous shared-position FLIP movement and an overlapping 560ms turn.
No original React game or artwork is embedded on NIGHT Play.

The earlier comparison lab has been removed. The original runs only in temporary
reference QA under `.github/workflows/night-play-whot-visual.yml`.

## Approved phone-performance direction — 18 October 2026 (supersedes visual row parity)

The user explicitly rejected the old full-width CPU row and slow, choppy card
animations. **Do not re-introduce those behaviors just to match the reference.**

- Render at most **three** overlapping CPU card backs. Hide the two partially
  covered backs' lettering so chopped-up words never appear. Keep the *real*
  CPU card count in the existing match HUD; hands may be much larger.
- Player cards remain individually identifiable. Only the CPU's concealed
  cards are visually stacked.
- Motion budget now targets **185ms player, 205ms CPU, 150ms market draw**,
  85ms CPU reaction, and 150ms flipping, not the source's slow 560–600ms
  turns. A short perceptible animation beats theatrical transitions.
- Deal multi-card penalties one card at a time into actual hand slots.
  Do not create three simultaneous 3D travelling card layers on mobile GPUs.
  Avoid extra FLIP recenter layers when the player's hand exceeds five cards.
- Keep custom NIGHT SVG card faces, original tabletop, strict Nigerian WHOT
  rules, user preference for sound effects, and real CPU-play animation.
- Automated regression checks test a large CPU hand's compact stack,
  exact HUD count, no overflow, shortened duration caps and a maximum of
  **one** simultaneously travelling card during Pick Three.
- CI/mobile viewport tests validate correctness and synthetic performance
  constraints. Real Android listening and frame-pacing review remains useful.
