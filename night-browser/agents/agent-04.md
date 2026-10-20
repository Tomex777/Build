# WAVE 2: NIGHT Browser Agent 4 — Production GeckoView site compatibility

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-04-engine` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 4"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #427 unmerged and conflicts with A3/A5 controller edits. Resolve actual Gecko delegates via small contracts: file chooser via SAF (single/multiple) from real websites, direct download streaming and MIME handling (A6), camera/mic/location user permission UI, JS prompts and auth, safe external deep links, popup/new windows, HTTPS state, crashes and content errors, redirects, cookies and WebRTC/media. Do not suppress insecure/TLS errors or pretend native page features work. Test hard modern sites (forms/login/media/files) beyond example.com. Preserve Peek/History/Incognito fidelity.

### Acceptance (real APK / real websites)

API26/API36 real-site interaction matrix: file upload/download both directions, several redirects, login flow, permission allow/deny/revoke, external app URL, broken certificate, tab crash/recovery and back/forward. Compile and use screenshot/logcat proofs. Never grant permissions silently.

### Dependency coordination

Own Gecko controller/delegate API; coordinate A3 private sessions, A5 history signals, A6 authenticated download streaming, A9 Site Studio extension with Agent 1.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 4: GeckoView engine, site compatibility and permissions

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-04-engine`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-04-engine` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Own GeckoRuntime/BrowserController and navigation/permission delegates. Handle popups/new sessions, external intent schemes, SSL/certificate error states, file upload chooser, media/WebRTC, user-initiated camera/mic/location permission prompts, JS dialogs, cookies, redirects, crash recovery, web compatibility and downloads hooks. Keep history/Journey and Peek behavior intact. Never silently approve powerful permissions or leak user data.

## Evidence required before reporting completion

Exercise real HTTPS sites, forms/auth redirects, upload, downloadable files, allow/deny/revoke permissions, popups, app resume and offline error screens on Android devices; log reproducible results.

## Collaboration / shared-file rules

Coordinate APIs with Agent 3 (sessions), Agent 6 (downloads), Agent 9 (Peek/Site Studio); you own shared BrowserController edits while granted by Agent 1.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
