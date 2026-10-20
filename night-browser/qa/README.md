# Agent 10 — independent browser QA

Owned files: night-browser/qa/** and .github/workflows/night-browser-qa.yml.
Source baseline: integration branch feature/night-browser-multi-agent-20261020
(2026-10-20); source inspection is NOT a substitute for installing an APK.

## Executable device test

This suite drives the **real NIGHT Browser APK** via ADB touches,
Android's accessibility tree and genuine emulator screenshots. It does not
fake website results or mark skipped scenarios as passing.

With Python 3, adb, a booted test emulator and a built APK:

    adb install -r night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk
    python3 night-browser/qa/acceptance.py --mode smoke --output night-browser/qa/results/local
    python3 night-browser/qa/acceptance.py --mode full --output night-browser/qa/results/full

WARNING: Each run clears com.night.browser application data. Use only a
disposable emulator/test profile, NEVER a personal device profile with saved
Journeys/bookmarks/logins. No test account credentials are used or recorded.
The smoke gate checks startup, Home, a real HTTPS site, bookmarks/Journey UI,
entry to Time Machine, ten tab creations, rotation, offline home and crash logs.
The full gate additionally enforces the intentionally unfinished contractual
requirements for session restoration and private-mode availability.

The GitHub workflow night-browser-qa.yml installs x86_64 debug APKs on Android
API **26 and 36** and runs the smoke gate on each. It publishes screenshots,
a machine-readable report.json, report.md and filtered crash lines as artifacts.
A smoke-green result only means the exercised flows passed; it is not a claim
of real-page rendering perfection, security validation or completion. Do not
commit any screenshot containing a real user's browsing data.

## Source-confirmed blockers as of the inspected integration branch

| ID | Finding | Source evidence | Owner | Severity | Execution |
|---|---|---|---|---|---|
| QA-01 | Tab session state is volatile: a new tab is created on every controller initialization, and no tab save/restore code exists. | BrowserController.kt init/newTab and no restore persistence | Agent 3 | P0 | Contract check included; not yet executed |
| QA-02 | No incognito/private tab entry, runtime/profile separation or cookie isolation. | MainActivity.kt menu and BrowserController.kt session setup | Agent 3 | P0 | Entry check included; cookie separation not yet executed |
| QA-03 | Browser does not bridge web input type=file, per-origin permission prompts or a real download manager. | BrowserController.kt delegates; MainActivity.kt and current library | Agents 4/6 | P0 | No simulated pass; fixture/device testing needed |
| QA-04 | Gecko content crash sets a message, but has no session recovery action. | BrowserController.kt onCrash | Agent 4 | P1 | Controlled crash test needed |
| QA-05 | Time Machine currently stores PDF pages rather than restorable interactive snapshots. | savePdfSnapshot()/TimeMachinePanel; README | Agent 7 | P1 | UI entry tested; PDF bytes/viewer not yet exercised |
| QA-06 | Tab thumbnails are captured as in-memory pixel bitmaps, but image freshness, 2-column readability and process death are not verified on devices. | WebSurface()/TabPanel() | Agent 3 | P0 | Screenshots captured by acceptance suite; human review outstanding |
| QA-07 | No verified explicit opt-in AI Universal Search or user-scoped Site Studio feature present. | AGENTS.md backlog and source audit | Agents 8/9 | P1 | Blocked until implementations |

| QA-08 | **Confirmed on real API 26 emulator:** rotation/activity recreation can crash browser when BrowserController constructs another GeckoRuntime in the same process. | Run 39357874291, API 26 crash artifact; `IllegalStateException: Only one GeckoRuntime instance is allowed` in BrowserController.kt:35 → MainActivity.onCreate:70 | Agent 4 (shared lifecycle with Agent 3) | **P0** | Emulator reproduced; fix and retest required |

QA-01–07 are source findings requiring fresh runtime validation on the integrated
APK. QA-08 has an emulator reproduction and was escalated to PR #427.

## Verified emulator run (2026-10-20; baseline branch)

Workflow run [39357874291](https://github.com/Tomex777/Build/actions/runs/39357874291)
built and installed on both API 26 and API 36. The initial harness exercised
Home, Time Machine entry, and ten tab creations successfully, **but did not pass
its smoke gate**. Navigation failed because the QA harness assumed the omnibar
placeholder remained visible instead of detecting an already-filled
`about:blank` input. The harness has since been corrected. API 36 confirmed
tab restore failure (10 tabs before force-stop, 1 afterward), and API 26
revealed the GeckoRuntime crash. Both reported private mode as absent on the
*baseline*, before Agent 3's branch is integrated.

Screenshot review of the baseline showed four square **letter-only** quick-site
tiles and a two-column tabs screen with mostly blank `about:blank` thumbnails.
This is a real capture, but **not proof that populated website thumbnails
render correctly**. A user-facing tab-heavy real-site test is still required.
The API 26 artifact additionally showed two **system-process** fatal log lines;
the harness now only attributes exceptions to the NIGHT Browser process.

## Next acceptance matrix (not executed; do not report as passing)

- Small Android 8/API 26 emulator; Android 16/API 36 emulator; landscape;
  and at least one actual ARM64 handset.
- Verify 10+ tabs: thumbnails must reflect real rendered content, selecting,
  closing, reopening and app restarts must maintain expected session state.
- HTTPS with redirects, form submissions, valid test-account login, history
  back/forward branching, long-press Peek and eventual promotion to a new tab.
- Download and open PDF/images; accept upload from SAF picker; record MIME,
  bytes, progress and failure recovery; deny and allow camera/location/audio.
- Check incognito cookies/local-storage and history separation from ordinary
  tabs through two controlled sessions; private screenshots must not leak.
- Offline/slow networks, process kill/OOM, rotations and status/navigation bars.
- Site Studio injection must be origin-scoped and reversible; AI requests must
  require consent and must not silently upload history/text/credentials.
- Measure memory/battery/jank over prolonged browsing, inspect traces and
  actual screenshots, fix defects then rerun on combined integration branch.

## Reporting convention

Keep separate labels **implemented**, **compiled**, **installed**,
**interaction-tested**, **physical-device-tested** and **integrated**.
Attach CI run URLs and artifact names when they exist, and record blockers with
reproduction steps, expected/actual results, APK commit and Android version.
Agent 1 owns merging and the shared docs/agent-backlog.md ledger.
