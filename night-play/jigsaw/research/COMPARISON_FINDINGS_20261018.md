# NIGHT Play Jigsaw vs PlayJigsaw.net — live gameplay & placement audit

Date: 2026-10-18. Source: actual PlayJigsaw.net production puzzle **Orchard Goat Sheltered Edge** (36 pieces) compared with the merged NIGHT Play Jigsaw (24 pieces, Alpine Lake photo), at 390×844 mobile and 1280×840 desktop. Headless Chrome navigated both, performed drag attempts, opened help/tools, and captured screenshots and visible DOM positions. Screenshots and full JSON are in GitHub Actions run **39131532178**, artifact `playjigsaw-vs-night-side-by-side-screenshots`.

## Outcome

**PlayJigsaw wins overall, decisively, for immersive game layout and tool orchestration.** NIGHT Play retains advantages in real-life photograph sourcing and user-facing, explicit hint limits, and passed three targeted placement drags per viewport. Different puzzles and piece counts mean this is not an apples-to-apples speed or difficulty benchmark. Automated audio capture was not performed; no claim about perceptual SFX quality is made.

| Measured first-play state | PlayJigsaw production | NIGHT Play | Consequence |
| --- | --- | --- | --- |
| Mobile visible canvas coverage, 390×844 | 91.7% | 78.2% | NIGHT loses ~13.5 percentage points to game chrome |
| Desktop visible canvas coverage, 1280×840 | 92.0% | 75.2% | NIGHT loses ~16.8 points |
| Mobile top edge of canvas | 65px | 169px | 104 extra pixels before the NIGHT tabletop |
| Desktop top edge of canvas | 65px | 201px | 136 extra pixels before the NIGHT tabletop |
| Desktop page height | 840px | 894px | NIGHT extends 54px below the fold; pieces clipped unless scrolled |
| Mobile page height | 844px | 867px | NIGHT creates avoidable page scrolling |
| Game top controls | Compact ~65px single rail | Three horizontal rows (global header, statistics, tools) | NIGHT is fragmented and cramped |
| Status | Auto-save badge at bottom-left, clock/pieces bottom-right | Time/moves/pieces consume a separate top row | Move status out of the playing-area allocation |
| Reference picture | Mobile Action Menu preview + live translucent reference guide control | Timed modal peek; guide in Settings | Original allows referencing without leaving the canvas |
| Hint | Top lightbulb; test showed auto-placement of 2 pieces | Top Hint; test added 1 placed piece | Different behavior; need deliberately choose balance |
| Phone sound | Enabled via Action Menu | Buried in Settings | Audio access less immediate in NIGHT |
| Desktop controls | Edges, Preview overlay, live guide, Hint, Sweep, Sound, Help, Background, Zoom, Pause, Fullscreen, Reset | Edges, Sweep, Preview, Hint, Zoom, Pictures, Fullscreen; Settings elsewhere | Original has more integrated functions |
| Difficulty selection | 36–400 pieces | 6–144 | Reference has high-end progression and optional rotation |
| Piece drag | 3 visible relocations on desktop | 3 successful target drops on desktop, 3 on touch | Both respond; reference mobile touch attempts weren't visually confirmed |

### What is strong about the reference
- Responsive canvas truly runs edge-to-edge beneath **one** extremely compact game rail, even on a 390px phone.
- Main elements do not fight for space: title/back, sparse essential help icons at the top; seconds, piece count and save status at bottom overlay positions.
- Layered cardboard-style edge and ambient matte look gives tactile contrast.
- Tool hierarchy: desktop shows a full row of vector icons; mobile hides secondary functions in a single action menu.
- Translucent reference directly on playing surface keeps spatial context while looking at the guide.
- Reset and Fullscreen remain accessible without a second branded website header.

### Most important NIGHT shortcomings
1. Redundant app chrome: back/brand/settings row, separate picture/time/moves/pieces row, separate edges/sweep/preview/hint/zoom row. Desktop canvas y=201; on phone y=169.
2. Height calculation lets desktop canvas bottom extend beyond viewport (bottom loose pieces cut off); mobile page also scrolls.
3. Main mat color and edge effect feel flat compared with production reference: pieces don't pop enough.
4. Zoom and hint occupy scarce permanent toolbar space on phones, while sound/pause/reference-guide aren't readily accessible.
5. Current timed picture peek is modal while reference supports in-place overlay and a dedicated translucent background guide.
6. At higher difficulties scattered piece zones become dense and overlaps impede visibility. A larger adaptive tabletop and proper scalable panning are needed.

## Proposed NIGHT Play placement (do not clone the reference brand)

### Mobile, portrait
**One 52–58px game HUD:** Back | Edges SVG | Preview SVG (remaining count as small badge) | Hint SVG (remaining count) | Sweep SVG | Menu SVG.
Menu: picture selection, sound on/off, background/guide toggle, pause/resume, fullscreen, restart, help and optional zoom buttons. Do **not** reintroduce duplicate actions or a second game settings surface. For accessible low-vision use 44px tap targets; if there is not enough room, move Sweep under Menu instead of squeezing targets.
**Tabletop:** from just below this one rail to the viewport bottom with all off-board pieces reachable without page scrolling; resize on orientation changes. Timer/placed/total and save feedback overlaid near bottom corners with unobtrusive semi-opaque chips. Keep touch pinch-to-zoom and pan, avoid permanent triple-button zoom strip on compact phones.
The picture should stay visually central between loose pieces; solve board edges not confused with surrounding workspace.

### Desktop
**One 56–64px rail** combining back/title, edges, preview, guide, hint, sweep, sound, zoom, pause, fullscreen and context menu. Avoid the double website header and secondary stats bar.
**Tabletop** fills the remaining viewport, no document scrolling. Controls never cover actual draggable puzzle pixels.

### Interaction
- Replace interruptive peek dialog during play with an in-canvas temporary preview overlay, keeping the limited-use rule; retain optional faded background guide **off by default**.
- Consider rotation and an extra difficulty bracket later; complete free cluster interactions, drag precision and visibility first.
- Ensure a single sound toggle is discoverable without modal navigation. Quality should be tested with actual listening, not inferred from generated WebAudio code.
- Preserve 14 real photographic images and private custom uploads.

### Acceptance tests for next implementation
- At viewport 390×844 and 1280×840, puzzle canvas starts below at most **64px** of HUD; visible canvas area **>=88%** of viewport; document scrollWidth<=viewport and scrollHeight<=viewport during play.
- At 320px width, all always-visible essential control targets remain >=40–44px and are never cut off.
- Confirm mouse and touch drags without synthetic test-only `place()`; grouped pieces preserve position on rotation and resize.
- Show real-photo mobile and desktop screenshots, measure empty/background area and verify no piece disappears behind the tool rail.
- Verify pause, sound, preview/hint consumption, guide, restart, fullscreen, save/reload, and memory-limited modes.
- A/B audio listen for fatigue and sharpness during at least 10+ minutes; do not assume that the generated tactile sounds match PlayJigsaw by merely inspecting code.
