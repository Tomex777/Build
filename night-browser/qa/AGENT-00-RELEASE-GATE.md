# NIGHT Browser · Agent 0 release-gate audit (2026-10-20)

## Scope and ownership

This is a **release-blocker triage** for the **existing** integrated browser candidate,
not another implementation wave. A1 owns coordinator PR #468
(`agent/browser-v2-01-coordinator`) and shared Activity/controller changes;
A2 owns Home, A3 tabs, A10 independent black-box device acceptance.
Agent 0 owns only the QA-contract repair draft PR #498. DO NOT merge to
`main`, sign a release, or represent a debug APK as fully accepted.

## Exact source and reproducible evidence

Unified candidate: `8cc731a83ee5788db93122d70f267f3f3c6962f2`.
Android Prototype [39440950026](https://github.com/Tomex777/Build/actions/runs/39440950026)
**PASS**: `:app:testDebugUnitTest :app:assembleDebug`; generated
`night-browser-android-arm64-debug`, artifact ID `12274422010`.
This proves an APK was built, **not** successful navigation/privacy/use.

Wave-2 emulator [39440949972](https://github.com/Tomex777/Build/actions/runs/39440949972)
actually installed Android builds and ran ADB; the CI job itself is **report-only SUCCESS**.
The **in-app case verdict** must not be relabelled SUCCESS:

| API | PASS | FAIL | BLOCKED or NOT_RUN | Selected confirmed evidence |
|---|---:|---:|---:|---|
| 26 | 7 | 10 | 22 | Cold launch, Quick access, 10 tab creation, landscape screenshot 1920x1080, force-stop tab count restore, PSS 151166 KB; C03/C04 Go absent. |
| 36 | 4 | 10 | 25 | Cold launch, Quick access, PSS 233941 KB; C03 Go absent, C04 typed URL not reflected, C16 counter inaccessible. |

Independent A10 [39440950376](https://github.com/Tomex777/Build/actions/runs/39440950376):
**device acceptance failed both API 26 and 36** after compiling. Screenshots
from installed emulator are real but are **not proof that visual UX passed review**.

## Specific failure-contract findings

* `NightStartPage` exposes a custom BasicTextField and only shows the
  `Go to address or search` arrow *after* nonempty input. `MainActivity`
  has a different `Go` control only on WEB/private; generic scripts assuming
  `Go` on Home are incorrect. The Gboard Theme popover may also steal focus;
  do not interpret a keyboard overlay as the browser's UI.
* Compose `TabPanel` labels counters as `Regular  N` and `Private  N`.
  Testing for an unlabelled decimal count is invalid. Conversely, a missing
  or inaccessible labelled count after the new selector is an actual app issue.
* Normal-mode `More` only offers `New tab`; **Tabs > Private > New private tab**
  is the legitimate incognito entry. Do not mark merely seeing an icon as
  privacy verified. Cookie positive/negative controls are required.
* `JourneyScreen` heading is `Journeys`, not `My browsing`. `PeekOverlay`
  dismiss control is `Dismiss preview`, not `Close preview`.
* `saveAsPdf` commits asynchronously: wait a *bounded* interval for an actual
  archived row. A saved PDF list alone does not verify offline opening/bytes.
* Wave2 C23–C25 explicitly stop short of byte-verified upload/download; do
  not relabel these as PASS when navigation begins working.

## Changes drafted (A0 fix-only PR #498)

`night-browser/qa/wave2_blackbox.py`: package-scoped editable field selection,
real native Go/IME dispatch with **network hit required**, labelled tab counts,
bounded async PDF/title waits, correct Journeys/Peek controls, no Python XML
element truthiness mistakes. `night-browser/qa/acceptance.py`: equivalent
app-owned input and actual private-entry flow. Pure Python tests in
`night-browser/qa/test_a0_ui_contracts.py` cover counter and private path.

These changes have **not yet been run on an Android emulator at PR #498 SHA**.
Do not upgrade the acceptance gate based on source review.

## Next independent actions

1. **A10** reviews #498 and runs source-integrated APK at an **exact new SHA**
   on API26/API36. Publish UI hierarchy on C03/C04/C16 failure, fixture hit
   evidence, screenshots with Gboard onboarding dismissed, no passwords/cookies.
2. **A2/A3** address the user's genuine polish blocker on existing PRs:
   Home's tiny title + redundant Browser suffix / sparse top-heavy hierarchy,
   real populated HTTPS previews (not repeated black new-tab cards), compact
   private/regular visual affordance, 320dp/390dp/tablet/landscape touch.
3. **A1** integrates only reviewed QA changes into **existing** #468, not
   `main`. Rebuild, install and retest end-to-end; keep shared file locks.
4. **Release gate** requires verified real navigation, login/redirects, SAF
   upload and byte-verified downloads, private cookies/download history,
   20-tab restore, Journeys branching, Peek, Site Studio origins, offline
   Time Machine PDF, accessible responsive screenshots, and crashes/memory
   evidence. Missing scenarios remain BLOCKED/NOT_RUN.
