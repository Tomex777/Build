# MISE A3 — 40-minute character-visuals sprint

**Agent:** MISE Agent 3 (characters and art presentation). **Run exactly 40:00 minutes of active engineering** under the master [THREE_AGENT_40_MIN.md](THREE_AGENT_40_MIN.md). Open the existing A3 MISE conversation. Do not claim the countdown began merely because this file exists.

## Mission and ownership
Make the actual **editable 3D** two-classmates-under-a-tree scene look substantially better: faces unobstructed, convincing independent poses, believable proportion/grounding and framing, legible hair and clothing, distinct female/male visual identity. Preserve the authentic licensed skinned VRoid GLBs, face blendshapes, lighting and project editability. **Don't produce an AI-generated 2D illustration or a cosmetically styled PNG pretending to be a live 3D scene.**

**Owned files:** `app/src/main/java/studio/artistscene/app/UnderTreeScene.kt` and `CharacterDemoStyling.kt`; add isolated A3 scene/style tests. For `PrototypeScene.kt` obtain ownership approval in issue #440 first. Do NOT edit `SceneViewport.kt`, `VrmSpringRuntime.kt`, `AnimePoseStudyInstrumentedTest.kt`, Gradle or shared workflow files. Agent 4 owns real hair physics and Agent 10 independently judges results.

**Starting defect evidence:** earlier actual Android shots show a very large dark hairstyle covering too much of the face, stiff arm posture, tiny center-framed characters with large empty sky and toy-like models. The packaged genuine female model has face/body/hair meshes and authored joints; work with what exists, not replacements of unproven provenance.

## Clock (40:00 total)
**00:00–05:00 (5m) — Read/claim.** Check latest integration SHA, issue #440 and existing A3 role file. Claim exact owned paths, share T0+UTC. Inspect `UnderTreeScene.kt`, `CharacterDemoStyling.kt`, latest real Android character screenshots, available rig/expression metadata and unresolved A3 tests. Select highest-impact *reproducible* appearance/framing failure; document actual frame source.

**05:00–23:00 (18m) — Implement.** Make small actual source improvements to the editable scene: e.g. correct head aim/pose, scale/horizon composition, believable facing-between-classmates, fix sample expression application or hair/face occlusion that can be safely fixed at authored transform/material level. Preserve unique actor IDs, licenses, independent rigs, low-memory compatibility and old project saves. Do not force demo pose/expression over artists' edited state. No redundant editor controls or filler labels.

**23:00–31:00 (8m) — Build AND real visual check.** Run available unit/Android test paths. Install/drive existing app on API26/API36 if an emulator is accessible; capture untouched full-screen **before/after 3D frames** at matching camera and device resolution. If Android can't run locally, inspect downloaded actual run frames/CI and report device validation NOT RUN, then add a fail-closed source/unit check instead of claiming visual success. Look closely at eyes, mouth, hair outline, arm elbows, foot position, shadow and negative space.

**31:00–38:00 (7m) — Correct what looks wrong.** Address the highest-confidence defect spotted in the frame review; rerun changed checks. If visuals really pass, take the next untouched A3-scoped P1 from `docs/agent-backlog.md` and work until the time boundary. Avoid editing A4-owned physics even if you notice motion failures; file them with reproduction for A4.

**38:00–40:00 (2m) — Commit/handoff.** Push a focused A3 branch/PR into **canonical integration** only (no direct integration push or `main` push); note latest checked base SHA. Post issue #440 `[MISE A3][READY|BLOCKED|HANDOFF]` with exact UTC start/end/elapsed, commit, test/visual artifacts and screenshot limitations. If unfinished, post **PARTIAL** plus file/line/next command rather than fabricated PASS.

## Acceptance rubric
- At least one **actual code change** addressing measured visual/behavior problems, not only an MD/status update. Better framing/appearance must remain a live 3D skinned scene.
- Each model remains independently selectable, poseable and persistent across save/reopen; don't silently overwrite artist edits. Faces should be legible rather than buried under hair, and two characters visibly different.
- No unlicensed asset swaps, no heavyweight rendering path that excludes Android 8+, no copied reference image or fake screenshot as proof.
- Agent 10 can confirm on an exact APK SHA or flag remaining issues. Until real frames are inspected, mark **VISUAL NOT VERIFIED**.
