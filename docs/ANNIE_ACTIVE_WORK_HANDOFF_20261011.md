# Annie active development continuation — October 11–12, 2026

Main remains unchanged. This is a development checkpoint, not a claim of final release readiness.

## Authoritative source

Branch: `annie-rc-file-types-20261011`, PR https://github.com/Tomex777/Build/pull/133.
Latest application/test commit: `c0a931185dc33cc750e223571428786707b85ba8`.
Tree: `a31285684e1ec42bbb1e3055204fc00b777e0247`.
The latest source includes the complete preceding stacked release candidate, browser restoration, recorder pause/resume, persistent downloads and session-security changes. Do not resume from main.

## Implemented in this Work session

- Editor selection/clipboard regression now waits for Sora's pending document layout after replacing text. The confirmed exception was `BlockIntList.removeRange` through `LineBreakLayout.afterDelete` and `CodeEditor.cutText`, near ScriptChatFlowTest line 668. Deletion/replacement/clipboard/multiline assertions remain intact and passed on both APIs.
- Android 8 Canvas teardown deadlock diagnosed with thread dumps: ActivityScenario.close waited for UI idle while animated WebView rendered continuously. Tests detach/pause the native view and finish the API26 host, waiting for destruction. Newer APIs retain framework-owned finishing to avoid a reproduced RESTARTED lifecycle race.
- Screenshot preservation on Android 8 explicitly invokes a shell script and checks completion. Compound shell operators were not interpreted by UiAutomation's direct command endpoint.
- Canvas now has strict rendered-pixel checks and native fullscreen swipe verification, alongside same-WebView/state preservation and return-to-chat assertions. Earlier green state-only tests captured blank fullscreen frames; waiting for actual presentation fixed the evidence. Reviewed the newer Android 8 screenshot: game fills available width/height beneath safe title bar, with native fullscreen interaction verified.
- Browser restores every normal tab instead of truncating at twelve. Thirteen-tab recreation verifies selected tab, desktop mode and exclusion of private tabs.
- Browser address loads mark loading immediately and use Gecko Loader originalInput. Native page interaction is included in back/forward/download verification. Real downloaded response bytes and offline opening remain asserted.
- Browser URL Go dismisses the keyboard, names navigation controls for accessibility, and respects system bars. A full Android 8 run exposed intermittent address refocus despite isolated passes. Latest source transfers focus to the page after the native IME frame, closes open panels and tests both IME Go and the explicit button from Downloads.
- Voice ON_STOP cancels unlocked drafts without sending; actual locked recordings pause until explicit resume. Pause failure cancels with feedback.
- Voice hold uses actual Android ACTION_DOWN/MOVE/UP/CANCEL: ACTION_CANCEL discards unlocked drafts and ordinary release sends exactly once. Native tests cover cancel, release, slide-left cancel and slide-up lock through microphone-button removal followed by UI Pause/Resume/Send. Real AAC and waveform files are verified. Microphone and dictation now have distinct icons.
- Image fullscreen pan bounds use decoded image dimensions and ContentScale.Fit geometry, keeping letterboxed axes centered. Four JVM cases cover landscape, portrait, square zoom and unavailable dimensions.
- Script Studio New File and Assist creation offer all supported text types, infer the current document's type and preserve typed extensions. Blank names are rejected. Four JVM cases and native HTML/CSS creation cover the workflow.
- Focused report verification rejects missing, failed, errored or skipped required methods.

## Development PRs

| PR | Branch | Work |
| --- | --- | --- |
| 127 | annie-rc-editor-canvas-repair-20261011 | Editor regression and Canvas diagnostic infrastructure |
| 128 | annie-rc-lifecycle-tabs-20261011 | Tab restoration and microphone lifecycle |
| 129 | annie-rc-image-pan-20261011 | Fullscreen fitted-image pan bounds |
| 130 | annie-rc-voice-gestures-20261011 | Native hold cancellation regressions and icons |
| 131 | annie-rc-acceptance-repair-20261011 | Fixed native cancellation and Android 8 teardown/screenshot preservation |
| 132 | annie-rc-browser-ux-20261011 | Actual browser UI and locked voice gestures |
| 133 | annie-rc-file-types-20261011 | Complete current stack, file types, visible fullscreen Canvas and browser focus repair |

Intermediate PR heads contain superseded failures; the latest PR133 branch is authoritative. Consolidated RC PR121 remains at its prior source pending latest integrated acceptance. No main merge or public release occurred.

## Completed CI evidence

