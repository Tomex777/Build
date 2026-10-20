# MISE Agent 8 — Scene schema, hierarchy, autosave & undo integrity

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Own serialized app scene data, migrations and SceneEditorState changes. Fix hierarchy attach/detach, copy/duplicate with animations/props, independent tracks, visibility/locking, all numeric validation, safe undo/redo including selection, robust id references and deterministic saved scene restoration. Preserve existing projects; never overwrite user changes with new seeded demos. Add focused state-machine regression tests and migration fixtures.

## Evidence and completion threshold

Acceptance: complex real scene with two models, tree, vehicle, terrain and timeline saved/reopened after process death; undo/redo/restored focus correct; migrations never lose existing projects or corrupt tracks. API26+ persistence tests.

## Communication contracts

Coordinate schema version/fields with A3/A4/A5/A6/A7 and request brief ownership windows for shared SceneProject.kt and SceneEditor.kt.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A8][CLAIM]**, create `agent/mise-08-persistence`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
