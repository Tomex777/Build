# NIGHT Browser — Multi-Agent Execution Ledger

Updated: 2026-10-20, Agent 1 audit. Base under review: draft PR #384 (`feature/night-browser-prototype-20261019`). Integration work: `feature/night-browser-multi-agent-20261020`. No ten-agent runner was launched by creating this file; ownership below is a **work allocation**, not a claim that these agents are currently active.

## Verified baseline (source inspection; device testing outstanding)

- App: `night-browser/`, Kotlin/Compose/GeckoView, package `com.night.browser`, minSdk 26.
- Gecko tabs and navigation, persisted Journey nodes, link-long-press Peek, bookmarks and PDF-only saved pages have code implementations.
- The original prototype has a text-only tab list; the integration branch has now added two-column thumbnail cards and pixel capture. Builds passed, but tab-heavy real interaction and image quality are not independently verified.
- The rejected original large-wordmark Home has a compact start-page revision on the integration branch. The separate Omni-inspired PR #422 offers another version; UX selection, integration and interaction tests are outstanding.
- Library is presently only bookmarks; Time Machine currently saved PDF copies, not interactive page restore.
- AI Universal Search, Site Studio, downloads manager, robust permissions/file inputs, true incognito, tab restore: **not delivered**.
- CI contains test/build and emulator screenshot workflows. A genuine install/playthrough and screenshots after the redesign remain necessary.
- Omni Browser UX reference is GPL-3.0: use inspiration/original work unless a deliberate compliant fork is selected.

## Task ledger (not active staffing status)

| ID | Owner | Priority | Status | Work and proof of done |
|---|---|---|---|---|
| B-001 | Agent 1 | P0 | Active: guard + ownership audit PR in progress | Preserve ABR; track ownership, integration, branch and CI status, provide reviewable APK. |
| B-002 | Agent 2 | P0 | Integration has preliminary Home; specialist unverified | Replace Home with Omni-inspired speed-dial/start page, well-balanced navigation and focus. Screenshot + tap walkthrough on Android phone sizes. |
| B-003 | Agent 3 | P0 | Integration has preview cards; session/private mode incomplete | Chrome-like 2-column tab cards with real page thumbnails; close/select/undo/tab count; session restore; properly isolated incognito. Tested with 10+ real tabs and app restart. |
| B-004 | Agent 4 | P0 | To implement | GeckoView production reliability: file inputs, downloads, external intents, site permissions, popups/dialogs, crash handling, certificate/security indicators. Verify on real websites. |
| B-005 | Agent 5 | P1 | Prototype exists | Correct parent-linked Journey branching (back/forward and navigation relationships), tree editing/search/resume. Multi-branch navigation test after app relaunch. No AI dependency. |
| B-006 | Agent 6 | P0 | To implement | Library with bookmark organization, history, search, downloads statuses and robust file handling; retryable downloads. Real media/file scenarios. |
| B-007 | Agent 7 | P1 | PDF alpha exists | Versioned Time Machine, delete/export, storage management; clearly distinguish PDF from true offline snapshot. No false restore claims. |
| B-008 | Agent 8 | P1 | Unimplemented | Real AI Universal Search with opt-in provider/on-device model, privacy policy, permission UX, truthful answer grounding and latency/cost tests. No dummy keyword substitute. |
| B-009 | Agent 9 | P1 | Peek alpha exists | Speed/UX Peek upgrade, safe per-origin Site Studio with rollback and privacy scope, adaptive memory handling. Test cross-site and process death. |
| B-010 | Agent 10 | P0 | To execute | Independent emulator + physical-device UI test, real sites, slow/offline navigation, uploads/downloads, security prompts, login, screenshots, traces and regression retests. |
| B-011 | Agent 1 | P0 | Blocked: no specialist READY PRs | Merge only passing + interaction-tested agent branches, rerun combined APK and regression test before proposing merge to main. |

## Agent file ownership — split before parallel development

- A2: start page + toolbar composables after extract from `MainActivity.kt` to e.g. `ui/HomeScreen.kt`.
- A3: tab overview and tab persistence modules. Coordinate controller changes with A4.
- A4: Gecko session/controller, delegates, manifest and browser-permission bridges. Own controller edit lock.
- A5: Journey graph/store and Journey UI; coordinate shared data store updates with A6/A7.
- A6: Library/Download service and corresponding UI.
- A7: archival service and Time Machine UI.
- A8: search provider/index interfaces and UI integration hooks, with explicit opt-in.
- A9: Peek/Site Studio independent UI/services, coordinate shared Gecko hooks with A4.
- A10: test automation and QA output; no edits to production code except reproduction fixtures.

