> **ACTIVE THREE-AGENT 40-MINUTE ASSIGNMENT (2026-10-22):** When an active MISE chat identifies itself as Agent **3, 4 or 10**, first read [the three-agent sprint](docs/sprints/2026-10-22/THREE_AGENT_40_MIN.md) and its own linked 40-minute MD. Those timed tasks supersede the old generic first-action instruction for this *one sprint only*. Separate 40:00 active sessions, real implementation/visual proof, no early finish or invented time; master task MDs do not autonomously launch the agents. Check current head/Issue #440 before every edit. All other AGENTS rules remain.

# MISE — RESUME EXISTING WORK, INTEGRATE AND COMPLETE

When a user says **"Continue"**, **"Resume"**, or **"Keep going"** in an existing MISE agent chat, read `artist-scene-studio/docs/integration-resume.md`, the current agent role, canonical branch head and all latest **GitHub Issue #440** comments before anything else. Resume your existing open PR rather than create another detached development wave. Agent 1 is responsible for conflict-safe integration, making one compiled/running APK and coordinating independent Android QA; other agents unblock integration and fix existing PRs; Agent 10 is independent critical tester. A green CI badge or submission of ten PRs is not completion. Chats do not continue operating in the background after ending.

# MISE — Operating Rules for Ten Cooperative Coding Agents

**User intent:** Finish a genuinely polished native Android 3D creation/posing studio, not only get CI green or populate a demo scene. ABR applies: real UI, no redundant controls or filler, smooth touch UX, proper vectors/icons, no "AI vibe-coded" appearance, minSdk 26, verified on actual Android emulator/device.

## Short-chat role router

When the user says **"You are MISE Agent N"** (N=1–10) or says "You are Agent N" in an unambiguously MISE chat, read `agents/README.md`, your `agents/agent-NN.md`, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and the latest comments on GitHub issue **#440**, then inspect code and **implement immediately**. Don't merely restate your role.

## One codebase, safe parallel development

- **One app/repo:** `Tomex777/Build` / `artist-scene-studio/`; package **`studio.artistscene.app`** (do not rename during multi-agent work), minSdk **26**, Android Kotlin/Compose + SceneView/Filament.
- **One living integration branch:** `integrate/mise-studio-completion-20261020`. The coordinator maintains it as a frequently tested single build. This is what "working on the same codebase" means.
- **Many short-lived work branches/worktrees:** agents branch from the latest integration tip, edit distinct files/interfaces, and open small, frequent PRs targeting integration. **Never have ten independent chats push to the same branch or edit the same mutable worktree simultaneously.**
- All agents use the **same** scene schema, UI tokens, asset contracts, renderer APIs, licensing manifest, build and test acceptance gate.
- **Shared high-conflict files:** `app/src/main/java/studio/artistscene/app/StudioScreen.kt`, `SceneViewport.kt`, `MainActivity.kt`, `PrototypeScene.kt`, and core `SceneProject.kt`, `SceneEditor.kt`; also Gradle, manifests and the release workflows. Declare ownership before changes and extract small modules when possible.
- The coordinator merges passing changes promptly after review and testing; agents fetch/rebase the integration branch before new tasks. An agent can request a small coordinator-owned wiring commit rather than modifying another agent's feature file.

## Frequent, real cross-agent communication

**GitHub issue #440 is the central message bus.** Read it and its latest comments on startup, before a conflicting shared-interface change, and after an important PR/dependency update. Write concise check-ins when claiming, changing interface, blocked, ready for review, integrated or retested. Use this exact format:

```text
[MISE A#][CLAIM|DESIGN|BLOCKED|HANDOFF|READY|VERIFIED|INTEGRATED]
Task:
Files and public interface:
Branch/commit/PR:
Dependency or request to Agent #:
Build / installed / touch-tested / screenshot proof:
Next action:
```

Use GitHub PR review comments for line-level feedback and issue #440 for cross-agent blockers/decisions. Never claim other agents saw a message until their acknowledgement is visible. Do not ping every few minutes; communicate at meaningful milestones.

**All sessions have limits.** "Nonstop" means **continue performing the next unblocked engineering task throughout each active session**, not just planning and not stopping at a green build. When time/tools run out, commit state and write the exact resume instruction in `docs/agent-backlog.md` / issue comments. It does not create round-the-clock autonomous workers; that would require a separate orchestrator.

## Required development loop

Inspect → choose highest-priority unblocked ticket → coordinate interfaces → implement → compile → install → exercise actual touch/3D rendering → inspect screenshots/logcat → fix → retest → PR → coordinator merges → app regression tests → claim next task.

Separate **implemented**, **built**, **installed**, **3D-rendered**, **visually reviewed**, **merged** and **released** statuses. A PR or successful build cannot prove any later stage. Do not silently delete or mutate users' saved projects. No fake characters/terrain or 2D art claiming to be a live 3D model. Do not assume Android simulator screenshots equal physical device validation.

## Build, asset and safety rules

- Build JDK 17; verify checksum-checked asset fetch `artist-scene-studio/scripts/fetch-test-assets.sh`.
- Standard build: run `gradle :app:testDebugUnitTest :app:assembleDebug` from `artist-scene-studio/`, plus the existing API 26/36 renderer/device test workflows.
- Preserve existing model import/export, scene graph, poses/IK, timeline, cameras, lights, environment, undo/redo, autosave/migration and PNG export.
- Use properly licensed artwork/assets with attribution and checksum pinning; never commit secrets, private signing keys or unlicensed 3D media.
- Preserve production signing identity; do not publish a production-signed release until official keystore is configured and validated. Never present CI QA/debug signing as production signing.
- Do not merge MISE integration to `main` until user approves release.

**Release gate:** `docs/definition-of-done.md`. **Live task ledger:** `docs/agent-backlog.md`. **Communication:** GitHub issue #440.
