# NIGHT Play — handcrafted visual system (V8)

This pass changes **presentation only**. The eleven game tiles and their SVG artwork are not a claim that all eleven games work: only Snake, 2048 and Tic-Tac-Toe are playable in this prototype. Chess, Ludo, Checkers and the remaining games retain their existing planned/WhatsApp statuses. Never change this contract by altering a card label.

## What a screen should feel like
- Bright, warm neutral base (`#f7f7f7`); deep forest / charcoal text and generous whitespace. The welcome page has one purpose: help someone enter.
- No fabricated moon or owl brand mark, glossy 3D, fake floating chips, excessive gradients, emoji-as-artwork, or ornamental motion.
- One intentional illustrated hero; a clear Game Library with large, distinct custom SVG board/arcade illustrations; the dashboard highlights a single main action. Section introductions can be simple ruled columns instead of another stack of cards.
- Every interactive icon is a genuine line-drawn SVG, not a font-dependent Unicode glyph. New game art should be authored as a reusable local `<symbol>`, with contrast and the original game palette. Avoid third-party image requests.
- SVG art is flat, static and directly tied to gameplay: grid/trail for Snake, numeric tiles for 2048, game board for Tic-Tac-Toe, and similarly legible objects for upcoming titles.
- Use cards only for self-contained activities that warrant a hit area. No needless nested cards. Type scale and focus states should carry hierarchy.
- Animation is for feedback: gentle hover within the artwork (~200 ms), never constant floating. Respect `prefers-reduced-motion`.

## Per-game personality
Snake's game canvas uses a green physical-board border; 2048 uses warm stone tile colors; Tic-Tac-Toe uses cool blue/ink squares. Per-game styles are selected with `#gameDialog[data-game]` and must not affect game engine state. All controls remain touch-safe.

## Real-data and safety contract
Profile numbers are derived from *device-local* activity only, no fake ranks, completed rounds or linked WhatsApp identity. Keep a single global Connect WhatsApp action; the modal must clearly indicate account linking is not live. The existing WhatsApp commands and production NIGHT Play remain untouched.

## Visual quality checks
- At mobile widths 390 and 360 pixels, no horizontal clipping or cramped labels; bottom navigation uses labeled SVG icons.
- At desktop widths, cards lay out evenly, hero art is genuinely flat, and section borders do not look like nested boxed components.
- Use browser screenshot review to check visual balance, legibility, colors, and intrinsic SVG sizing. Automated smoke verifies 11 visible SVG artworks, tab icons, no floating labels, flat hero, and playable games.
