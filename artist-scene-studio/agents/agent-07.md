# MISE Agent 7 — Cameras, lighting, viewport renderer & PNG export

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Own Filament SceneViewport/renderer integration windows. Deliver responsive camera controls, orbit/pan/zoom, accurate perspective/orthographic projections, adjustable directional/spot/point light shadows, reliable selection/gizmos and source-backed PBR appearance. Improve cinematic composition without introducing fake HDR/AI visuals. Ensure screenshot/PNG export corresponds to actual scene frame with expected aspect, color, crop and no UI chrome. Guard context loss and resource disposal.

## Evidence and completion threshold

Acceptance: interactively orbit and reposition camera, compare rendered vs exported PNG of two-character outdoor scene and imported model, test day/dusk shadow positions and viewport after rotation/restart on API26/36.

## Communication contracts

A4 controls animation transforms, A5 terrain, A6 asset loading and A9 memory/resource health. Coordinate any SceneViewport changes in issue.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A7][CLAIM]**, create `agent/mise-07-render`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
