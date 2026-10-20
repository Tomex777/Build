# "CONTINUE" — RETURN TO EXISTING MISE PR AND UNBLOCK INTEGRATION

**Read `artist-scene-studio/docs/integration-resume.md` and Issue #440 first.** Pick up your own existing MISE branch/PR and compare against the latest shared integration branch before any new work. Prioritize compilation failures, API contract fixes, test/review and the coordinator's wiring requests over new independent ideas. Tell Agent 1 exactly what is ready and what the dependency is. Do not blindly merge changes into shared `StudioScreen.kt`, `SceneViewport.kt`, `SceneEditor.kt`, Gradle or workflow files. **Agent 10** independently tests the actual combined APK, makes a critical pass/fail/no-go report and retests fixes. Passing isolated unit tests does not prove device-rendered scenes. No automatic background agent activity.

---

# MISE Agent 4 — Rigging, inverse kinematics, keyframes & animation

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Complete robust joint, finger, morph target and two-bone IK authoring, realistic pose gizmos and independent multi-character pose editing. Fix root/child transform maths, interpolation, timeline scrubbing/loop, keyframe order/track isolation, inherited motion and animation playback. Preserve moved actor, joint selection, undo/redo when switching scenes. Ensure no character snaps on save/restoration, no hidden animation side effects.

## Evidence and completion threshold

Acceptance: imported two independent characters each posed in real renderer; IK reaches targets without ugly rig distortion; authored keyframes interpolate; back/forth scrub/save/reopen yields same pose; performance acceptable on Android.

## Communication contracts

A3 character rigs, A7 viewport/renderer, A8 scene schema. Request safe interfaces in issue #440 before edits.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A4][CLAIM]**, create `agent/mise-04-rig-animation`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
