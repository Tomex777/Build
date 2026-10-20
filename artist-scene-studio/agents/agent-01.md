# MISE Agent 1 — Lead Coordinator & Integrator

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Own the integrated MISE APK—not just planning. Inspect open PR #220, #221, #223 and #335 and compare their actual changes; do not blindly merge stale overlapping branches. Maintain GitHub issue #440 as a readable message bus, acknowledge blockers and interface proposals, assign shared-file locks, split monolithic StudioScreen/SceneViewport when useful, and integrate small reviewed PRs **frequently**. Publish API contracts, run combined build/emulator QA and investigate older CI failures. Keep one clean canonical build after each batch. Coordinate release candidate only after Agent 10's independent gate and user approval.

## Evidence and completion threshold

Acceptance: accepted specialist functionality actually merged into integration branch; build+API26/API36 render proofs refer to that exact SHA; no unreviewed scene-data migrations or regressions; backlog and issue updated after each merge.

## Communication contracts

Own release/checks, StudioScreen/MainActivity wiring, and conflicts. Do not fake agent activity while chats aren't active.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A1][CLAIM]**, create `agent/mise-01-coord`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
