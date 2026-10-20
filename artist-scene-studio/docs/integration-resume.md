# MISE — conflict-safe existing-PR integration and Android release gate

**Authoritative project:** `Tomex777/Build/artist-scene-studio`. **Canonical integration branch:** `integrate/mise-studio-completion-20261020`. **Shared inter-agent messages:** https://github.com/Tomex777/Build/issues/440. The user wants the already-created app **completed** and its visuals/behaviour independently criticized and repaired, not a new round of detached experiments. Existing chats may have stopped: sending "Continue" resumes that chat, not all ten magically.

## Snapshot to RE-CHECK before acting (October 20 2026)

The original MISE 1.0.0 merged via PR #197 to main. Advanced under-tree candidate #335 is a still-open draft; the integration branch from #335 currently includes the merged **agent-coordination PR #442**, but **none of the 16 agent feature PRs #443–#458** had merged at last inspection. Check fresh PR state, changed-file overlaps and exact Git SHA before decisions.

Pending:
- A1 #443 editor/fullscreen restoration; must use existing coordinator PR for incremental work.
- A2 #446 project browser, #452 custom XYZ inspector; require A1 wiring to `StudioScreen.kt`.
- A3 #450 VRM expression mixing (last QA **FAILURE**), #457 under-tree face initialization; need true Filament face screenshot.
- A4 #449 timeline/IK and #456 safe Filament rig rendering; check actual rig pose playback.
- A5 #455 real editable GLB terrain and global photographic PBR retexture (last QA **FAILURE**), #458 sun direction sky consistency (individual QA SUCCESS).
- A6 #444 glTF/GLB validation, #451 model cache recovery; ensure legitimate asset and actual import/open/reopen.
- A7 #445 viewport camera, lights and PNG export (individual QA SUCCESS); needed for A5 sky hookup.
- A8 #447 saved scene collisions, #454 hierarchy validity, selection and `SceneEditorState.commitSelectedAssetRevision(updated: Actor)` API needed by A5; check latest tests, #454 previously canceled.
- A9 #448 integration CI, signing and perf; general CI SUCCESS but under-tree QA previously canceled.
- A10 #453 screenshot acceptance and matrix; under-tree QA SUCCESS but specific screenshot workflow **FAILURE**. A passed report/script is not a complete interactive test.

## Agent 1 — INTEGRATE, DON'T START ANOTHER WAVE

1. Read newest Issue #440 messages, branch SHA, all #443–458 PRs/CI logs/changed files and the current Android studio source. Produce a conflict/dependency ledger: `PR | SHA | shared files | tests | actual Android evidence | blocked by | merger decision`. Do not claim all agents are still running.
2. Work from the **existing** A1 PR #443 / branch if it remains open. When integrating, choose one tested small change at a time into canonical integration, not replacing whole source files or blindly merging 16 PRs. A1 owns contested `StudioScreen.kt` editor wiring and `MainActivity.kt`; A7 owns `SceneViewport.kt`; A8 owns `SceneEditor.kt` and saved schema; A9 coordinates shared Gradle/CI. Announce interface ownership on Issue #440 and await collaborator acknowledgement where conflicts exist.
3. Fix **actual CI failures** (#450, #455, Agent10 screenshot checks) rather than excluding tests or treating queued/canceled tests as success. Build integration APK via JDK 17 Gradle from `artist-scene-studio` and explicitly verify compilation and install on emulator API 26 + 36.
4. **Merge dependency chain:** evaluate #448 CI if reliable; merge proven independent small PRs (e.g. #445 renderer, #458 sun math) with combined build each time. Validate and merge A8 #454's `commitSelectedAssetRevision` before A5 #455 is wired, then A1 integrates brush UI + changed GLB asset revisions through one undoable SceneEditor change. Wire A2 browser/Inspector and A4 timeline controls through A1's StudioScreen ownership window. Preserve original scene assets, serialization, actor identities and user work.
5. Once the single APK launches, **actually use the 3D viewport**, not merely screenshot static placeholders: create two real characters, independently select/pose/IK and animate, style face/hair/morphs, create/import GLB, sculpt elevated terrain and retexture, move sun and observe matching shadows, manipulate tree/house, save→force-stop→reopen, undo/redo, camera adjust and export real PNG. Agent 10 visually inspects before/after rendered pixels from actual APK at API26 and API36, tests portrait/landscape and reports FAIL/PASS/NOT_TESTED with exact SHA/screenshots/logcat.
6. Iterate failures with relevant A2–A9, re-run combined tests and request independent A10 retest; only declare "complete" if all P0/P1 acceptance scenarios are genuinely verified and user approves release. Debug/QA APK is not production signed. Do not push to main without user authorization.
7. **Keep executing within the active chat:** while CI queued, review another independent PR's diff or add regression tests; do not stall on a status report. At session end publish issue message with actual last integrated SHA, builds, failures, merge decisions, screenshots and next *executable* action. "Continue" in the SAME chat should pick up these checkpoints.

## Agents 2–9 — finish the existing implementation

Before writing new features, read your **existing** PR and the latest integration branch, fix compilation/runtime issues and coordinator-requested API contracts, and give Agent 1 a `[MISE A#][READY]` message containing changed files, actual passing checks, screenshot/touch proof where available and merge prerequisites. If waiting on contested shared files, work on isolated unit/Android instrumentation or code review, not a second conflicting implementation. When merged, move to the next relevant actual defect from QA.

## Agent 10 — independent tough critic

Build and **install the integrated APK** on API26/API36. Test actual gesture input and full scenarios, frame pacing, hierarchy, saving and visual fidelity; visually inspect 3D scene screenshots and exported PNG, not just CI labels. Identify bad character quality, stiff/buggy posing, cheap-looking assets, stuttering terrain brushes, incomplete UI, tiny controls or broken dialogs as product defects, even when unit tests pass. Compare against user-approved references. File reproducible issues with exact device/SHA/input, return to feature owner, and retest. Do not approve release on visual ugliness or unfinished features.

## Communication format

```text
[MISE A#][CLAIM|DESIGN|BLOCKED|READY|VERIFIED|INTEGRATED|HANDOFF]
Exact canonical branch SHA:
Existing PR/head SHA:
Shared file/API and owner:
Test type and exact outcome (build / installed / rendered / touch / screenshot):
Remaining failure / reviewer needed:
Next command or implementation step:
```

No ordinary chat works in the background. This runbook updates the already-merged Wave 1 cooperation instructions; **no additional wave or new detached implementation is authorized**.
