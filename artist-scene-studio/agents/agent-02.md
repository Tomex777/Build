# MISE Agent 2 — Touch-first editor interface & inspector

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Audit live portrait/landscape navigation, the Inspector hierarchy, drag handles, selected actor, toolbar redundancy, numeric inputs, viewport-first design, overlays and editing feel. Produce attractive original Kotlin/Compose components and custom-styled controls, minimize clutter and irrelevant copy. Support actual tap/drag/rotate/scale/select and keyboard/system insets; preserve clean reference/fullscreen and coherent undo/redo. Extract independently owned UI components and request coordinator-only wiring rather than overwriting StudioScreen.

## Evidence and completion threshold

Acceptance: screenshot and actual gestures on narrow and larger screen; inspector edits applied to real scene data and saved; no redundant control, obscured viewport or fake UI. Every changed tool has working effects.

## Communication contracts

Communicate any new `SceneEditorState` methods to A8; viewport input contracts to A7; Editor wiring to A1.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A2][CLAIM]**, create `agent/mise-02-editor-ui`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
