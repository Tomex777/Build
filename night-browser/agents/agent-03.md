# ON "CONTINUE" — RESUME EXISTING PR, UNBLOCK THE COMBINED APK

Read `night-browser/docs/integration-resume.md`, latest Issue #460 messages and the current unified integration candidate #468. Continue your existing open agent PR/branch; **do not create another unmerged copy** unless Agent 1 explicitly requests a separate narrow bugfix. Verify your module is wired in the real combined browser, address actual build/API26/36/runtime/security errors, coordinate shared-file edit ownership, request review/merge and supply tested evidence. Agent 10 owns independent black-box QA of the combined SHA; a report-only workflow's green status is not acceptance. No promises of background work after chat ends.

---

# WAVE 2: NIGHT Browser Agent 3 — Stable tab grid, process restoration and true incognito

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-03-tabs` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 3"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #429 includes encrypted manifest, Gecko SessionState and private mode work **not merged**. Rebase to current integration and coordinate with A4 to land actual session APIs. Audit real pixel-preview lifecycle: never stale/black after switching, enforce bitmap memory caps, dispose on close/trim, handle orientation and Gecko compositor failures. True private sessions require no permanent cookies/history/Journey/TimeMachine/AI indexing leakage and clean isolation from regular tabs, not merely a black tab label. Implement restore active tab, original tab list and proper undo after app force-stop and new-process launch. Support 10–30 tabs on modest Android hardware.

### Acceptance (real APK / real websites)

Record regular/private sign-in cookie isolation, closed-tab persistence, process death, 20-tab switches, low-RAM memory/perf with screenshots and logs on emulator. Secure sensitive state and prevent private history upload.

### Dependency coordination

A4 holds Gecko controller modification window; A1 shell wiring; A5 private Journey exclusion; A8 AI privacy controls.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 3: Tabs, thumbnails, incognito and restoration

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-03-tabs`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-03-tabs` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Implement polished Chrome-style two-column tab preview cards showing genuine rendered site pixels; audit existing capturePixels prototype for compile/runtime API compatibility, bitmap lifetime and private-data leakage. Add tab switch/close/undo, responsive grid, robust session restoration after process death, low-memory behavior and real private mode with isolated cookies/history/storage—not a cosmetic switch. Keep active Gecko sessions stable during Compose recomposition.

## Evidence required before reporting completion

Actually browse 10+ sites, switch/close/reopen/restart, inspect tab previews; verify private login cookies do not leak into normal tabs or persisted history. Record memory and crash testing.

## Collaboration / shared-file rules

Coordinate Gecko runtime/session hooks with Agent 4; Agent 1 owns conflicting MainActivity wiring.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
