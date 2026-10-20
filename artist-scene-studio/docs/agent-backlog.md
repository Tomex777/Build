# MISE — Live 10-agent completion backlog

Updated 2026-10-20. Canonical integration: `integrate/mise-studio-completion-20261020`. **Communication hub: issue #440 (https://github.com/Tomex777/Build/issues/440).**

**State: launch instructions prepared; not evidence that 10 autonomous workers are running.** Project owner must open separate coding chats. Each agent claims a task and posts a GitHub comment; Agent 1 owns integration. No "done" until code is merged, built, installed and interaction-tested.

## Known foundation before new multi-agent work

- MISE 1.0.0 merged to main via #197.
- Latest advanced branch `mise-two-students-under-tree-20261018` = draft PR #335 based on `mise-anime-figure-pose-study-20261014` (#223). Integration branches from this advanced snapshot for preservation, *not* from empty main.
- Earlier #220 immersive editor and #221 PBR skies remain independently open; **compare code & CI** before cherry-pick, since their content may overlap.
- Real terrain meshes, sun orbit lighting, versioned project data, real PBR surfaces and two students under tree present in #335 source. User's direct terrain brush, terrain painting, visible sun disk/gizmo and advanced grass/structure interiors were explicitly not completed.
- Build/run proof for latest integrated head **not checked or accepted**. Source inspect is not visual QA.

## Task queue (owners are *role assignments*, not claim of live execution)

| ID | Agent | Priority | Status | Task |
| --- | --- | --- | --- | --- |
| M-001 | A1 | P0 | UNCLAIMED | Coordinator: branch/release integration, reconcile stacked PRs, locks, continuous PR merging, end-to-end APK |
| M-002 | A2 | P0 | UNCLAIMED | Editor UI: immersive professional workspace, custom inputs, tool discoverability, reliable touch gestures and inspector |
| M-003 | A3 | P0 | UNCLAIMED | Characters: detailed anime/real model bases, actual hair/facial appearance, selection and accessories |
| M-004 | A4 | P0 | UNCLAIMED | Rigging/animation: independent pose/IK, morphs, timeline, playback and keyframes |
| M-005 | A5 | P0 | UNCLAIMED | Outdoor environments: terrain brush/heightmap, sun gizmo, ground PBR, trees, atmosphere and shadows |
| M-006 | A6 | P0 | UNCLAIMED | Asset library: real licensed source models, SAF import, durable asset cache, searchable browser and materials |
| M-007 | A7 | P0 | UNCLAIMED | Camera/render: perspective/ortho, lighting, export match, photorealistic scene output and mobile GPU quality |
| M-008 | A8 | P0 | UNCLAIMED | Project model: reliable hierarchy, undo/redo, saving, migration, correct attachments and animation tracks |
| M-009 | A9 | P0 | UNCLAIMED | Android health: FPS/memory, asset packaging, build CI, device compatibility and signing guard |
| M-010 | A10 | P0 | UNCLAIMED | Independent QA: API 26/36 installation, real taps/3D screens, scene screenshots, regressions |
| M-011 | A1 | P0 | UNCLAIMED | Combine PRs into one running APK and inspect on Android before candidate release |
| M-012 | A5 | P1 | UNCLAIMED | Investigate requested in-app terrain sculpting/terrain painting vs existing heightmap import |
| M-013 | A3 | P1 | UNCLAIMED | Verify character artwork quality/hair and face differentiation with real rendered screenshots |
| M-014 | A9 | P1 | UNCLAIMED | Reduce cold start/memory/perf issues with a representative heavy multi-character scene |

## File ownership and API coordination

- A1: integration branch, whole-app MainActivity/StudioScreen wiring windows, shared build/release decisions and ledger.
- A2: extracted `ui/*` editor/inspector tools; negotiate StudioScreen wiring.
- A3: character assets/appearance modules and materials; coordinate Rig/Actor changes with A4/A8.
- A4: rig/pose/keyframe/animation modules; coordinate Actor schema with A8 and viewport with A7.
- A5: independent terrain, sun, outdoor environment modules; negotiate viewport hooks and WorldSettings schema.
- A6: asset import/cache, model/prop libraries; coordinate load contracts with A7 and persistence with A8.
- A7: renderer/camera/light/export; own SceneViewport/renderer change window; A5 and A4 contribute through interfaces.
- A8: scene graph/data schema/project persistence/undo; own SceneProject/SceneEditor change window.
- A9: lifecycle, assets build/Gradle/workflows; obtain shared file lock before edits.
- A10: testing scripts, screenshots, QA documents, instrumentation tests; independently retest integrated commits.

## Handoff template

```
Task ID / owner:
Branch / commit / PR:
Files changed and shared API:
Built? (exact command + status)
Installed? (device/API)
Touch interaction, image proof, logcat:
Remaining defects:
Blocked by whom / next executable step:
```

**Nonstop means active-session continuation.** After merge, take the next unblocked scoped item; when a tool/session limit occurs, persist the handoff instead of falsely promising background action. Issue #440 is the cross-chat conversation channel.


## 2026-10-20 integration resume update

**This overrides the initial UNCLAIMED/launch table above.** Ten specialist agent chats already produced **16 open MISE feature PRs #443–#458** in the integration stream; the original project/advanced PRs #220/#221/#223/#335 are separate and must be compared, not bulk-merged. Integration branch is based on #335 and still only merged shared coordination PR #442 at `7425f9507976502c9ad9f18aa0516497a53983bb` when checked. At that check, no specialist feature PR was integrated yet. Some QA (e.g. #445, #458) passed, #455 sculpt and #450 expressions failed, #453 screenshot QA failed; this is an early snapshot, not a substitute for checking current CI. **Use `docs/integration-resume.md` as the executable handoff and Issue #440 as the live communication/PR ledger.** A new "Continue" in existing Agent 1 should resume coordinating existing PR #443 and merging reviewed work, not create Wave 3 or new branches. The previous UNCLAIMED rows are archived historical allocation, not live status.
