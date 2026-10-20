# NIGHT Browser — Multi-Agent Execution Ledger

Updated: 2026-10-20. Base under review: draft PR #384 (`feature/night-browser-prototype-20261019`). Integration work: `feature/night-browser-multi-agent-20261020`. No ten-agent runner was launched by creating this file; ownership below is a **work allocation**, not a claim that these agents are currently active.

## Verified baseline (source inspection; device testing outstanding)

- App: `night-browser/`, Kotlin/Compose/GeckoView, package `com.night.browser`, minSdk 26.
- Gecko tabs and navigation, persisted Journey nodes, link-long-press Peek, bookmarks and PDF-only saved pages have code implementations.
- Existing tab overview is a one-column **text list** with no thumbnails; redesign for Chrome's visual 2-column style is required.
- Existing Home is a large NIGHT wordmark plus Journeys and bookmark lists; user expressly rejected it.
- Library is presently only bookmarks; Time Machine currently saved PDF copies, not interactive page restore.
- AI Universal Search, Site Studio, downloads manager, robust permissions/file inputs, true incognito, tab restore: **not delivered**.
- CI contains test/build and emulator screenshot workflows. A genuine install/playthrough and screenshots after the redesign remain necessary.
- Omni Browser UX reference is GPL-3.0: use inspiration/original work unless a deliberate compliant fork is selected.

## Task ledger (not active staffing status)

| ID | Owner | Priority | Status | Work and proof of done |
|---|---|---|---|---|
| B-001 | Agent 1 | P0 | Documentation started | Preserve ABR; track ownership, integration, branch and CI status, provide reviewable APK. |
| B-002 | Agent 2 | P0 | To implement | Replace Home with Omni-inspired speed-dial/start page, well-balanced navigation and focus. Screenshot + tap walkthrough on Android phone sizes. |
| B-003 | Agent 3 | P0 | To implement | Chrome-like 2-column tab cards with real page thumbnails; close/select/undo/tab count; session restore; properly isolated incognito. Tested with 10+ real tabs and app restart. |
| B-004 | Agent 4 | P0 | Implemented on isolated branch; CI/device QA pending | GeckoView production reliability: file inputs, downloads, external intents, site permissions, popups/dialogs, crash handling, certificate/security indicators. Verify on real websites. |
| B-005 | Agent 5 | P1 | Prototype exists | Correct parent-linked Journey branching (back/forward and navigation relationships), tree editing/search/resume. Multi-branch navigation test after app relaunch. No AI dependency. |
| B-006 | Agent 6 | P0 | To implement | Library with bookmark organization, history, search, downloads statuses and robust file handling; retryable downloads. Real media/file scenarios. |
| B-007 | Agent 7 | P1 | PDF alpha exists | Versioned Time Machine, delete/export, storage management; clearly distinguish PDF from true offline snapshot. No false restore claims. |
| B-008 | Agent 8 | P1 | Unimplemented | Real AI Universal Search with opt-in provider/on-device model, privacy policy, permission UX, truthful answer grounding and latency/cost tests. No dummy keyword substitute. |
| B-009 | Agent 9 | P1 | Peek alpha exists | Speed/UX Peek upgrade, safe per-origin Site Studio with rollback and privacy scope, adaptive memory handling. Test cross-site and process death. |
| B-010 | Agent 10 | P0 | To execute | Independent emulator + physical-device UI test, real sites, slow/offline navigation, uploads/downloads, security prompts, login, screenshots, traces and regression retests. |
| B-011 | Agent 1 | P0 | To integrate | Merge only passing + interaction-tested agent branches, rerun combined APK and regression test before proposing merge to main. |

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

- 2026-10-20 / Agent 4 / B-004: Branch `agent/browser-04-engine`, files `BrowserController.kt`, `BrowserPlatformHost.kt`, `ExternalNavigation.kt`, `MainActivity.kt` (one-line Activity host init), `AndroidManifest.xml`, navigation policy unit tests, and targeted PR CI workflow. Added SAF file inputs (single/multiple), explicit site/OS camera-mic-location prompts, JS alerts/confirm/text/auth/select, beforeunload, safe external-app confirmation, TLS-state capture, crash/kill reload, Peek permission denial, Gecko external-response hook for Agent 6 downloads. **Build: not yet confirmed. Install: not performed. Live interaction: not performed.** Pending: PR CI, emulator/physical-device testing of upload, real sites, permissions allow/deny, popup, auth, process death; hand off download response to Agent 6 and coordinate MainActivity merge with Agents 2/3.

- 2026-10-20 / Agent 4 B-004 follow-up: Fixed a **real Gecko popup failure**: `onNewSession` must return a newly constructed **unopened** GeckoSession; Gecko itself opens and navigates it (fixed in commit `28f1875f`). Added strict `WebResponse.body` cleanup when Agent 6 download service is absent or fails, and reset stale TLS state on navigation. Added local offline/network/certificate/safe-browsing error pages with strong Content Security Policy and no failed-URL injection, tracking original failed URL so browser Reload retries the actual website; unit tests ensure certificates cannot be bypassed. Replaced hanging emulator script (prior emulator jobs installed app and captured initial site but stalled on an unbounded UI Automator dump; then ran until cancellation) with bounded commands and explicit failure reporting for three real sites. Updated PR #427 and gave agents 3/6/9 integration warnings. **Prior build/test:** verify on commit `06135913` passed (run #39358926994); emulator run was cancelled, not passed. **Current head:** after these changes has no recorded GitHub check yet; no current APK compilation, installation or real site visual QA verification. Blockers: workflow scheduling/current-head run; Agent 1 integration of controller conflicts. Next: current-head build + emulator + physical Android, upload/camera/mic/location tests, screenshot inspection.
