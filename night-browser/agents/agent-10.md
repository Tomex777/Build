# WAVE 2: NIGHT Browser Agent 10 — Independent app-wide Android quality and security gate

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-10-qa` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 10"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #426 contains ADB harness, but actual latest integrated APK hasn't been certified. Test **the combined integrated SHA**, not only isolated agents. Write/execute at least 20 end-to-end tasks using real Gecko page loads and human-like taps on API26 and API36: new tab, history tree, private/nonprivate cookie tests, multiple file types download/open/upload, photos/permissions, AI consent, offline Time Machine, Site Studio origin isolation, screenshot previews, authentication, media, background/low memory and orientation. Measure cold start, scroll responsiveness, tab switching and memory on lower-end emulator. Collect carefully redacted screenshots/UI XML/Traces. Report exact crashes and ensure owners fix and retest; do not greenwash unsupported backend providers.

### Acceptance (real APK / real websites)

Release matrix with SHA, APK artifact, device/API/viewport, scenario steps, pass/fail/not-run, real screenshot/log reference, regressions versus baseline; independent QA rejects a feature that only compiles. Physical-device limitations explicitly marked.

### Dependency coordination

Coordinate test workloads with A1/A2–A9; own QA tools not shared production source. Request bug patches and re-test actual merged head.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 10: Independent QA, security and performance

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-10-qa`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-10-qa` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Be the impartial browser tester, not a fake pass/fail reporter. Build/update Android emulator + ADB/UIAutomator/Espresso flows and QA ledger for real-world pages. Test Home/omnibar, 10 tabs and preview images, session restore, incognito cookie separation, Journey branches, bookmarks/downloads, Peek, PDFs/Time Machine, permissions, file uploads, Universal Search opt-in and Site Studio origin safety. Capture screenshots/logs/perf from running app and reproduce failures with exact steps.

## Evidence required before reporting completion

Test small and larger Android profiles, rotation, offline/slow network, process death, real interactions and regression after merge. Clearly distinguish physical phone vs emulator testing and not-executed tests.

## Collaboration / shared-file rules

Report defects to each feature owner and Agent 1; own tests/reports, not other agents' implementation branches.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
