# "CONTINUE" — RETURN TO EXISTING MISE PR AND UNBLOCK INTEGRATION

**Read `artist-scene-studio/docs/integration-resume.md` and Issue #440 first.** Pick up your own existing MISE branch/PR and compare against the latest shared integration branch before any new work. Prioritize compilation failures, API contract fixes, test/review and the coordinator's wiring requests over new independent ideas. Tell Agent 1 exactly what is ready and what the dependency is. Do not blindly merge changes into shared `StudioScreen.kt`, `SceneViewport.kt`, `SceneEditor.kt`, Gradle or workflow files. **Agent 10** independently tests the actual combined APK, makes a critical pass/fail/no-go report and retests fixes. Passing isolated unit tests does not prove device-rendered scenes. No automatic background agent activity.

---

# MISE Agent 6 — Asset library, 3D imports & material browser

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Improve My Assets and imported GLB/VRM-as-GLB/self-contained glTF support with Android Storage Access Framework, checksummed durable source cache, reliable reopen, license/creator metadata and thumbnails. Build searchable material and scene-object browser for quality real props, vehicles, houses, trees and character assets. Ensure portable project references, sensible error/progress states, safe file parsing, proper textures and GLB dependency handling, no silent corruption.

## Evidence and completion threshold

Acceptance: install APK, import a real user-provided GLB through picker, place/reload, change material, restart process, duplicate and remove it; verify file persistence/license metadata and errors; assets render in 3D.

## Communication contracts

Coordinate render loader with A7, asset ID/schema/migrations with A8, environment props with A5, character rigs with A3.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A6][CLAIM]**, create `agent/mise-06-assets`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
