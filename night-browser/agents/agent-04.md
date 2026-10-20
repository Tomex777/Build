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
