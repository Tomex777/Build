# Endless — Agent Coordination & Photorealism Delivery Rules

**Project:** NIGHT / Endless (native Android planetary exploration)  
**Updated:** 2026-10-22  
**Canonical repository:** Tomex777/Build  
**Integration branch:** `endless-android-ci`  
**App overrides:** `endless_m2_override/app/`  
**CI, tools and actual visual checks:** `.github/workflows/endless-android.yml` and `endless_ci/`  
**Status:** Active development. **NOT photorealistic or complete.** This document coordinates the lead plus three additional agents.

> **All Endless agents: read this file before changing code.** Work in separate branches and PRs. Do not overwrite another agent's files, declare success from compilation alone, or report a blocking build queue as the end of work.

## 1. Objective and nonnegotiable visual standard

The goal is an explorable solar system whose **real Android screenshots can plausibly be mistaken for spacecraft/planetary photography**, within transparent device-performance constraints. Visuals must not be chunky, cartoonish, uniformly smooth, obvious heightmaps, texture-wrapped balls, or brown/blue filters. Preserve scientifically defensible proportions, illumination, planetary materials, and locations. **Never claim photorealism until actual Android captures demonstrate it.**

### Existing visual defects; treat as acceptance blockers

- **Mars:** Smooth/repetitive terrain, inadequate geological variation and distant relief.
- **Moon:** Elevation exists but regolith, fractures, rocks, shadows and scale read as basic geometry.
- **Mercury/Ceres:** Similar grey synthetic terrains; not distinctive or sufficiently geologically grounded.
- **Europa:** Artificial ice; lacks convincing fracture networks, chaos terrain, ridges and color/material response.
- **Titan:** Atmospheric haze is a flat brown filter rather than convincing light transport and depth.
- **Jupiter:** Obvious textured sphere, cloud bands without real depth/motion; Great Red Spot lacks storm structure.
- **Saturn:** Too dark; rings thin, flat, insufficiently detailed or illuminated.
- **Uranus/Neptune:** Require credible cloud/haze scattering and correct coloration, not simplistic single-color spheres.

Accept improvements only against screenshots of the **actual running app** at consistent camera positions, along with performance measurements and honest remaining limitations.

## 2. Agent ownership — exactly four independent lanes

**LEAD / INTEGRATOR (current agent) — physics, scientific coherence, integration and final release.**

- Owns coordination, merging/cherry-picking, shared shader integration, orbital/navigation correctness, landing rules, planet scale, collision correctness, regression diagnosis, release signing and Android CI verification.
- Owns **shared central code** such as `EndlessRenderer.kt`, `CelestialBody.kt` and `KeplerEphemeris.kt` **unless a time-boxed, explicitly announced edit lock is granted to another agent**.
- Must keep all existing six-world landings, orbital flight, touch navigation and game functionality working.
- Main branch for landing reviewed changes is `endless-android-ci`. Merge only verified PR heads.
- Tracks visual comparison; no fabricated renders or AI-generated pictures presented as emulator screenshots.

**AGENT 1 — GAS GIANTS, STORMS, CLOUDS, RINGS.**

- Focus: Jupiter/Saturn/Uranus/Neptune orbital and close-view appearance. Include realistic oblate shape, sunlit phase, atmospheric limb, cloud layers, antialiased detail, reasonable differential band motion, Great Red Spot structure, Saturn ring density/optical depth, ring shadows, and polar features only when scientifically justifiable.
- Prefer **isolated new modules** (e.g. `GasGiantAtmosphere.kt`, `GasGiantShaders.kt`, `SaturnRingMaterial.kt` and independent tests) instead of concurrent edits to `EndlessRenderer.kt`. Provide a minimal documented integration patch or request the lead to perform the shared-file wiring.
- Relevant tests/scripts: `PlanetShapeTest.kt`, `SaturnRingIlluminationTest.kt`, `endless_ci/check_gas_giant_visuals.py`. Show both far and near frames for Jupiter/Saturn.
- Gas giants **have no conventional landable solid surface**. Optional atmospheric descent must use a probe/flight mechanic with increasing pressure, opacity and eventual failure, **not an imaginary rocky landing platform**. Treat simulated weather as illustrative unless derived from properly cited observations.
- Avoid large expensive full-screen fragment raymarch loops without scalable quality levels and benchmarking.

**AGENT 2 — ROCKY/ICY SURFACES, TERRAIN AND MATERIALS.**

