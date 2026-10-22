# MISE — Live 10-agent completion backlog

## ACTIVE CHECKPOINT — 2026-10-22 (supersedes historical table below)

**Integration**: `integrate/mise-studio-completion-20261020`, latest coordinated source at `9c70c3d478184a20caa94a7be8e06b3c0f2a46bf` when this was written. **Never merge to production `main` without the user's approval.** All earlier specialist PRs #443–#458 were merged into integration on October 20, contrary to the old launch snapshot below. Issue #440 is the authoritative agent message stream.

### Tested versus not yet verified
- **Actually verified earlier:** API26 and API36 real under-tree Android render suites passed on `9b623c57`; many fresh-editor API26/36 object/rig tests and independent humanoid/bicycle frame checks passed. This is not a final quality sign-off.
- **Existing confirmed release P0/P1:** APK candidate staging failed because `android.injected.build.abi` did not remove x86/x86_64 libraries from the ARM64 variant; real API26/36 anime hair/skirt frame comparison fails, and Android 16's system immersive confirmation obscured PNG controls on first use. Old API26 SAF test looked in the pre-seeded under-tree scene file, not the newly created scene.
- **Coordinator fixes now committed to integration (not yet re-verified):** `192ff53` explicit Gradle `ndk.abiFilters` for `-Pmise.arm64Only=true`; `49bf504` CI builds deterministic ARM64 APK and inspects native ZIP ABIs; `9c70c3d` smoke test identifies actual newly created saved project using file-list delta and clicks the real OS first-use fullscreen dialog; earlier `194d8c1` strengthens PNG-export and imported-character persisted actor-ID checks; `9b623c57` merged checksum-preserving CC0 CDN retries (#507). Do not weaken any pixel, persistence or ARM64 assertions.
- **Existing PR #504 — open, not landed**: true VRM hair physics fix branch `fix/mise-hair-physics-rig-20261020`, current head `fde6376c2f840f1c0edaaff4031d6f01a7f8116f`. Resolves actual authored glTF spring joints, avoids slow-emulator-frame resets, separates hair/fabric controls, restores disabled springs to rig bind pose, uses `rememberUpdatedState(actor)` for the SceneView native onFrame closure and logs real changes to wind/gravity. **This MUST pass actual API26/36 hair >30-pixel motion and stationary-skirt proof and Agent10 independent review before merge.**
- **Real pixel evidence:** An unpacked October 22 API26 failing screenshot artifact showed **0 hair-region changed pixels** under the earlier spring code, despite 7 bound hair roots and actual Inspector clicks; no claim that current fix works until a new rendered screenshot confirms it. Extracted bundled female GLB from earlier passing release APK; at least 7 authentic HairJoint roots are in the skin GPU palette and some have hundreds of weighted vertices. Do not substitute a fake sprite wiggle.

### Next executable, unblocked engineering actions
1. A1/A9: inspect `9c70c3d` combined run `39827482887` after jobs complete. For ABI staging, confirm exact arm64-only and universal native ZIP entries, APK QA signing only and actual release-install API26/36. For smoke, verify two Android fullscreen controls and the imported actor persists to the exact newly-created project, with real pose/force-stop/PNG export proof.
2. A3/A4/A7: inspect #504 run `39827886295` and `39827886332`. If the live hair value logs update but hair pixels remain static, debug Filament skin matrix lifetime/animation frames against weighted HairJoint nodes and do **not** lower thresholds. A1 coordinates SceneViewport ownership and merges only proven changes.
3. A10: independently critique and capture full API26/API36 **real APK** frames: two independently posed anime classmates, hair silhouette and face visibility, jacket/skirt, real terrain sculpt, directional shadows and sun disk, frame size, modal touch targets, autosave/reopen, live rig/picker, orientation and PNG. Flag visually ugly but compiling outputs.
4. A5/A6/A8: fix whichever first Android device acceptance failure remains after first integration run. Keep authentic licensed art, scene schema/IDs, undoable terrain changes and stable project files.
5. Coordinator: once checks actually prove the PR, merge #504 to integration only, repeat the full combined build/install/gesture/visual screenshot test, then move to next open P0/P1. **Release gate NO-GO until documented interactive visual and API26/API36 passes**; production signing not configured.

Historical notes and task assignments below are retained for provenance but must not override this dated checkpoint.

Updated 2026-10-20 (A1 startup audit). Canonical integration: `integrate/mise-studio-completion-20261020`. **Communication hub: issue #440 (https://github.com/Tomex777/Build/issues/440).**

**State: launch instructions prepared; not evidence that 10 autonomous workers are running.** Project owner must open separate coding chats. Each agent claims a task and posts a GitHub comment; Agent 1 owns integration. No "done" until code is merged, built, installed and interaction-tested.

## Known foundation before new multi-agent work

- MISE 1.0.0 merged to main via #197.
- Latest advanced branch `mise-two-students-under-tree-20261018` = draft PR #335 based on `mise-anime-figure-pose-study-20261014` (#223). Integration branches from this advanced snapshot for preservation, *not* from empty main.
- Earlier #220 immersive editor and #221 PBR skies remain independently open; **compare code & CI** before cherry-pick, since their content may overlap.
- Real terrain meshes, sun orbit lighting, versioned project data, real PBR surfaces and two students under tree present in #335 source. User's direct terrain brush, terrain painting, visible sun disk/gizmo and advanced grass/structure interiors were explicitly not completed.
- Build/run proof for latest integrated head **not checked or accepted**. Source inspect is not visual QA.

## Coordinator's verified integration audit (2026-10-20)

- Integration at launch: `7425f9507976502c9ad9f18aa0516497a53983bb`. The #221 environment branch, #223 anime branch and #335 under-tree branch are **all ancestors** of integration (`ahead_by=0` on `compare_commits`): do not cherry-pick or merge them again.
- #220 immersive Inspector PR diverges from integration (`ahead_by=15; behind_by=237`) and overlaps `MainActivity.kt`, `StudioScreen.kt`, and `PrototypeScene.kt`. **Do not merge #220 wholesale.** Port small scoped features with fresh tests: A1 owns editor window policy; A2 owns inspector/custom XYZ UI; A3/A8 check any additional demo seeding contracts.
- Coordinator feature PR: **#443** (`agent/mise-01-coord`) implements editor status-bar hiding/browser restoration with Android transition assertions. **Draft/QA pending**, not merged or installed. Initial integration QA run: **39378406981**, queued at first audit; #443 QA: **39380143250**, queued when posted.
- Agent 1 owns shared `MainActivity.kt` change window until #443 is reviewed. Coordinate A2 viewport/UI changes, A7 renderer, A8 schema, A9 workflows and A10 device acceptance through issue #440. No other specialist activity should be asserted without their own GitHub check-ins.
- Before landing #443: inspect CI results and ideally get A10's separate emulator touch/screenshot evidence; then merge to **integration only**, and update this ledger with the exact SHA.

## Task queue (owners are *role assignments*, not claim of live execution)

| ID | Agent | Priority | Status | Task |
| --- | --- | --- | --- | --- |
| M-001 | A1 | P0 | ACTIVE — #443 in QA | Coordinator: branch/release integration, reconcile stacked PRs, locks, continuous PR merging, end-to-end APK |
| M-002 | A2 | P0 | UNCLAIMED | Editor UI: immersive professional workspace, custom inputs, tool discoverability, reliable touch gestures and inspector |
| M-003 | A3 | P0 | UNCLAIMED | Characters: detailed anime/real model bases, actual hair/facial appearance, selection and accessories |
| M-004 | A4 | P0 | UNCLAIMED | Rigging/animation: independent pose/IK, morphs, timeline, playback and keyframes |
| M-005 | A5 | P0 | UNCLAIMED | Outdoor environments: terrain brush/heightmap, sun gizmo, ground PBR, trees, atmosphere and shadows |
| M-006 | A6 | P0 | UNCLAIMED | Asset library: real licensed source models, SAF import, durable asset cache, searchable browser and materials |
| M-007 | A7 | P0 | UNCLAIMED | Camera/render: perspective/ortho, lighting, export match, photorealistic scene output and mobile GPU quality |
| M-008 | A8 | P0 | UNCLAIMED | Project model: reliable hierarchy, undo/redo, saving, migration, correct attachments and animation tracks |
| M-009 | A9 | P0 | UNCLAIMED | Android health: FPS/memory, asset packaging, build CI, device compatibility and signing guard |
| M-010 | A10 | P0 | UNCLAIMED | Independent QA: API 26/36 installation, real taps/3D screens, scene screenshots, regressions |
| M-011 | A1 | P0 | BLOCKED — combined QA | Combine PRs into one running APK and inspect on Android before candidate release |
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
