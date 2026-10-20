# WAVE 2: NIGHT Browser Agent 7 — Versioned Time Machine with trustworthy offline behavior

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-07-time-machine` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 7"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #428 offers versioned PDF captures with quotas/search/export/deletion, but still unmerged. Integrate its UI and persistence safely, ensure actual PDF save, thumbnails/metadata, size quota and cleanup do not lose user files. Go beyond PDFs only where technically demonstrable: offline **readable saved pages** that load without network may be useful; label them accurately. Do NOT claim interactive page history/session restoration from a PDF. Add version comparison/sorting and robust missing/corrupted file handling. Avoid silently archiving sensitive/private pages.

### Acceptance (real APK / real websites)

On API26/API36 save twice, open offline after network disconnect, export via SAF, delete one version and confirm disk cleanup, restart and check older migration; screenshots and size accounting.

### Dependency coordination

A4 Gecko save hook, A6 Library file manager, A3 private exclusion, A1 route integration.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 7: Time Machine archival and versions

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-07-time-machine`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-07-time-machine` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Current Time Machine only saves PDF copies. Implement useful versions of a saved page with timestamps, deletion, browsing/search, export and storage limits. Clearly label PDF snapshots as PDF; only call a feature offline interactive page restoration when actual HTML/resources and state work offline. Handle missing apps/files, exceptions and storage quota. Never silently capture sensitive/private pages.

## Evidence required before reporting completion

Save two versions from real webpage; open offline, compare timestamps, delete files/metadata, restart, test missing PDF viewer and error states. Show screenshots and honest feature labels.

## Collaboration / shared-file rules

Share storage API with Agent 6, Gecko capture hook with Agent 4; schedule UI integration with Agent 1.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
