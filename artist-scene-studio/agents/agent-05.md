# "CONTINUE" — RETURN TO EXISTING MISE PR AND UNBLOCK INTEGRATION

**Read `artist-scene-studio/docs/integration-resume.md` and Issue #440 first.** Pick up your own existing MISE branch/PR and compare against the latest shared integration branch before any new work. Prioritize compilation failures, API contract fixes, test/review and the coordinator's wiring requests over new independent ideas. Tell Agent 1 exactly what is ready and what the dependency is. Do not blindly merge changes into shared `StudioScreen.kt`, `SceneViewport.kt`, `SceneEditor.kt`, Gradle or workflow files. **Agent 10** independently tests the actual combined APK, makes a critical pass/fail/no-go report and retests fixes. Passing isolated unit tests does not prove device-rendered scenes. No automatic background agent activity.

---

# MISE Agent 5 — Terrain, PBR materials, sun & realistic environment

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Build on #335's real terrain and photo-backed materials, NOT replace them with 2D illusions. Complete native editable terrain workflow, brush sculpt or terrain-heightfield editing, material repaint where feasible, stable imported heightmap, physically plausible shadows, visible/usable sun direction gizmo and cinematic sky. Ensure realistic tree foliage, scale-consistent hills/valleys, grass and prop placement without exploding Filament draw calls. Keep scene edits undoable and persisted.

## Evidence and completion threshold

Acceptance: actual API26/API36 Filament renders of two altered terrain shapes, sun directions and ground surfaces; sculpt changes survive save/restart, no material fallback disguising test failure; provide before/after screen proof.

## Communication contracts

A7 owns SceneViewport render hooks; A8 WorldSettings/schema, A6 environment asset material pipeline. Agree interfaces and branch ownership in issue.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A5][CLAIM]**, create `agent/mise-05-environment`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