## Immediate execution order

1. Extract UI sections safely, build **new Home** and **visual tabs** before new vanity features.
2. Stabilize GeckoView browsing, sessions and web permissions; add real downloads.
3. Improve Journeys, Library and Time Machine while respecting the existing data model.
4. Integrate genuinely useful AI Universal Search and Site Studio without violating privacy or licensing.
5. Repeat end-to-end **device interaction** QA and fix defects until smooth.

## Record updates here, not only in chat

For each new implementation add: `Task ID | owner | worktree/branch | commit/PR | build result | install result | live scenario and screenshot | outstanding defect | next action`.

### Latest activity

- 2026-10-20: Inspected PR #384, existing `MainActivity.kt`, `BrowserController.kt`, JourneyStore, CI; created scoped `AGENTS.md` and ledger in isolated branch. No assertion that ten autonomous agents are now executing.

- 2026-10-20: Added root `AGENTS.md`, short-chat launcher and ten role briefs under `night-browser/agents/`. **Documentation only:** user must launch the 10 separate agent chats; existence of role files does not mean the agents are actively coding.

## Agent 1 live branch audit — 2026-10-20

**Truthful staffing:** Agent 1 has begun work in this isolated conversation. Assignments for Agents 2–10 are invitations, **not** evidence that those chats or any parallel agent runner are running. Do not mark them active before receiving branch/PR evidence.

### Branches and PR integration decisions