- Focus: Mars, Moon, Mercury, Ceres, Europa, Titan. Improve multiscale relief, appropriate regolith/ice/rock materials, craters/ridges/fractures, geomorphology, atmospheric perspective where appropriate, realistic distant silhouette and terrain/rock transitions, and landing-site distinctiveness.
- Prefer terrain/material/asset paths (`MarsSurfaceTerrain.kt`, `MoonSurfaceTerrain.kt`, `ExtendedSurfaceTerrain.kt`, `ExtendedSurfaceMesh.kt`, `SurfaceMicroMaterial.kt`, `SurfaceMicroTexture.kt`, `MeasuredElevationPatch.kt`) and **new isolated helpers/tests**. Leave `EndlessRenderer.kt` and shared `SurfaceDetailRenderer.kt` / `SurfaceMaterialShaders.kt` integration to the lead if touched by another active PR.
- Identify which data are **NASA/ESA measured DEM or imagery** versus **procedurally invented terrain** in code and PR. Do not label fake terrain as an actual photographed location.
- Favor image-space and terrain LOD stability: no seams, floating features, grid repetition, abrupt near-field replacement or warped collision ground.
- Demonstrate two views per affected world: orbital/approach and on-foot. Test six-world landings and terrain collision after each integration.

**AGENT 3 — MOBILE CONTROLS, UI POLISH, PERFORMANCE AND QA.**

- Focus: ABR compliance, immersive fullscreen, usable movement stick and independent camera-look gestures, speed/damping, collision/landing transitions, lifecycle stability, graphics settings and Android performance; remove redundant buttons/labels and default stock-styled inputs.
- Prefer `MainActivity.kt`, `EndlessGLView.kt`, `SurfaceJoystickMath.kt`, `RendererFrameTiming.kt`, instrumentation and benchmark utilities; coordinate before editing shared rendering files.
- Controls requirement: **GTA-style virtual joystick for movement, separate right-side drag to look**, real multitouch, no tap/hold camera glitches, prevent stuck input after backgrounding, orientation or focus loss. Do not add a second control that does an existing control's job.
- Keep **minSdk 26 (Android 8)**, ARM64 usable, Android 16/API 36 validated. Measure frames and memory on emulator *and* a lower-tier device when available; provide reproducible test conditions.
- Visually inspect interface **in actual running app**, not static layouts alone.

Agents do not have permission to redefine their scope unilaterally; propose cross-cutting edits in PR descriptions and agree on a file owner first. The lead should **not** silently rewrite another agent's active work.

## 3. Shared work protocol: no conflicting branches

