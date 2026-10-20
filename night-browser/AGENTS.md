# NIGHT Browser — Agent Operating Rules (ABR)

## Agent N chat activation

In a NIGHT Browser chat, **"You are Agent N"** (N from 1 to 10) selects `night-browser/agents/agent-NN.md` on branch `feature/night-browser-multi-agent-20261020`. Immediately read `night-browser/agents/README.md`, that role document and `night-browser/docs/agent-backlog.md`, then begin actual work. The user does not need to paste the entire handoff. The chat still needs GitHub access and enough project context to locate the repository. These files do not spawn agent conversations automatically.


Scope: `night-browser/**` in `Tomex777/Build`. These instructions also apply to AI/coding agents updating this project's CI workflow. Read `README.md`, `docs/agent-backlog.md`, and the current code before acting.

## Mission and constraints

Build a polished, **real Android browser**, not a mockup, using native Kotlin, Jetpack Compose and Mozilla GeckoView. Keep the application ID/namespace `com.night.browser` and `minSdk 26` until the project owner explicitly changes them. Public brand is NIGHT Browser. Follow ABR: no redundant controls or filler copy, no default unstyled input fields, polished custom SVG/vector iconography, no fake features, responsiveness and accessibility, and actual emulator/device verification before claiming completion. System bars must not overlap controls; respect insets and browser fullscreen behaviour.

Preserve the user's approved product direction:
- Home/navigation UI: **Omni Browser-inspired** organization, not the rejected large-logo/journey-list Home; clean search and quick-access surfaces, no clutter. Chrome-like **two-column tab overview with genuine page thumbnails**, tab titles and close affordances; private tabs must be truly separate, not just tinted.
- Core GeckoView browsing must work on real sites: navigation, cookies, session restore, file upload, downloads, redirects, dialogs, permission prompts, authentication, autoplay policy and crash recovery.
- **Journeys** automatically form meaningful branching navigation trees using visit events and back/forward context. NO AI required for Journeys. Avoid treating raw visit logs as a finished tree UX.
- **Peek** must open a fast disposable preview without corrupting the current tab's history; allow promote to tab and dismiss.
- **Library** needs a useful bookmarks/history/downloads organization with real metadata, search, and progress; it is not merely a list of bookmarks.
- **Time Machine** should have explicit snapshots/versions and truthful labels. Existing PDF copies are *not* live webpage recovery or full offline archives.
- **Universal Search uses actual AI**, only with an explicit opt-in, consent and documented on-device or provider-backed architecture. Do not claim ordinary keyword matching is AI. Never silently upload browsing history, page text, searches or credentials.
- **Site Studio** must permit clearly scoped, user-controlled per-site CSS/JS modifications with safe toggles, previews, undo, and origin boundaries; no unsafe hidden arbitrary injection.
- Adaptive browsing, memory economy, privacy, error states, and battery use must be measurable.

### Omni Browser reference and license
Reference: https://github.com/REBEL-ROOT/omni-browser. Omni is **GPL-3.0**. Using its ideas/screenshots for comparative UX research is distinct from copying its source or assets. Prefer original Compose implementations inspired by the user's approved visual reference. If any Omni GPL source is copied or the project becomes a derivative, flag that decision for legal/license review and comply with GPL-3.0 redistribution, attribution and source requirements; do not quietly paste code or ship Omni branding.

## Ten independent responsibilities

1. **Coordinator/integrator**: architecture audit, task ledger, dependency graph, branch protection, PR review, conflict resolution, combined app QA, APK handoff. Do not replace specialist execution.
2. **Home + navigation UI**: Omni-inspired Home, speed dials, address search, compact toolbar/menu, accessibility, mobile/tablet layout. Own UI shell and shared visual tokens.
3. **Tabs, sessions and profiles**: Chrome-style visual tab grid with *real previews*, close/undo/reorder, tab restoration, truly private tabs/profiles, session isolation. Own tab lifecycle and session metadata, coordinate with Agent 4.
4. **GeckoView browser engine**: permissions, popups, TLS/security indicators, cookies, file chooser, WebRTC, intents, crashes, session restoration, safe external launches, navigation lifecycle.
5. **Journeys**: automatic visit DAG/tree and meaningful UI; back/forward branching, rename, merge, search, resume, export and data consistency. Must remain non-AI.
6. **Library + downloads**: bookmarks, reading list, history, downloadable items, MIME/download persistence, progress, pause/retry where supported, filters/search, Storage Access Framework.
7. **Time Machine**: versioned local page captures with clear distinctions between PDFs, offline page resources and actual recoverable state; view/delete/export and storage controls.
8. **AI Universal Search**: genuine opt-in model/provider integration, privacy controls, retrieval/indexing, meaningful ranking and grounded answers; disclose usage and never fabricate.
9. **Peek, Site Studio, adaptive tools**: disposable Peek UX, safe per-origin CSS/JS tools, sandboxing, reset/undo, site-specific settings and low-memory adaptation.
10. **Independent QA / security / performance**: two or more Android API/device profiles, real site tasks, screenshots and touch flows, permission/privacy tests, process-death and offline/slow network, battery/memory checks, regression reporting and retesting.

## Parallel execution and ownership

Use real parallel coding agents only in a runner that actually supports them. Distinct branches/worktrees per agent; no fake parallelism. Before editing shared files (`MainActivity.kt`, `BrowserController.kt`, `JourneyStore.kt`, Gradle, manifest), split interfaces or agree ownership in `docs/agent-backlog.md`. The coordinator merges only verified changes and maintains a current integration branch. Do **not** merge to `main` without the owner's explicit authorization.

Agent 10 tests other agents' implementations independently. The coordinator continuously allocates the next independent task when an agent finishes, within execution/session limits; keep the task ledger durable.

## Development / definition of done

`INSPECT → IMPLEMENT → BUILD → INSTALL → USE REAL SITES → SCREENSHOT/TRACE → FIX → RETEST → INTEGRATE`.

Build with Java 17 and `gradle -p night-browser :app:testDebugUnitTest :app:assembleDebug`, then install the architecture-appropriate APK and perform real UI interactions (not only screenshots of static empty pages). Test a tab-heavy workflow, real authentication and forms, media and downloads, low network and offline, browser exit/restart, rotations and insets. Keep logs and redacted screenshots/recordings as evidence. Don't claim device-tested or production-ready until verified.

Every feature report must distinguish **implemented**, **built**, **installed**, **interaction-tested**, **blocked**, and **integrated**. Passing CI != done. Record file ownership, branch, commit, tests and next action in `docs/agent-backlog.md`. If tooling cannot actually spawn agents or run emulators, state the limitation and deliver an executable handoff rather than claiming invisible workers are running.

## No regressions and security

No fake private mode, pretend AI, invisible permission approvals, silent user data uploads, broken navigation, leaking tab state, or misleading snapshot descriptions. Do not discard existing working Journeys/Peek/Library/Time Machine behaviours while improving them. Never commit API credentials, browsing content, user profiles, or private screenshots.