| Run | Source | Result |
| --- | --- | --- |
| 38164013376 | 10920bb | Diagnostic rule exposed internal type; compilation failed, subsequently corrected |
| 38164360176 | 155391d | API36 editor+three Canvas PASS; API26 editor PASS, Canvas bounded timeout yielded teardown diagnosis |
| 38164362290 / 38164362341 | 982b06c | Earlier full/focused runs exposed browser history/Canvas acceptance issues; superseded |
| 38164520864 | daef955 | App/instrumentation compile and all 182 JVM cases PASS |
| 38165110333 | be82e65 | Normal voice release PASS both; native cancellation sent audio incorrectly both, fixed subsequently |
| 38166056708 | 8f981cb | Native microphone cancellation/release PASS both APIs |
| 38166056675 | 8f981cb | Editor+all three Canvas methods PASS both APIs (before stronger pixel/fullscreen-touch checks) |
| 38166056691 | 8f981cb | Full jobs and owner-signed installation/reinstallation PASS both; validated production gate PASS |
| 38166056705 | 8f981cb | Focused API26 PASS; API36 Canvas teardown RESTARTED race, fixed subsequently |
| 38166516994 | 53a9604 | Browser test action-chain compilation error; fixed in 3682c3a |
| 38166802536 / 38166984449 / 38166984471 / 38166984478 / 38166984484 / 38166984496 | Superseded heads | Cancelled by newer source pushes; no complete acceptance claimed |
| 38167096516 | 401a41e | Editor+all Canvas methods including visible pixels/native fullscreen interaction PASS both |
| 38167096539 | 401a41e | Browser UI+all four voice gestures PASS both |
| 38167096602 | 401a41e | File type UI/JVM compilation PASS both |
| 38167096506 | 401a41e | All focused features PASS both |
| 38167096571 | 401a41e | Full API36 PASS; API26 URL-field focus assertion failed. Owner-signed installs/reinstalls PASS both; final validated-release gate correctly skipped |

Android 8 full reports include one capability assumption, `PlayerBrowserCookieIsolationTest.playerReadsOnlyItsOwnProfileCookies`, because its WebView lacks MULTI_PROFILE. Actual unavailable/forged/cross-host sources remain blocked; do not describe that assumption as exercised cookie-sharing coverage. Earlier full API26 artifact had 153 unique cases and process-death seed/restore checks each returned OK (1 test).

Useful artifacts: 11712412068 (API26 initial teardown repair); 11712648388 (API26 visible fullscreen Canvas); 11711994072 (same API36); 11712813557 (401a41e full API26 failure); 11712993734 (401a41e API36 full report); 11712714780 (401a41e owner-signed outputs); 11713965130 and 11713380468 (production installs API26/API36).

## Latest repair and repository correction

`f8b37593ba54686f85f01d3b3e4a8e395e21171f` introduced the browser focus handoff but accidentally used an incomplete tree after transient orchestration state was lost. It contained only two files, so no workflows started. Commit `c0a931185dc33cc750e223571428786707b85ba8` restores the entire 401a41e tree plus those two intended changes. Verified diff from 401a41e: exactly AnnieGeckoBrowser.kt and AnnieGeckoBrowserUiTest.kt, 34 insertions/1 deletion. No force push or history loss occurred. Do not check out f8b3759 as a standalone source tree.

## Release signing and environment

Owner identity unchanged: `4e7ab744bdbbcf6ddb577186e8163be4b0f4127d2123ed21108ca6e7dce70288`.
Application ID `com.tomex777.annie`, minSdk26, targetSdk36, versionCode1/versionName1.0.0.
Production signing script pins this certificate, signs ARM64/universal APKs and AAB, and records checksums. Production installation gate uses the exact owner-signed universal APK for fresh install and reinstall on both supported APIs. Latest source c0a9311 has NOT yet passed release acceptance; do not deliver its predecessor as though it includes the newest focus repair.

Android SDK/Gradle unavailable locally; all device builds run via GitHub Actions. Local repair-contract fixtures and whitespace checks passed. Local TypeScript check could not run because tsc is absent; CI has the tooling. The merge-repair script generates six source adjustments before CI builds; local generated edits were restored to committed originals after inspection.

## Next concrete task

Inspect the five c0a9311 push-triggered acceptance runs. Read every failure and artifact. Specifically verify browser IME and explicit Go from Downloads on API26, full API26/36 suite, actual fullscreen screenshots, all focused required methods, owner-signing identity, and production install/reinstall. Fix remaining failures without weakening assertions. Once the exact integrated source passes, fast-forward only the development consolidated RC branch, update PR121, inspect/download the permanent-signed ARM64 APK/checksums and deliver it. Keep main unchanged.

Remaining unverified product work includes broad manual-device UX, image sharing/advanced cropping and long-session browser/media/lifecycle behavior beyond existing automated coverage. Do not call the entire application feature-complete solely because an acceptance run passes.

No background work is promised after a session ends.
