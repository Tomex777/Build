# NIGHT Browser — Wave 2 CONFLICT-SAFE INTEGRATION / RESUME

**User intent, October 20 2026:** previous coding agent chats may have stopped. "Continue" must resume actual existing Wave 2 implementation and land a **single coherent GeckoView Android APK**, not restart a new wave. Project `night-browser/`, repository `Tomex777/Build`; integration branch `feature/night-browser-multi-agent-20261020`; communication hub https://github.com/Tomex777/Build/issues/460. Android identity **`com.night.browser`** and minSdk26 remain unchanged pending user decision.

## Starting snapshot (must recheck live GitHub before action)

Prior coordination and Home PRs #425/#430/#437 MERGED; project instructions #462 MERGED. **Agent 1 draft unified browser #468 remains OPEN**, head `4691050764b4` as inspected October 20 ~08:38 Africa/Lagos; it claims a wired app assembled from Home/Gecko/tabs/Journeys/Library/Time Machine/AI/Peek/Studio/QA modules but has **not established successful combined APK compilation, installation or end-to-end privacy QA**. Earlier partial #439 is a separate open draft, likely overlapping and may be superseded by #468. Additional specialist PRs include #427–434 (original), #441 Home QA and wave-2 #472 Gecko, #474 Home, #475 Time Machine, #476 Site Studio, #478 QA. Status/heads will change: fetch before decisions. An open draft is **not** a release or merge.

## AGENT 1 FIRST: resolve conflicts and make an actually working APK

1. **Audit and ledger:** fetch current branch HEAD, latest Issue #460 messages, all open Browser PRs including #468 and #439, source changed-file sets, new vs base commits, unit/Android CI and build logs; record `PR | headSHA | files | conflicts | compiled? | installed? | privacy tested? | merge decision`. Do not mistake GitHub's `mergeable=true` for software correctness.
2. **Use current candidate.** Work on the existing `agent/browser-v2-01-coordinator` branch/PR #468 (unless merged or superseded). Rebase/merge canonical integration into it carefully; if necessary create small *fix-only* PRs that eventually land into canonical, rather than generating another all-in-one draft. Preserve intentional UI, all original specialists' new files, and existing working Gecko/Peek/Journeys instead of silently deleting a subsystem to satisfy compiler.
3. **Collision contracts:**
   - `MainActivity.kt` route/composable calls from A2 Home, A3 tabs, A6 Library, A7 Time Machine, A8 AI, A9 Peek/Site Studio — one owner-controlled wiring window; ensure buttons navigate to real features.
   - `BrowserController.kt` Gecko sessions/new tab/private partition A3/A4, download callback A6, links Peek A9, visit hooks A5; preserve all required lifecycle/permission delegates.
   - `JourneyStore.kt` parent cursor and privacy isolation A5; no fake chronological tree.
   - `AndroidManifest.xml`, Gradle, ContentProvider/SAF authorities and Gecko APIs must compile and respect `com.night.browser`, minSdk26. Divergent UI PR #422 changes package and must **not** be blindly merged.
   - Resolve test-only localhost cleartext restriction with a **debug-only** exception or HTTPS fixture. **Never weaken production network security to make emulator tests pass**.
4. **Compile ACTUAL combined source first.** Check GitHub Actions latest-head logs or run `gradle -p night-browser :app:testDebugUnitTest :app:assembleDebug`; fix Kotlin/Gecko compile errors, manifest config, dependencies, initialization and crash on startup. Do not declare done from isolated A2/A4 CI. Publish artifact/run link only when it exists and install test reports.
5. **Installation+real use next:** on emulator API26 and 36 (and physical phone if accessible), launch APK, open multiple real HTTPS sites, navigate forms/logins, check 20 tabs preview+restore, private cookie isolation, file upload and authenticated/private downloads, Journey A→B→C/back A→D graph, Library, offline PDF Time Machine, AI consent/no key, Peek disposal and Site Studio cross-origin separation. Record screenshots/logcat/memory and fix every P0 before merge/release. Agent 10's report-only CI can be green while actual acceptance FAILS.
6. **Incremental merge:** land verified independent modules/fixes into canonical integration in small PRs after checks and review; stage shared MainActivity/BrowserController merges with a deterministic conflict-resolution checklist. Keep specialist branches intact until absorbed; close superseded #439 only after proving #468 contains its necessary code. Do not merge browser to main or production-sign without owner's approval.
7. **Keep advancing:** while a CI run is queued, inspect conflicting PRs or implement independent integration tests; don't idle or declare "we're waiting." At session end record exact SHA, compile/error logs, merged components, screenshot evidence and next command in Issue #460 so another "Continue" resumes.

## Agents 2–9: no new standalone feature wave

On "Continue", find **your existing role PR** and test against coordinator's combined candidate #468 plus current integration. Repair merge conflicts, requested contracts and compile/site/security bugs using a narrow branch if needed; submit actual code+tests, not new unconnected UIs. A2 Home/keyboard, A3 session and true private store, A4 Gecko delegates, A5 correct graph, A6 real bytes/download history, A7 authentic PDF archive, A8 consent-based provider and safe no-key behavior, A9 origin-safe CSS/Peek. Use issue #460 for shared API negotiation and explicit ownership; do not stomp changes inside another agent's controller/UI.

## Agent 10: independent release judge

Resume PR #478, make Android API26/API36 harness runnable, install **combined** APK at exact integrated SHA, record PASS/FAIL/BLOCKED/NOT_RUN across 20+ real-site scenarios. Check local HTTP test fixture only via debug-only network configuration or HTTPS fixture. Require a positive regular-profile cookie followed by negative private observation, test sensitive private download history and cross-site Site Studio isolation. Share defects with owning agents and retest merged fixes. **Report-only workflow passing is not product acceptance**.

## Message contract

```text
[BROWSER A#][CLAIM|DESIGN|BLOCKED|READY|INTEGRATED|VERIFIED|HANDOFF]
Canonical integrated SHA:
Existing PR/branch and head SHA:
Shared file owner + conflicts resolved:
Actual Gradle/APK install/device/API/site scenario + evidence:
Bugs remaining and requested reviewer:
Next exact command/action:
```

The owner wants a single **genuinely polished, fully functional browser**. CI green, authored modules or a draft PR is not completion. These GitHub docs are a handoff system; they cannot run paused ChatGPT chats autonomously.
