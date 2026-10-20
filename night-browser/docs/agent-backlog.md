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

## 2026-10-20 — Agent 1 coordination checkpoint (real GitHub actions)

- **A1 PR #425 MERGED into integration** at `e4125a7`: tests + Android build guard passed run `39357615262`. Guards are not app UX tests.
- **A2 PR #430 MERGED** at `25a4b35`: custom NIGHT start page, editable speed dials, first-party favicon validation; isolated Android build/tests passed. UI was not wired until next step.
- **A1 PR #437 MERGED** at `184ef33`: wired `NightStartPage`, removed redundant bottom address row on Home and non-web panels. Both latest-head Gradle build CI jobs passed. Emulator/manual touch verification still needed.
- **A1 draft PR #439 — integration assembly** at `agent/browser-01-feature-assembly` (base: browser integration): staged original, unmodified added files (33 total at copy) from A6 #432 `ef56e6d`, A8 #431 `4313034`, A9 #434 `6e6dc0b`, A10 #426 `51c30f4`. These are **assembly inputs**, not operational integrations: new Library/AI/Peek/Site Studio screens are not wired to the app; A10 QA is genuine emulator acceptance infrastructure. PR #439 stays **draft** until build and touch QA are green.
- **All nine specialist agent branches now exist** and corresponding PRs #426–#434 opened. Agents' reported source changes are distinguishable from passed device QA. #427 A4 engine build passed, emulator cancelled; #429 A3 tabs build passed, emulator failed on missing `New tab` UI target; #428 A7 archive emulator failed; #426 A10 API26/36 emulator actually exercised existing app and found missing navigation/landscape locator, tab restore and incognito. A1 posted review feedback on #426, #427, #428, #429 and #432. #422 old alternate Omni branch diverges and must not overwrite current integration.
- **Coordinator merge order for shared code:** A4 Gecko controller and platform host before A3 session persistence/private mode; A5 visit tracker/JourneyStore after Gecko hooks; A6 Gecko response streaming and private download confirmation; A7 archive MainActivity callbacks; A9 Site Studio origin bridge and Peek; A8 actual consent/search action. A10 runs both API 26/36 after each combined candidate. All features remain blocked for production until real interactions pass.
- **No merge to main** or production APK release is authorized or claimed.
