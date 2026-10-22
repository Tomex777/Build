# Endless — parallel-agent implementation contract (2026-10-22)

**Canonical repository:** `Tomex777/Build` · **integration base:** `endless-android-ci` · **current realism candidate:** PR #565 (`feature/endless-terrain-lighting-20261022`). Read this file and the latest open Endless PRs before changing code.

## Definition of done (not “it builds”)
Endless is an Android 8+ (minSdk 26) **scientifically grounded, visually credible** solar-system explorer. All existing worlds must remain traversable/navigable; photorealism requires real imagery where properly licensed, consistent lighting and scale, believable landings and responsive motion. No chunky geometry, flat color balls, synthetic planetary photos passed off as observations, or fake promises of physics/surface accuracy. Never describe generated meter-scale terrain on Europa, Titan, Mercury or Ceres as measured NASA terrain. Gas giants have no walkable solid surface: show cloud-top or atmospheric flybys, not a fake landing.

## Parallel ownership: avoid touching another agent's files
1. **Agent 1 — gas giant rendering/science:** Jupiter/Saturn/outer gas giant appearance, oblate geometry, camera-phase readability, ring occlusion, sunward lighting, source attribution, regression tests. Own focused renderer shader/geometry sections, `PlanetShape`-adjacent helpers and gas-giant tests. **Do not redesign controls or extended-world terrain.**
2. **Agent 2 — walkable world realism:** scientifically labeled terrain textures/DEM where available, near-field geometry, surface illumination/shadows, atmospheric haze and terrain seams; six walkable scenes and their tests. Coordinate any renderer edits at function/hunk level.
3. **Agent 3 — controls and experience:** ABR-compliant joystick and camera/look inputs, landing/takeoff navigation, low-end Android performance, responsive UI for multiple screen sizes, focus/accessibility, lifecycle safety. **Do not rewrite shaders or surface terrain.**
4. **Integration / QA owner:** reconcile PRs with latest base after each agent's unit tests, verify full Android CI, review *actual* emulator screenshots at multiple devices and API26/36, check signed output provenance and scientific claims, catch regressions. Do not rubber-stamp.

## Work protocol
- The user requested a **40-minute working session** per agent unless the app is completed or the session ends earlier; actively implement, test and fix, not merely monitor actions. Do not claim to have spent 40 minutes unless actual elapsed time confirms it. Do not fake background execution.
- Work from the current integration candidate if needed, on a **unique agent-specific branch**. Open a dedicated PR against the branch it actually builds upon; do not concurrently edit one branch or directly overwrite another agent's work.
- Ship functionality + tests + clear review notes. Don't stop at the first fix, first green unit test or a screenshot placeholder. Fix regressions and continue through the available session.
- Screenshots are accepted as evidence only when captured by an installed Android emulator/device from the current tested SHA. Keep raw images, angles and lighting; a contact sheet may downscale but not fake/enhance.
- Performance: GLES3, no mandatory high VRAM, bounded texture allocations and graceful downsampling. Verify no shader compilation warnings/errors on API26 as well as API36.
- ABR: minSdk 26, no redundant controls or filler copy, polished vector iconography, custom input styling, hide status bar in fullscreen, verify install/runtime rather than treating compilation as release.
- CI **release gate:** pass all unit/lint/build checks, API36 runtime and eight-world visual checks, API26 smoke, actual signing verification, and owner review of raw screenshots from the exact head. A queued/cancelled workflow is **not** a passing run.

## Known next checks
- PR #565 changes sun-aligned terrain shadows, NASA Trek bilinear reprojection and Jupiter/Saturn daylight flyby cameras, but its 2026-10-22 head workflow **was cancelled** as of agent coordination; do not mark it merged/released based on previous passing heads.
- Inspect Saturn's ring shadow against the true oblate/tilted ellipsoid, not a spherical projected disk. Inspect outer giant texture clarity in direct solar phase.
- Inspect the eight-world contact sheet, then full-resolution captures, for blown highlights, black disks, seams, color casting, horizon depth, and HUD obstruction. Statistical pixel checks alone cannot certify photorealism.

## Agent communication
Each PR description must name: files changed, checks genuinely run (or not), screenshot/run URLs and observed defects, remaining blockers, and agent ownership. Leave untouched work for other agents. Preserve physical/scientific caveats rather than hiding them.