| PR | Base → head | Verified state | Decision / next action |
|---|---|---|---|
| [#384](https://github.com/Tomex777/Build/pull/384) | `main` ← `feature/night-browser-prototype-20261019` | Open **draft**, initial browser. | Keep draft. Do not merge unfinished browser to main. |
| [#421](https://github.com/Tomex777/Build/pull/421) | prototype ← `feature/night-browser-multi-agent-20261020` | Open **draft**. Head `017f492b` compiled via GitHub Actions runs 39353032137 / 39353027136; real tab-heavy/browser/device QA not certified. | This is the shared browser integration branch; require feature-ready PRs into it. |
| [#422](https://github.com/Tomex777/Build/pull/422) | prototype ← `feature/night-browser-omni-inspired-ui-20261020` | Open **draft**, **diverged** from #421 and overlaps Home/Tabs/MainActivity. Emulator visual run 39353721616 passed on commit `944cb4c`, not the latest `8d2b670` head. PR also changes identity from `com.night.browser` to `com.nice.browser`. | **Do not blindly merge** #422. Retarget/rebase to browser integration, resolve UI conflicts with Agents 2/3 and settle identity/installation migration; rebuild and re-evaluate screenshots + actual taps on the final head. |
| Agent 1 | integration ← `agent/browser-01-coordinator` | Python manifest/package regression guard, 7 tests and Android build workflow now run for PRs targeting browser integration. Code authored; CI and emulator evidence pending on PR. | Run checks, review, merge only after passing; never claim Android device test from Python guard. |

### Ownership / shared-code locks

- **Agent 1 owns** `night-browser/docs/agent-backlog.md`, integration contracts, and coordinator gate. Agent 10 independently owns gameplay/UI QA workflows; coordinate any shared CI modifications.
- **Agent 2 owns** Home and address/toolbar visual design, after extracting its composables into its own file. **Agent 3 owns** tab-grid composables plus tab restore/private-session metadata. Both currently coexist inside `MainActivity.kt`: **do not edit this file simultaneously**; nominate Agent 2 to extract Home first and Agent 3 to extract Tabs after syncing branch.
- **Agent 4 owns** `BrowserController.kt`, Gecko delegates, manifest and runtime hooks until extracting interfaces. Agents 3 and 9 should use new supporting files and coordinate necessary controller signatures before changes.
- **Agent 5 owns** `JourneyStore.kt` and tree consistency. Agents 6/7 must isolate Library/archive stores instead of editing JourneyStore simultaneously.
- **Agent 8 owns** opt-in provider/indexing architecture; no unconsented browsing-data transmission or pretend AI.
- **Agent 9 owns** Peek/Site Studio per-origin modules; work with Agent 4 on Gecko hooks.
- **Agent 10 owns** independent multi-device, real-site UI/functional/security QA evidence; does not claim other agents' builds as testing.

### Acceptance gate

1. PR targets **`feature/night-browser-multi-agent-20261020`**, not `main` or the old prototype, with a distinct agent branch. Preserve all prior work before merging.
2. Shared-file ownership identified; compare affected files against #421 and #422 to prevent lost fixes.
3. Automated guard + JVM unit tests + **real Android compilation** pass on the actual merge head; app IDs, FileProvider authority, Kotlin packages and Android SDK must be internally consistent. Package choice remains under project-owner review due to #422.
4. **Install** the resulting APK on suitable Android emulators/devices, **interact** with browser pages, forms, multiple tabs, Journey branches, Peek, downloads and permission dialogs; attach redacted screenshots/logs and record failures.
5. Coordinator records branch, commit, CI run, actual install/test evidence, remaining defect and next action. Do not call it integrated/done until combined app is tested.


## WAVE 2 integration and whole-app quality priorities (2026-10-20)

**This table supersedes unverified "unimplemented" statuses in the first-wave snapshot:** Integration `feature/night-browser-multi-agent-20261020` has merged #425 coordinator, #430 Home components, #437 Home shell. **Open draft #439** partially assembles new Agent 6/8/9/10 files without production wiring. **Open specialist #426–#434**, plus Agent 2 #441 QA; statuses must be rechecked before action. Build workflows on #439 were queued at review; not accepted as a working full app. Issue: [#460](https://github.com/Tomex777/Build/issues/460). First mission: integrate existing outstanding work, not create parallel replacements.

| Agent | Wave-2 immediate quality task | Evidence required |
| --- | --- | --- |
| A1 | Coordinator: produce ONE working browser APK | Actual integrated app compile/install/site tests, not just PR exists |
| A2 | Home, omnibar and visual design mastery | Actual integrated app compile/install/site tests, not just PR exists |
| A3 | Stable tab grid, process restoration and true incognito | Actual integrated app compile/install/site tests, not just PR exists |
| A4 | Production GeckoView site compatibility | Actual integrated app compile/install/site tests, not just PR exists |
| A5 | Beautiful correct automatic branching Journeys | Actual integrated app compile/install/site tests, not just PR exists |
| A6 | Real downloads and useful Library | Actual integrated app compile/install/site tests, not just PR exists |
| A7 | Versioned Time Machine with trustworthy offline behavior | Actual integrated app compile/install/site tests, not just PR exists |
| A8 | User-consented AI Universal Search, genuinely useful | Actual integrated app compile/install/site tests, not just PR exists |
| A9 | Peek, per-origin Site Studio and adaptive browsing | Actual integrated app compile/install/site tests, not just PR exists |
| A10 | Independent app-wide Android quality and security gate | Actual integrated app compile/install/site tests, not just PR exists |

Communication posts: `[BROWSER A#][CLAIM|DESIGN|BLOCKED|HANDOFF|READY|VERIFIED|INTEGRATED]` on Issue #460. Agent1 owns cross-file integration lock; preserve original package/SDK. At session end record exact last commit/PR/CI error, APK/test evidence, blocker, next command. Truthful "not integrated / not live model-tested / not installed" labels required.


## Wave 2 A1 live integration — 20 Oct 2026 (branch candidate, NOT released)

| Task | Owner | Branch / PR | Source commit | Compiled tests | Installed | Live-site QA | Next action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B-001/B-011 combined assembly | A1 | `agent/browser-v2-01-coordinator` / [#468](https://github.com/Tomex777/Build/pull/468) | `dd24d01f` from #439 + final A6 #432, `f2e5a1e` A5 #433 + A7 #428 integration | **Queued** workflows 39384620540/20603/20491/20601/20582/20623/20633. Prior assembly #439 compiled, but this combined head has **no verified build result yet** | **Not yet verified** | **Not yet verified** | Inspect compile/unit-test logs, fix interface regressions, run A10 API26/API36 touch/web scenarios; do not merge on source inspection alone |
| B-002 Home QA follow-up | A2 | [#441](https://github.com/Tomex777/Build/pull/441) | `72d369f` | APK compilation passed 39378252910 | Emulator app launch occurred | **FAILED**: screenshot assertion landed in Android keyboard Theme settings, not NIGHT Home; no valid full Home pass | A2/A10 fix deterministic focus/IME handling, rerun; only then evaluate merge |

**Shared-file reconciliation:** A1 has the active assembly edit lease for `MainActivity.kt` and cross-module `BrowserController.kt` hooks on #468. A3/A4 own isolated Gecko/tab features, A5 owns `JourneyStore.kt` and graph, A6 owns download and Library services, A7 archive, A8 AI, A9 Peek/Site Studio, A10 independent QA. Exact current source/PR evidence and cross-agent requests are posted on [Issue #460](https://github.com/Tomex777/Build/issues/460). Main branch/release untouched. The original #439 remains a draft partial assembly and is not silently considered merged.