1. **Read current state** first: `endless-android-ci`, open PRs, HEAD SHA, the latest CI logs, this document, and the source you intend to touch.
2. **Known in-flight PR as of 2026-10-22:** [#565 — terrain lighting, gas-giant sunlit framing, landing/collision, visual sheet](https://github.com/Tomex777/Build/pull/565), head initially `878b3dd4c77476cffa1a20bc591a4bc35da3986e`. Its final-head CI was still running when this file was drafted. **Recheck**; do not assume merged or tested. It touches `EndlessRenderer.kt`, `EndlessGLView.kt`, `SurfaceDetailRenderer.kt`, `SurfaceMaterialShaders.kt`, terrain and workflow files. The lead owns completing/merging/fixing it.
3. **Branch from the latest `endless-android-ci`** for independent work. Suggested branch names: `feature/endless-gas-atmospheres-20261022`, `feature/endless-geology-materials-20261022`, `feature/endless-controls-qa-20261022`. If #565 merges after your branch cut, synchronize before requesting merge.
4. **Open a PR against `endless-android-ci`** that states: lane/owner, base+head commit IDs, changed files, before/after captures, verification run URL, known shortcomings, and any shared-file integration requests.
5. **Edit your owned files only** unless a shared-file lock is agreed in writing in the PR thread. Prefer additive implementation helpers and a small lead-owned integration diff.
6. **Communicate via GitHub PR comments / review threads**, not unsynchronized local chat assumptions. On discovering a regression, comment with an exact reproduction, stack/log/screenshot and SHA; claim ownership of the fix.
7. **Do not merge or force-push teammates' branches**. The lead merges sequentially after green CI, reviewing screenshots and file conflicts after *each* merge.
8. If tests/builds queue, **continue useful independent implementation, tests or source review**. Do not repeatedly poll and call it work. Do not bypass failed checks, hide errors or claim progress from an incomplete workflow.
9. Work continuously through the assigned session (user's target: **40 minutes**, or until the work is complete or the session ends). **No asynchronous/background-work claims and no invented elapsed-time reports.**

## 4. Build, visual and release acceptance gates

- Android app still **builds**, installs, launches and survives background/foreground transitions. Check **API 26 and API 36** with runtime smoke and input tests; preserve minSdk 26 and landscape/fullscreen.
- Use the repo's actual GitHub Actions `.github/workflows/endless-android.yml`. **Important:** branch push triggers may be allowlisted; a successful lint test is not equivalent to a complete CI run. When needed, explicitly dispatch workflow or request an appropriately scoped trigger update. Only a **completed success for the exact final head SHA** can satisfy the CI gate.
- Capture eight worlds: **Mars, Moon, Mercury, Ceres, Europa, Titan, Jupiter, Saturn**, plus Uranus/Neptune when those views are modified. Capture from the Android emulator/device itself.
- Compare meaningful view angles, scales, terminators, exposure, shadows, horizon/ground seams, vegetation-free body materials, ring occlusion and apparent atmospheric depth. Distinguish **orbital** and **surface** scenes; for gas giants use **orbital/atmospheric** views.
- Produce one labeled **actual-screenshot contact sheet** via `endless_ci/build_visual_contact_sheet.py` (from #565 when merged) or equivalent; store raw screenshots as CI artifacts. Never represent Photoshop, Blender, ImageGen or manually painted concept art as a verified build screenshot.
- Check graphics correctness under low sun elevation, night-side phase, different camera distances and motion. Look especially for **Jupiter appearing flat** and **Saturn's rings disappearing in darkness**.
- Validate source/provenance/license for newly downloaded astronomical imagery and distinguish measured observations, interpretation and simulated procedural details.
- Set realistic Android performance tiers; benchmark real frame times/GPU behavior before making a performance claim. Avoid silent reductions in quality that make the delivered app obviously chunky.
- Signed ARM64 and universal APKs are **final deliverables only after exact-head release CI**, installation smoke and current screenshots. Earlier passing APKs are not proof that a newer PR works.

## 5. ABR — app-building rules all agents must follow

1. No redundant controls, duplicate reloads, pointless text or filler claims.
2. No stock/default Android text boxes: custom, consistent, legible control styling.
3. Use polished vector/SVG-equivalent assets rather than crude placeholder symbols.
4. Strict Android minimum SDK 26 and actual installation tests; a green compile alone is not completion.
5. Immersive/fullscreen scene with status bar hidden where appropriate; touch targets usable.
6. Use device hardware volume keys; avoid redundant global volume sliders.
7. Optimize practical smoothness; do not sacrifice controls/physics for screenshots.
8. Visually compare **real screenshots**, do not report cosmetic tweaks as photorealism.
9. Make honest claims about simulated geology, atmospheric approximations, orbital ephemerides and device limits.

## 6. Current integration backlog for the lead

**P0:** Complete and verify #565 **at its final commit**; read exact logs for any failing Kotlin unit/instrumentation test; check `ExtendedLandingSitesTest.kt` crater-rim assertions and `EndlessRenderer.kt` terrain-sampler expression if compilation fails. Recheck close/long-range screenshots, and integrate the final working branch into `endless-android-ci`.

**P0:** Ensure six grounded landing environments remain accessible; gas giants never invoke solid-ground landing. Confirm camera light-facing orientation does not override deliberate manual look.

**P1:** Integrate Agent 1, Agent 2 and Agent 3 PRs without simultaneous edits to common renderer/shader files. Run full CI and generate exact-build actual capture sheets.

**P1:** Address inescapable visual defects systematically. A planet being identifiable is not evidence it is photorealistic. Iterate through lighting, materials, silhouette, depth, atmospheric/ring rendering and measured-scene screenshot comparisons.

**P2:** Release and verify fresh signed APKs; document performance, scientific approximations and remaining items without calling unfinished work complete.

## 7. Handoff template (put this in every PR)

- **Lane / scope:**
- **Branch / base SHA / head SHA:**
- **Files owned and edited:**
- **What visibly/physically changed:**
- **What is real data vs simulated:**
- **Tests run (links, exact SHA, API level):**
- **Actual screenshot artifact links (before/after):**
- **Performance results and device:**
- **Shared integration edits needed from lead:**
- **Open issues and next actionable step:**

## 8. Coordination source of truth

This file on the **`endless-android-ci` branch** and GitHub PR comments are the collaboration contract. User/lead can revise lane assignments; teammates should **read fresh before each major integration**. When an agent arrives, identify your lane in your first PR/comment, select unclaimed work in that lane, and implement rather than posting a status-only message.

**No milestone-only stop, no silent replacement of real tests by illustrations, no invented verification, no default declaration of "complete".**
