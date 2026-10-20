# WAVE 2: NIGHT Browser Agent 1 — Coordinator: produce ONE working browser APK

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-01-coordinator` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 1"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

P0: review PR #439 (draft module assembly) plus specialist #426–#434 and #441. Resolve compiled interfaces and shared files systematically (MainActivity.kt, BrowserController.kt, JourneyStore.kt, manifest, Gradle), not by wholesale replacing another agent's file. Integrate verified modules into current browser branch in small batches and verify the full build before the next merge. Close superseded branches only after confirming replacements. Demand actual Android API26/API36 install + live-site interactions before announcing feature integrated. Organize meaningful interface contracts and acknowledge dependency claims on Issue #460. Deliver cohesive navigation, library/search/site tools from one running shell, then coordinate QA/release candidate.

### Acceptance (real APK / real websites)

One installable integrated APK contains actual active modules; all major surfaces reachable; 20+ real-site scenarios and security/privacy gates tracked by A10; integration PR/commit, failing tests, screenshots and logs traceable. No production release or main merge without owner approval.

### Dependency coordination

Own branch integration and shared UI/manifest wiring, not all specialist implementation; special attention to #439 partial assembly and blank disabled screens.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 1: Lead coordinator and integration

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-01-coordinator`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-01-coordinator` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Inspect PRs #384/#421, all agent branches and current code; maintain task ledger, ownership locks, dependency graph and priorities. Split the monolithic MainActivity where necessary to avoid conflicting work. Review agent PRs for real functionality, licensing/privacy/ABR and testing evidence. Merge each READY PR promptly into browser integration; run combined APK and real UI flows after merges. Do NOT merge to main without user approval.

## Evidence required before reporting completion

Ledger records owner, task status, branch, PR, evidence, blocker and next action; integration PRs actually reviewed and merged when ready; combined browser exercised. Do not claim agents are active when the user hasn't launched them.

## Collaboration / shared-file rules

Coordinate Agent 2–10. If no other chats active, do substantive safe code/refactor work rather than waiting.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
