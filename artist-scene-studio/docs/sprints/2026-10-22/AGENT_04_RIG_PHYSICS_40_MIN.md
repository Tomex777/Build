# MISE A4 — 40-minute real rig / hair physics sprint

**Agent:** MISE Agent 4 (animation/rigging). **Spend exactly 40:00 in the active session**, following [THREE_AGENT_40_MIN.md](THREE_AGENT_40_MIN.md). Reuse **existing PR #504** (`fix/mise-hair-physics-rig-20261020`) rather than inventing another competing spring system. Do not assert that Markdown has started an agent.

## Priority P0: Hair must visibly move on the ACTUAL skinned character
Authentic female VRM model: 7 hair roots + 12 fabric spring roots were logged; actual 320×640 API26 screenshots after real Inspector taps had **0 head/hair pixels changed** before the latest fix. Prior fix added glTF-hierarchy root lookup, bounded frame timing, strict hair/fabric separation, disabled-spring rest restoration and `rememberUpdatedState(actor)` for SceneView native frame callbacks, plus `MiseHairPhysics` live-settings diagnostics. **Still not a PASS until actual API26+API36 screenshots prove it**. Retain strict independent body/camera stability.

**Owned code on PR #504:** `app/src/main/java/studio/artistscene/app/VrmSpringRuntime.kt` and `app/src/androidTest/java/studio/artistscene/app/AnimePoseStudyInstrumentedTest.kt`. `SceneViewport.kt` is shared with A7/A1; announce and confirm a scoped editing window on #440 before changing it. Don't change A3's `UnderTreeScene.kt`/`CharacterDemoStyling.kt` or release workflows.

## Clock (40:00 total)
**00:00–05:00 (5m) — Claim and measure.** Read issue #440, latest canonical/PR #504 SHA, PR mergeability, and actual CI state (start at [#39827886295](https://github.com/Tomex777/Build/actions/runs/39827886295) and under-tree [#39827886332](https://github.com/Tomex777/Build/actions/runs/39827886332)). Start stopwatch, post T0 UTC and claimed files. Inspect fresh fail logs if tests have run; do not cite an old queued label as current.

**05:00–21:00 (16m) — Real implementation.** Determine whether the per-frame callback sees live Inspector values and whether named HairJoint node transforms update SKINNED vertices. Inspect GLB skin palette, transform hierarchy, actual `model.animator.updateBoneMatrices()` call order and spring motion. Fix **the cause of nonmoving genuine geometry**, not the ROI threshold. Rebuilding a bind pose after toggling physics must not freeze changes, corrupt user rig edits, move skirts under hair-only wind or compound rotations each frame. Preserve motion on API26 low-end GPU.

**21:00–30:00 (9m) — Test visible deformation.** Run unit/Android instrumentation when possible, and inspect *raw real* still vs wind/gravity frames from installed MISE. Validate the already-existing hair-only assertion: **>30 changed pixels in actual head/hair region AND <70 changed pixels in rigid lower-body/camera region**, same dimensions and camera, independent of fabric springs. Require logcat real live slider values and authentic rendered head displacement, not UI animation or a screenshot overlay. Also check wind/off -> settle, pause/resume, hair+fabric individually enabled.

**30:00–38:00 (8m) — Repair and regress.** Fix first new measured failure; rerun. If hair passes, take next owned A4 P0: independently posed A/B actor, real joint bend/IK alignment, timeline pose key/scrub/play or saved pose restoration. Do not stop early; if CI is queued, perform real model/skin data analysis and add an independent focused regression test.

**38:00–40:00 (2m) — Commit/handoff.** Push the **same PR #504**, record current branch and conflict with integration if any. Send `[MISE A4][READY|BLOCKED|HANDOFF]` on issue #440 with measured elapsed minutes, exact test status, real image/run links and remaining blockers. Ask A10 to accept/reject raw frames. **Never merge to main or declare scene release ready.**

## Fail-closed criteria
- A passing Kotlin unit test, bound bone count, live Inspector callback and under-tree render PASS are necessary but NOT SUFFICIENT: actual visible hair pixels must differ.
- Hair controls must not animate rigid skirt/feet/camera. Fabric toggle must work independently and restore skinned rest transforms, without loss of authored pose.
- Test threshold/ROI must not be lowered, re-centered opportunistically, doctored, or bypassed. If API36 renderer/emulator drops ADB, mark that platform BLOCKED rather than PASS.
