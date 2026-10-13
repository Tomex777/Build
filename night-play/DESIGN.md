# NIGHT Play — handcrafted visual system (V11)

This release prioritizes real visual playability and also changes Snake speed settings and its rendering path. The eleven game tiles and their SVG artwork are not a claim that all eleven games work: only Snake, 2048 and Tic-Tac-Toe are playable in this prototype. Chess, Ludo, Checkers and the remaining games retain their existing planned/WhatsApp statuses. Never change this contract by altering a card label.

## What a screen should feel like
- Bright, warm neutral base (`#f7f7f7`); deep forest / charcoal text and generous whitespace. The welcome page has one purpose: help someone enter.
- **Official NIGHT logo only:** the horizon/sunrise semicircle with a horizontal bar below it, taken from `whatsapp-ai-android/app/src/main/res/drawable/ic_night.xml` in `Tomex777/Build` (`night-player-final-ci` branch). Do not fabricate new brand marks. No glossy 3D, fake floating chips, excessive gradients, emoji-as-artwork, or ornamental motion.
- One intentional illustrated hero; a clear Game Library with large, distinct custom SVG board/arcade illustrations; the dashboard highlights a single main action. Section introductions can be simple ruled columns instead of another stack of cards.
- Every interactive icon is a genuine line-drawn SVG, not a font-dependent Unicode glyph. New game art should be authored as a reusable local `<symbol>`, with contrast and the original game palette. Avoid third-party image requests.
- SVG art is flat, static and directly tied to gameplay: grid/trail for Snake, numeric tiles for 2048, game board for Tic-Tac-Toe, and similarly legible objects for upcoming titles.
- Use cards only for self-contained activities that warrant a hit area. No needless nested cards. Type scale and focus states should carry hierarchy.
- Animation is for feedback: gentle hover within the artwork (~200 ms), never constant floating. Respect `prefers-reduced-motion`.

## Per-game personality
Snake is a **Canvas-rendered 18 × 18 board**, with direct tick-by-tick movement. Never switch to 60fps interpolated rendering without explicit user approval: V9 made the game feel slower and less responsive. Presets: Relaxed 195ms, Classic 115ms, Fast 85ms, Turbo 66ms. No on-screen D-pad; swiping and keyboard only. Snake's game canvas uses a green physical-board border; 2048 uses warm stone tile colors; Tic-Tac-Toe uses cool blue/ink squares. Per-game styles are selected with `#gameDialog[data-skin]` and must not affect game engine state. All controls remain touch-safe.

## Real-data and safety contract
Profile numbers are derived from *device-local* activity only, no fake ranks, completed rounds or linked WhatsApp identity. Keep a single global Connect WhatsApp action; the modal must clearly indicate account linking is not live. The existing WhatsApp commands and production NIGHT Play remain untouched.

## Visual quality checks
- At mobile widths 390 and 360 pixels, no horizontal clipping or cramped labels; bottom navigation uses labeled SVG icons. At 390 × 844 px, Snake/2048/Tic-Tac-Toe boards should measure **at least 376 px** wide and remain within ~30 px of the actual vertical viewport midpoint. Avoid transformed wrappers that cover the settings controls.
- The focus toggle can enter browser fullscreen (when supported), with a CSS focus layout fallback, and has a reversible pressed state. Game settings/back remain accessible.
- At desktop widths, cards lay out evenly, hero art is genuinely flat, and section borders do not look like nested boxed components.
- GitHub CI captures real Chromium 390 × 844 px JPEG screenshots of the library and Snake/2048/TTT, attached to each run as the `night-play-mobile-design-review` artifact (14-day retention). Review the actual boards, logo, positioning, card art, legibility, colors and controls rather than inferring quality from the existence of SVGs. Automated smoke verifies 11 visible SVG artworks, tab icons, no floating labels, flat hero, and playable games.

## V11 — input responsiveness and fixed-focus arena
- Touch controls must respond on **pointermove**, once a directional threshold is crossed (9 px Snake; 12 px 2048). Do not wait for touchend. Keep a release-event fallback for very fast flicks and capture pointer interaction on the board to avoid scrolling.
- Snake accepts up to two legal directional changes before the next tick, checked against the most recently queued direction. The game still moves **only on grid ticks**, at Classic 115 ms and Turbo 66 ms; do not reintroduce 60 fps interpolation or draw a whole extra frame per input.
- Gameplay title is always at the **top** directly beneath the global game navigation (approximately y=61 on a 390×844 mobile viewport) while the actual square board stays visually near the viewport's center (roughly y=415 center vs y=422 screen midpoint).
- Focus **must not enlarge or stretch** any board. Capture the current board's displayed outer width before invoking fullscreen; clamp the focused arena to that original size. Snake's measured board includes its green frame — measure `.snake-shell`, never the smaller internal canvas.
- Visually review both regular and Focus screenshots for all three games. The CI browser test now sends **real Chromium touch events while fingers remain down**, ensuring Snake and 2048 react before touchend. Original NIGHT horizon mark remains unchanged.
