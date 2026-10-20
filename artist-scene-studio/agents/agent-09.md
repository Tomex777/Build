# "CONTINUE" — RETURN TO EXISTING MISE PR AND UNBLOCK INTEGRATION

**Read `artist-scene-studio/docs/integration-resume.md` and Issue #440 first.** Pick up your own existing MISE branch/PR and compare against the latest shared integration branch before any new work. Prioritize compilation failures, API contract fixes, test/review and the coordinator's wiring requests over new independent ideas. Tell Agent 1 exactly what is ready and what the dependency is. Do not blindly merge changes into shared `StudioScreen.kt`, `SceneViewport.kt`, `SceneEditor.kt`, Gradle or workflow files. **Agent 10** independently tests the actual combined APK, makes a critical pass/fail/no-go report and retests fixes. Passing isolated unit tests does not prove device-rendered scenes. No automatic background agent activity.

---

# MISE Agent 9 — Android stability, performance, packaging & CI

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Profile memory/Filament frame times and allocations with realistically populated scenes; fix leaks, excessive allocations, load spikes, lifecycle crashes, rotations, background/resume and thermal/slow-phone issues. Preserve offline asset bundle and SHA-256 asset verification. Harden CI for integration PRs, Android API26/36 tests, debug/ARM64 install proof and production signing certificate continuity. Fix actual failing gradle/emulator runs, not just suppress errors. Avoid changing app identity or shipping debug keys as production.

## Evidence and completion threshold

Acceptance: CI runs on integration branch and agent PRs; actual renderer screenshots and logs; FPS/memory measurements for heavy scenes; installable APK validated, no regressions or false release-signing claims.

## Communication contracts

Own build/workflows and memory utilities after coordinating with A1; defer core renderer edits to A7 or request narrow lock.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A9][CLAIM]**, create `agent/mise-09-reliability`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
