# MISE A10 — 40-minute independent Android QA sprint

**Agent:** MISE Agent 10 (independent adversarial QA). **Work exactly 40:00 minutes**, following [THREE_AGENT_40_MIN.md](THREE_AGENT_40_MIN.md). **DO NOT implement A3/A4 features or certify your own feature changes.** Evaluate the actual integrated code and authentic 3D Android frames; tell other agents what failed.

## Targets / evidence, before first tap
- Canonical integration: `integrate/mise-studio-completion-20261020` (re-check actual SHA, not stale file text).
- Combined Android [run #39828822136](https://github.com/Tomex777/Build/actions/runs/39828822136); hair candidate [#39827886295](https://github.com/Tomex777/Build/actions/runs/39827886295); under-tree real-scene [#39827886332](https://github.com/Tomex777/Build/actions/runs/39827886332). Run states change; query them live.
- Earlier real evidence: 0 pixels changed in hair ROI under slider taps; API36 Android immersive notice hid PNG actions; ARM64 candidate accidentally carried x86/x86_64; API26 smoke read the wrong saved scene file. These are bugs/past failures, not assertions that the latest fix is broken.

**QA-owned output:** `docs/qa-acceptance-matrix.md` and a dated SHA-scoped run report; screenshots/JSON/logcat from real Android. Only isolated A10 verification scripts may be edited after declaring ownership on issue #440. Do NOT change `VrmSpringRuntime.kt`, `SceneViewport.kt`, `UnderTreeScene.kt`, `StudioScreen.kt` or `AnimePoseStudyInstrumentedTest.kt` during this sprint.

## Clock (40:00 total)
**00:00–05:00 (5m) — Set baseline.** Record UTC T0 and commit/branch, issue #440 state, PR #504 exact head and latest CI results. Claim QA docs. Check APK installability and release manifest/ABI data; distinguish QA/debug signed vs official production-signed. Open real artifact screenshots, logcat and XML before writing a verdict.

**05:00–20:00 (15m) — Execute integrated Android tests.** On API26 and API36 emulators when available: install/launch actual APK, enter project browser, open two-classmates scene, select A/B, pose joints, scrub timeline, create a fresh project, import genuine SAF GLB, save/force-stop/reopen; enter clean fullscreen, dismiss real Android prompt if present, export PNG, rotate portrait/landscape, verify input and no flicker/black frame. Capture untouched screenshots & logcat. If no connected emulator or APK, **download/inspect actual CI evidence**; label actions you did not execute as NOT RUN, never invent touches or APK screenshot.

**20:00–30:00 (10m) — Visual comparison (MANDATORY).** Open actual raw 3D frame PNGs: female/male faces, hair silhouette, clothing vs body occlusion, stiff poses, tree/ground contact, sky proportion, shadows/sun, clipping, tiny tool controls and top bar. Verify live hair before/after against same camera and exact hair ROI **>30 changed pixels**, rigid-body/skirt ROI **<70 changed pixels**, genuine geometry not fake overlays. Compare PNG export against live 3D screenshot when available. Reject looks that still resemble cheap toy placeholders even if tests pass.

**30:00–38:00 (8m) — File reproducible failures and retest.** For each real defect post severity, exact SHA/API/steps/expected/actual, original artifact link and responsible agent in issue #440. If fixed candidate arrives, independently retest; otherwise write fail-closed regression coverage or inspect another non-conflicting P0 such as arm64-only APK content, project autosave or API36 immersive notice. Do not stop after one screenshot or CI badge.

**38:00–40:00 (2m) — Final evidence handoff.** Commit QA report on a dedicated A10 branch / PR targeting integration, or post report in issue #440 if code write conflicts. Record actual UTC start/end, measured elapsed, **PASS/FAIL/NOT RUN/BLOCKED** *per test and API*, source artifacts and required next owner action. Final decision remains **NO-GO** unless *entire* definition-of-done and release chain is genuinely satisfied.

## Absolute rules
- Unit PASS ≠ APK install PASS ≠ user touch PASS ≠ pixel/visual PASS ≠ release.
- Keep screenshots unedited for evidence, refer to precise commit; never substitute a generated promo render. Critique imperfect art/UI, ABR violations, touch errors and performance despite green checks.
- Never weaken A4's hair/skirt pixel criteria to make the run green, or call an API36 emulator disconnect a successful run.
- No `main` merge, signing key upload, production-release claim or silent saved-user-project mutation.
