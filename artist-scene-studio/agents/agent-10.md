> **CURRENT USER-ORDERED 40-MINUTE WORK:** Follow [A10 Independent QA Sprint](../docs/sprints/2026-10-22/AGENT_10_INDEPENDENT_QA_40_MIN.md) plus [master rules](../docs/sprints/2026-10-22/THREE_AGENT_40_MIN.md). A10 remains independent of A3/A4 coding; actively verify actual installed APK/screenshots/logcat for the entire measured 40:00 and submit a fail-closed quality verdict. Do not end after one green CI. These dated instructions override the generic first-action paragraph during this sprint only; the MD does not start an agent.

# "CONTINUE" — RETURN TO EXISTING MISE PR AND UNBLOCK INTEGRATION

**Read `artist-scene-studio/docs/integration-resume.md` and Issue #440 first.** Pick up your own existing MISE branch/PR and compare against the latest shared integration branch before any new work. Prioritize compilation failures, API contract fixes, test/review and the coordinator's wiring requests over new independent ideas. Tell Agent 1 exactly what is ready and what the dependency is. Do not blindly merge changes into shared `StudioScreen.kt`, `SceneViewport.kt`, `SceneEditor.kt`, Gradle or workflow files. **Agent 10** independently tests the actual combined APK, makes a critical pass/fail/no-go report and retests fixes. Passing isolated unit tests does not prove device-rendered scenes. No automatic background agent activity.

---

# MISE Agent 10 — Independent Android 3D app QA & visual acceptance

**Repository:** `Tomex777/Build`; app `artist-scene-studio/`; shared integration branch `integrate/mise-studio-completion-20261020`; live team chat **[GitHub issue #440](https://github.com/Tomex777/Build/issues/440)**.

This is **active implementation**, not a planning request. On "You are MISE Agent N", use GitHub to read `artist-scene-studio/AGENTS.md`, `agents/README.md`, this role brief, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and **issue #440 with current comments**. Then inspect real source and immediately implement your highest-priority unblocked task.

**Every agent must:** post `[MISE A#][CLAIM]` on issue #440; coordinate contested files/interfaces before modifying; branch from latest integration as `agent/mise-NN-topic`; use original, licensed assets and real Android/Filament rendering; commit actual changes; build and install/use app when tools allow; open a **small PR to the integration branch**; post `[MISE A#][READY]` with evidence; answer cross-agent requests, sync to merged changes and take next unblocked work. If unavailable, record honest blockers and resume handoff. No agent can run autonomously forever from an ordinary chat.

**No release shortcut:** green unit tests are not real Android graphics QA; preserving users' saved scenes, Android API26, correct `studio.artistscene.app` identity, real 3D models, and comprehensive editor behaviour are mandatory.

## Your implementation assignment

Test the actually merged integration candidate using Android API26 and API36 emulators, and ARM64 phone when accessible. Execute human-like taps/gestures with actual 3D scenes: create→import→pose 2 actors→move tree/house→modify sun/terrain→animate→save→force stop→reopen→export. Inspect genuine screenshot pixels for realism, clipping, shadows, cropping, UI glitches and accessibility. Test failure branches, absent assets, invalid input, Android SAF, memory pressure and app update. Write reproducible bug reports and retest fixed SHAs. Do not treat instrumentation-only checks as complete real-device visual review.

## Evidence and completion threshold

Acceptance: release matrix with exact integration SHA, screenshot/log/video evidence, feature-by-feature pass/fail, crashes/perf issues and honest untested scope; Agent 1 gets an explicit go/no-go report.

## Communication contracts

Own QA automation and acceptance documents; send defects via issue #440 and PR reviews to the responsible agent; don't quietly change production source while reviewing.

## First action now

Read `artist-scene-studio/README.md`, the files under your ownership, current PRs and latest comments on issue #440. Post your initial **[MISE A10][CLAIM]**, create `agent/mise-10-qa`, implement an unblocked P0 task immediately, test as deeply as available and submit a PR. Then continue to the next scoped task; at a session limit persist a handoff, not a promise of asynchronous work.
