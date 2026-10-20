# NIGHT Browser — Agent 03 worklog

Updated: 2026-10-20. Branch: `agent/browser-03-tabs`; PR: #429 (draft, targeting `feature/night-browser-multi-agent-20261020`).

## Implemented in this branch

- B-003: Android Keystore AES-GCM + AtomicFile snapshot storage for regular Gecko tabs. Records stable IDs, Journey association, page title, URL, last Journey node and Gecko session state. Private mode excluded from persistence. Session state contains possible form data, so it must not be placed in plaintext JSON/preferences.
- Regular-tab restoration and back/forward state restoration using GeckoSession.ProgressDelegate.onSessionStateChange and GeckoSession.restoreState, with URL fallback on invalid/unavailable state. Persist at tab updates + Activity stop and on destruction.
- Separate regular/private grids and genuinely private GeckoSessionSettings with no Journey writes or persisted previews; private popup and Peek inherit private settings. Private tabs cannot silently create Bookmarks/Time Machine PDFs.
- Normal tab close/undo with five in-memory recently closed normal tabs; no incognito undo cache.
- Free in-memory bitmap previews on close/low-memory callback; close Gecko sessions on Activity destruction, including rotation.

## Ownership and integration

Agent 3 owns `TabSessionStore.kt`, `ci/agent03-smoke.py`, this worklog and `.github/workflows/night-browser-agent03.yml`.

**Shared-file changes for explicit Agent 1/4 review:** `BrowserController.kt` (tab session lifecycle, private-mode delegation) and `MainActivity.kt` (tab overview, snackbar undo, lifecycle). The alternative Omni UI branch PR #422 has overlapping `MainActivity.kt` changes. DO NOT blindly overwrite; integrate composables, selectors and menu in Agent 1's chosen UI shell. Agent 4 should review Gecko session delegate lifecycle and `onNewSession` privacy.

## Verification protocol

CI workflow `night-browser-agent03.yml` builds Android arm64 + x86_64 APK with Java 17 and Gradle 9.7.1. After a passing build, it boots API-36 emulator, loads two actual domains in different normal tabs, loads a domain in a private tab, force-stops/restarts the process, and checks normal tabs restore while private tabs do not. It uploads UI screenshots and crash logs. **Workflow, results and screenshots require actual review; do not report device-tested until jobs finish successfully.**

Still required: 10+ website manual playthrough; inspect actual screenshots and touch responsiveness at multiple form factors; verify normal versus private authentication cookies and localStorage isolation; encrypted restored back/forward + form state; 20-tab memory load; Android 8 compatibility; review potential performance cost of frequent encrypted state snapshots.

## Status at initial PR

- Implemented: **yes, unmerged draft**.
- Built: **CI checks initiated, pending**.
- Installed: **not yet confirmed**.
- Browser interaction test: **CI scenario pending; not certified**.
- Security/profile isolation: **code-level separation present; live cookie leak test outstanding**.
- Integrated: **no; Agent 1 review required**.

## Wave 2 continuation — 2026-10-20

**Current head:** `674b4423b06f615e57d0812fb3213cf696002a26`; PR #429 remains an open draft against browser integration. Changes are on the **existing** A3 specialist branch; no detached duplicate module has been created.

- A3-owned `TabPersistencePolicy.kt`, `TabPersistencePolicyTest.kt`: constrain restored normal-tab URLs to safe HTTP(S), preserve existing percent escapes, remove URL userinfo + fragments in encrypted display metadata, reject control characters/local schemes/oversized URLs and keys; cap Gecko-state length.
- `TabSessionStore.kt`: enforce a maximum encrypted manifest size before decryption, bound metadata fields, dedupe identities, validate active-tab identity and retain complete-or-omitted Gecko session state; never truncate state JSON.
- A3-owned `TabPreviewBudget.kt`, tests and `TabOverview.kt`: cap 24 remembered pixel previews / 14 MiB, prioritizing active/recent tabs. Never force-recycle a bitmap still referenced by a Compose frame. **The screenshot capture path in shared MainActivity still needs A1/A4 ownership-coordinated review and stale-preview invalidation.**
- `ci/agent03-smoke.py`: accepts both A2 Home's accessibility action `Go to address or search` and the web omnibar's `Go`. Actual previous emulator failure was an out-of-date selector (run `39377895311`) despite successful Gradle compile.
- Kotlin CLI independently compiled and ran policy / preview-budget sanity checks locally, **not** the Android application/JUnit Gradle build.
- This latest commit was **not** Android CI-verified, installed, visually tested or integrated at reporting time. Previous API36 emulator failed before the 10-tab/private flow completed.
- A1 owns combined `MainActivity.kt`, A4 Gecko controller; A3 did **not** write these shared files in Wave 2. Coordinate on issue #460. Known blocking controller issues: `onNewSession` must return unopened Gecko popup sessions; `onStop` must guarantee queued encrypted manifest writes are flushed before process exit for reliable restore. A3's private-mode flag must propagate to A5 Journeys, A6 downloads, A7 archives, A8 AI and A9 Peek.
- Next: A1/A4 conflict-safe integration; run actual API26/API36 20-tab navigation/restore/cookie-isolation screenshots + memory sample with A10 and fix discovered defects.
