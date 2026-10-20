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
