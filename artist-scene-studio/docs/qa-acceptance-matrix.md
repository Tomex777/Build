# MISE — Agent 10 independent release acceptance record

**Status: NO-GO (2026-10-20).** A passing commit or a compiled APK is not permission to release. The canonical integration SHA when A10 started was `7425f9507976502c9ad9f18aa0516497a53983bb`. The latest complete combined app is not yet independently certified. Do not promote the A10 QA branch or any debug build as a production release.

## Evidence levels and current finding

| Requirement | Code/source inspection | Compiled APK | Installed on API 26 | Installed on API 36 | Independent touch + visually examined real frame | Conclusion |
| --- | --- | --- | --- | --- | --- | --- |
| Launch, immersive/editor layout, portrait and landscape | Existing Android smoke covers some controls | Unverified for this integration SHA | Unverified | Unverified | Unverified | OPEN |
| Projects: create, rename, duplicate, delete, save, force-stop, reopen | Existing renderer script exercises save/reopen | Unverified | Unverified | Unverified | Unverified | OPEN |
| SAF import and real textured/skinned model visible | Existing Android smoke includes SAF and real GLB | Unverified | Unverified | Unverified | Unverified | OPEN |
| Two independent actors, bone transform, IK and pose persistence | Real instrumented tests and scene fixtures exist | Unverified | Unverified | Unverified | Unverified | OPEN |
| Hair, face, accessories and appearance | Real humanoid/anime tests exist | Unverified | Unverified | Unverified | Unverified | OPEN |
| Timeline keyframes, scrub, loop, undo/redo | Source and unit tests exist | Unverified | Unverified | Unverified | Unverified | OPEN |
| Terrain, sky, visible sun, two light positions, shadows, tree/house | Terrain tests exist; in-editor sculpting/sun gizmo still known gaps | Unverified | Unverified | Unverified | Unverified | OPEN |
| Renderer output, camera/perspective, PNG export live-frame match | Screen and export smoke exist | Unverified | Unverified | Unverified | Unverified | OPEN |
| Cold launch, memory/FPS, invalid input, crash/ANR, rotation | No current integrated report | Unverified | Unverified | Unverified | Unverified | OPEN |
| Real ARM64 physical device and production signature | No connected ARM64 device/approved keystore in this A10 session | Unverified | N/A | N/A | Unverified | OPEN |

**Why NO-GO:** CI run `39378406981` for integration `7425f950...` was **queued** when inspected, not passed. This chat's execution container lacks Android SDK, `adb`, emulator and `gradle`; therefore the six locally executed Python verifier fixture tests do **not** count as real Android rendering or visual acceptance. Real-device and full app QA must be recorded separately. No attempt has been made to erase or rewrite a real user's saved scenes.

## New automated safety net (Agent 10)

- `scripts/verify-acceptance-frames.py` checks both **original device PNG files**, PNG CRC/integrity, canvas non-black/non-uniformity, equal image size and changed central 3D-region pixel fraction. It rejects identical frames and differences outside the central viewport. It emits a per-case JSON report with raw-image SHA-256 digests. The small default 0.1% gate in our ADB wrapper is a regression alarm, **not proof of model correctness/beauty**.
- `scripts/fresh-editor-instrumentation.sh` now invokes that checker for actual Android before/appearance and appearance/posed **humanoid** frames separately, along with **anime**, **bicycle** and **car** pose/movement frames. It stores JSON beside the captured screenshots for reviewers. The already-existing test fixture still asserts state persistence, independently in Kotlin.
- `.github/workflows/mise-agent10-frame-qa.yml` runs the six offline checker regressions, then API26 and API36 Android `humanoid`, `anime` and `bicycle` instrumented touch/render passes (maximum two jobs concurrent); uploads unmodified screenshots and JSON. It is not a replacement for manual visual review of the downloaded artifact. Car can be explicitly exercised by running `TEST_SUITE=car bash scripts/fresh-editor-instrumentation.sh` in an emulator.

## Manual interaction protocol and report format

1. Start with two genuinely different configurations: API 26 x86_64 and API 36 x86_64, then available ARM64 physical hardware. Record `git rev-parse HEAD`, `adb shell getprop ro.build.version.sdk`, ABI, device resolution/density, `aapt dump badging`, signature and installation status. Capture fresh start and orientation change.
2. Use the installed app UI (not direct project-store mutation) to create, rename, duplicate, delete and open scenes; record original and restored project JSON after force stop. Confirm another user's saved project is untouched. Exercise SAF `ACTION_OPEN_DOCUMENT` and a deliberately broken file.
3. Import two real independently skinned model assets. Tap actor A, apply visible joint/IK pose; tap B, apply distinct pose; verify actor A remains unchanged. Adjust hair/material/face where supported. Make a keyframe on each, scrub, play, loop and undo. Check touch handles at different zoom levels and orientations.
4. Place/edit a tree, house, ground, hills and real prop. Select/hide/lock/move/duplicate an object, edit sun direction and light, move camera and export PNG. Compare export against live frame; inspect geometry, resolution, horizon, alpha and crop. Verify absent/unknown assets produce actionable UI feedback.
5. Save, force-stop via `adb shell am force-stop studio.artistscene.app`, relaunch and verify assets, hierarchy, poses, timeline and camera. Collect logcat, ANR/crash buffer, screenshots and native crash details. Repeat on prolonged edits to measure frame responsiveness and memory.
6. Complete the matrix **per exact integration commit**, device, API, test action, observed result, screenshot/log artifact and defect. Mark any unexecuted row **UNTESTED**, not PASS. Report blockers to the owner agent through issue #440 with reproduction, severity and responsibility.

### QA go/no-go handoff to Agent 1

- **Current decision:** **NO-GO — INSUFFICIENT AND PENDING INTEGRATED DEVICE EVIDENCE.**
- **Immediate action:** complete the pending integration workflow, run A10 API26/36 screenshot jobs, inspect artifacts manually (character realism, controls, terrain shadows, no clipping), and file reproductions for any FAIL. Agent 1 must merge verified fixes and rerun the gate on that exact integration SHA.
- **Release:** requires all P0 blockers removed, P1 scope transparently agreed, independent Android touch/visual evidence, and user approval. Debug/QA signatures must never be described as production signing.
