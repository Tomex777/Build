# NIGHT Browser Wave 2 — Agent 4 Gecko engine handoff

**Date:** 2026-10-20  
**Review:** [#472](https://github.com/Tomex777/Build/pull/472) `agent/browser-v2-04-engine` → `feature/night-browser-multi-agent-20261020`  
**Related first-wave specialist:** [#427](https://github.com/Tomex777/Build/pull/427) (not independently merged; new PR #472 supersedes this source integration proposal)  
**Coordination:** [#460](https://github.com/Tomex777/Build/issues/460)

## Changed source

- `BrowserController.kt`: preserves old Gecko page/tab public API while adding correct unopened popup GeckoSessions, lifecycle recovery, external navigation policy, TLS state, safe errors and `downloadHandler: ((BrowserTab, WebResponse) -> Unit)?`. Gecko response streams must always be closed by the consumer. Existing stub closes streams rather than silently pretending they downloaded.
- `BrowserPlatformHost.kt`: Android/Gecko permission and prompt bridge, SAF picker for single/multiple files, user-consented site camera/microphone/geolocation and auth/JS/choice/beforeunload dialogs. `cancelPending()` rejects unresolved SAF and media requests on Activity destruction.
- `ExternalNavigation.kt`: only supported app schemes can be externally opened after explicit user gesture and Android confirmation; even `isDirectNavigation=true` is not enough. Script redirects and intent/file/custom URL types blocked.
- `BrowserErrorPage.kt`: local dark error page for DNS/network/cert/safe browsing errors, strict CSP, no arbitrary URL string HTML interpolation.
- `MainActivity.kt`: minimal mandatory constructor/host wiring, teardown cancellation and **proposed** site information/permission reset UI on current integrated shell. A1 owns final shared-file merge; do not wholesale overwrite shell/UI work.
- `AndroidManifest.xml`: optional camera/microphone and location permission declarations. No broad storage access and no mandatory hardware features.
- JVM tests: `ExternalNavigationTest` + `BrowserErrorPageTest` including top-level external URI without user activation.

## Integration contract

1. **A1 exclusive MainActivity/assembly lock.** Incorporate `BrowserPlatformHost(this)` before Activity STARTED and call `cancelPending()` during teardown. Reconcile the proposed site-information menu/dialog with current shell instead of overwriting other UI changes. Maintain package `com.night.browser`, minSdk 26.
2. **A3 session/private restore.** Add `isPrivate` to `BrowserTab` and real Gecko private settings, preserve encrypted nonprivate session restore, call session-persistence hooks. An A3 private popup must inherit its opener's private setting; current A4 code alone lacks that integration. Private tabs may not record Journeys, persisted screenshots or downloads.
3. **A5 Journeys.** Reuse `JourneyVisitTracker`, delayed/final successful navigation commits and pause semantics; direct `store.record` from this source must be replaced when merging to avoid redirect/reload noise and privacy regressions.
4. **A6 downloads.** Bind `browser.downloadHandler = { tab, response -> consent.offer(response, tab.isPrivate) }` **only after A3 supplies reliable isPrivate**. A6 owns streaming original `WebResponse.body` with consent, private SAF export and stream closure. Do not refetch signed/authenticated URLs or write private downloads to normal Library.
5. **A9 Peek/Site Studio.** API `newPeekSession(url, configure: (GeckoSession) -> Unit = {})` allows navigation/progress delegates to attach **before opening/loading**. A4 sets a fallback external navigation guard, calls `configure`, then enforces a denied-permissions delegate. If A9 replaces the navigation delegate it must install `PeekNavigationPolicy` to block custom app URLs. SiteStudioBridge attach/detach to be merged after A9 files are ready; only to normal nonprivate sessions, before initial navigation.
6. **A10 QA.** Android API26/API36 real interaction: HTTPS navigation/history & redirect, `input type=file` (single and multiple) with SAF, permission grant/deny/revoke, media/WebRTC, JS confirm/auth and popup, unsupported external intent, HTTPS certificate error without bypass, crash/kill/reload, downloaded stream owner + private isolation after A6 hookup.

## Verification truth table

| Stage | Status |
|---|---|
| Source committed | **Yes**, see PR #472 commits |
| Static regression tests authored | **Yes**, pure Kotlin policy/error unit tests |
| Gradle unit tests and debug APK | **CI pending** at writing; see actual PR Actions head before acceptance |
| Android app installed | **No** in this ChatGPT runtime (no Android SDK or ADB) |
| Real-site button/gesture tests | **No**, independent API26/API36 QA required |
| Visual screenshot/logcat reviewed | **No** |
| Integrated into shared codebase | **No**, Agent 1 final conflict review needed |
| Released | **No**, do not merge to main |

Potential risks: A3, A5 and A9 delegate/constructor overlaps; release cannot proceed until one combined controller preserves all privacy/journey/session hooks. Browser prompts still need actual live site validation; folder input and direct camera capture are not certified.
