# ON "CONTINUE" — RESUME EXISTING PR, UNBLOCK THE COMBINED APK

Read `night-browser/docs/integration-resume.md`, latest Issue #460 messages and the current unified integration candidate #468. Continue your existing open agent PR/branch; **do not create another unmerged copy** unless Agent 1 explicitly requests a separate narrow bugfix. Verify your module is wired in the real combined browser, address actual build/API26/36/runtime/security errors, coordinate shared-file edit ownership, request review/merge and supply tested evidence. Agent 10 owns independent black-box QA of the combined SHA; a report-only workflow's green status is not acceptance. No promises of background work after chat ends.

---

# WAVE 2: NIGHT Browser Agent 5 — Beautiful correct automatic branching Journeys

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-05-journeys` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 5"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #433 implements graph/tree and visit tracker but still awaits merge. Integrate cursor semantics with real Gecko Back/Forward and new-tab link context: A→B→C, return A→D should branch off A not C; suppress redirect/reload and fragmented duplicates, preserve parent references after trimming and migrations. Improve actual visual flow: pan/zoom/expand selection path, search filtering without losing ancestor context, reopen exact nodes, meaningful names, merge/delete/export, favicon/site title; no unbounded renders or mandatory AI. Treat sensitive/private URL parameters securely.

### Acceptance (real APK / real websites)

Real browser interactions and persistence: multi-branch graph after restart, 600+ nodes memory performance, search/rename/export/delete, no private data persistence; screenshot of graph at phone width and actual tree correctness tests.

### Dependency coordination

A4 Gecko events, A3 tab private flag; A6 Library uses separate storage; A1 integrates Journey screen.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 5: Automatic Browsing Journeys

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-05-journeys`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-05-journeys` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Journeys remain a NON-AI feature. Audit existing JourneyStore parent-node logic: correct branch parent when going back/forward, jumping nodes, redirects, reloading, new tabs and app restarts. Build a readable real graph/tree UI, search/filter, rename, resume and delete; migrate existing data rather than dropping history. Reduce duplicate noise and sensitive/private URL capture.

## Evidence required before reporting completion

Test A→B→C, back A→D, revisit another node, open child tab, restart; verify exact parents and visual tree on narrow Android screen. Assert private visits not stored.

## Collaboration / shared-file rules

Coordinate JourneyStore data model with Agent 6/7, Gecko navigation signals with Agent 4, and UI wiring with Agent 1.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
