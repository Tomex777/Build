# ON "CONTINUE" — RESUME EXISTING PR, UNBLOCK THE COMBINED APK

Read `night-browser/docs/integration-resume.md`, latest Issue #460 messages and the current unified integration candidate #468. Continue your existing open agent PR/branch; **do not create another unmerged copy** unless Agent 1 explicitly requests a separate narrow bugfix. Verify your module is wired in the real combined browser, address actual build/API26/36/runtime/security errors, coordinate shared-file edit ownership, request review/merge and supply tested evidence. Agent 10 owns independent black-box QA of the combined SHA; a report-only workflow's green status is not acceptance. No promises of background work after chat ends.

---

# WAVE 2: NIGHT Browser Agent 2 — Home, omnibar and visual design mastery

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-02-home` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 2"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PRs #430 and #437 already integrated redesigned Home. **Do not recreate old Home.** Review open Agent 2 PR #441, finish installed/interactive UX verification and polish complete browser shell: URL/search focus, IME, safe paste/clipboard, favicons/quick-access edit/persistence, search suggestions with user privacy, landscape/tablet, PDF/fullscreen/media insets, progress and error screens. Compare against Omni Browser inspiration but write original code; don't copy GPL-3.0 assets. Remove unnecessary double bars, generic filler, overlapping status/navigation areas, and broken more-menu targets. Ensure start screen useful with zero history and after many journeys.

### Acceptance (real APK / real websites)

Actual emulator API26/API36/screenshots narrow+wide; repeatedly add/edit/delete speed dial and reopen app, keyboard/paste/search, back/forward, rotated layout and site permission prompt UI. Changed visual design must be visibly better and functional.

### Dependency coordination

A1 controls MainActivity.kt shell wiring; A3 tab overview; A4 Gecko navigation; A9 per-site UI.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 2: Home, Omnibar and navigation UX

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-02-home`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-02-home` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Replace the rejected oversized-wordmark Home with a polished Omni Browser-inspired start page. Inspect current quick-access implementation, improve it with functional user-editable sites, favicons, recent visits, Journey entry and good empty states. Make the search/address field behave correctly on keyboard, clipboard, URL or search terms, focus and submit. Clean up toolbar/navigation, no duplicate controls, avoid Android status/navigation bar collisions. Use original Compose assets rather than copying Omni GPL code.

## Evidence required before reporting completion

Install browser. Visually compare at small/large width. Use shortcuts, enter URLs and queries, navigate back/forward, rotate and use keyboard. Share actual screenshots and working behaviour, not static mocks.

## Collaboration / shared-file rules

Agent 3 owns tab grid; Agent 4 owns Gecko controller. Extract Home UI into separate composables; reserve MainActivity integration with Agent 1.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
