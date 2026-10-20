# MISE — release definition of DONE

Scope: native Android interactive 3D artist-reference / scene-creation application with trustworthy source-backed real 3D models, original polished UI, full editable projects, and good performance on API 26+. Do not assume any feature is finished just because a test fixture or video exists.

**Current starting state:** MISE 1.0 (#197) was merged into main; newer character/editor/environment work accumulated across #220, #221, #223 and draft #335. The integration branch is based on #335's current head, so it is **advanced but not automatically accepted/production-ready**. Do not overwrite user-created scene data. Audit the 1.0 baseline and reconcile older unmerged PRs by code comparison (not indiscriminate merging).

## Non-negotiable complete app acceptance scenarios

| Gate | Scenario | Evidence required |
|---|---|---|
| UX | Create/open/edit/rename/duplicate/delete a project; discover tools without redundant controls; correct Android bar/keyboard insets, portrait/landscape | Actual tap/swipe walkthrough on API26 and API36, screenshots |
| Real characters | Import GLB/compatible glTF/VRM-as-GLB; independently select/move/pose 2+ detailed real 3D characters, inspect geometry/materials; adjust hair/appearance/expression/accessories where implemented | Real Filament frame images, selected bone/pose before-after, screenshots |
| Pose & animation | Joint pose, IK hand/foot, finger/morphs, save/load pose, keyframe transforms/poses, timeline scrub/play/loop, undo; no corruption or broken constraints | Playback and serialized state tests + on-device interaction/video or captured frames |
| Environment | Ground PBR materials, imported/generated hills/valley/mountains, cast/receive shadows, sunlight direction/sky, trees/houses/vehicles, camera movement | **actual** rendered scenes under two contrasting light positions and different terrain, not a mock |
| Objects & assets | Transform/hierarchy, child attach, select/lock/hide, duplicate with independent animations, legitimate asset provenance/checksum, Android document picker import | Model file tests + real touch manipulation |
| Camera/export | Perspective/orthographic views, camera transforms and lighting, no accidental clipped/stretched models; clean high-quality export PNG with correct colors | Compare exported image to live scene and check dimensions/crop/transparency intent |
| Data integrity | Autosave, explicit save, persistent user scene after force-stop/update; safe schema migration, undo/redo, deleting a model cleans references and tracks | Tests on preserved sample and user-like saved projects |
| Stability | No blocking crashes/ANRs across long scene edits or complex models; responsive gestures; device thermal/RAM limits respected; offline usability | API26/API36 emulator and ARM64 physical-device where available, logcat/memory/FPS notes |
| Installation | Debug/QA APK actually installs and runs; verify package ID and minSdk 26; production signing only with official persistent keystore | CI artifacts, apksigner verification, install log |
| Product quality | No fake implemented features, no empty shell menu, no repeated buttons, visual quality judged against legitimate 3D creation apps | Agent 10 independent screenshot and user-facing scene/playthrough audit |

## Known starting gaps to investigate, not claim solved

- Current #335 acknowledges **no in-editor brush sculpting**; heightmap import can build terrain but direct sculpt is distinct.
- Terrain material repainting, a visible sun/lighting gizmo, realistic instanced grass and building interiors are not implemented in #335.
- Independent older PRs may overlap or not be contained in the latest branch. Some earlier head CI runs failed; current integration CI and device evidence must be checked afresh.
- Asset sourcing, external test-download stability, API26 graphics and Android 16 behaviour need actual QA.
- Character appearance must be real model/material edits, not static overlays; exact user-approved quality must be assessed visually.

## Release gates

**P0 release blockers:** build fails; no install/launch; broken editor/scene save/import; unusable manipulation; broken renderer or export; scene loss; severe privacy/security/copyright issue; unhandled regressions.

**P1 completeness:** missing/inert promised editor tools, visibly placeholder characters/props, inconsistent posing, camera/light inaccuracies, broken Time/undo, rough mobile UI, lack of asset authoring where core promise requires it. Clearly negotiate scope with user for out-of-scope ambitious future tools; do not declare done with unapproved omissions.

**P2 polish:** animations, haptics, visuals, tutorials, microinteractions, non-essential extras after P0/P1.

For each scenario record **implemented / built / installed / exercised / visually accepted / merged / release candidate** separately, with commit, device, API, input sequence and artifact evidence.

**DONE** only when Agent 10 independently verifies integrated build, Agent 1 reviews release blockers, owner approves scope and MISE's correct signed/QA artifact is provided. No fictional 24/7 execution claims.
