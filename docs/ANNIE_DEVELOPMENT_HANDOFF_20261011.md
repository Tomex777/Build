# Annie development continuation — 2026-10-11

Development stopped when the workspace execution service disconnected and then returned HTTP409 environment_offline: "Environment is not connected." GitHub remained available, so all prepared source changes were preserved remotely and CI was triggered. No fully validated new APK is claimed.

## Authoritative branches and PRs

- Consolidated RC: annie-release-candidate-integration-20261011, unchanged at4222a5d5649b374c8cd98d4b554d1f2e65c6439b; draftPR121.
- Current development branch: annie-rc-download-history-20261011.
- Latest application commit: a3d4dca0573f3ad471b9458338b6e5282f709bb6.
- PR stack: PR122 browser restoration -> RC; PR123 voice recording -> browser-restoration branch; PR124 download/image/acceptance fixes -> voice-recording branch.
- Main was not merged or modified. No app publication, credential change, or signing-configuration change occurred.

## Implemented and pushed

1. 1fcc4a493a7d89dd95122097930c3f93a341c358: persist selected normal tab and desktop mode, migrate old URL-only tab snapshots, restore desktop settings before navigation, exclude private URLs and selection, avoid startup persistence overwrites.
2. d666e0876dbfc41be88b39acd827306a51a04944: real MediaRecorder pause/resume for locked voice recording; monotonic duration excludes paused time; paused waveform capture stops; user controls and microphone/AAC device acceptance.
3. 4cd355653e0dd2137322c111cf3317b850fb685b and follow-ups: completed Gecko download history persists only local-file metadata; offline reopening; metadata removal preserves bytes; live Gecko HTTP navigation/back/forward/download acceptance checks original response bytes.
4. 74507b61945900b670a9d8a4c6a357c37f67a06a and414c4e740315c3ff2327a26326bbf3ea5c6a9163: full instrumentation timeout/checkpoints and focused per-class runner. The previous comma-separated class selection executed only its first class; corrected runner verifies every requested method in XML and rejects missing, failed, errored or skipped cases.
5. d5f75d6d412680ae2bf8925bf7a8b80c8444a81e: native Snake input waits for a focused and presented WebView; input remains real Android touchscreen events, never JS steering.
6. 6862fce550cb1ee7c08145bfd627d014b4c0bd1a: inline PNG/JPEG/etc. data decodes to Coil byte models in chat, viewer and editor. Fullscreen image loading/error/retry is visible. Viewer acceptance requires decoded-image readiness and a presented frame. Replaced old PNG fixture with invalid IDAT checksum. Added three parser JVM cases.
7. a3d4dca0573f3ad471b9458338b6e5282f709bb6: Android8 screenshot capture bypasses the accessibility recovery intended for Android16; diagnostic checkpoints retain native pixel capture. Correct corrupted MIME regex and malformed-type regressions. Decode editor images at4096px bound before making editable bitmap; added wide inline PNG device regression. Live Gecko acceptance uses a fresh controlled session, restores browser preferences and reports navigation state/requests on timeout. Canvas runs even after unrelated focused failures.

The evidence-inspection workflow was added in174c364a6b984089dd076c27f4c06365618b63d1 and refined in77576250fca3fa4e143c5097b4467ad43fe4c2aa. Runs38156133646/38156241410 read actual artifacts through CI after the workspace disconnected.

## Verified evidence

- Original full run38150304637: build/API36 passed144 full instrumentation methods, plus process-death seed/restore and release smoke. Owner-signed installation/launch passedAPI26 andAPI36.
- Original API26 job114502195305 did not complete: tests started15:20:38UTC; operation cancelled16:32:26UTC at its80-minute job limit. Final validated-production-release was skipped.
- Artifact11707519757 retains API26 logs. Built-in Snake reached "gameplay verified" at15:21:19.289 after real pointer/touch input, then never finished screenshot capture. Counter fullscreen and real-chat Snake methods had finished. This narrows the stall to the screenshot helper, not failed Snake gameplay. The helper called Android16-specific UiDevice accessibility recovery onAPI26.
- Run38152685202 at1fcc4a4: API36 ran145 methods, failing only the built-in Snake native input check; tab restoration passed. No application gesture assertion was removed.
- Run38152943085: actual microphone/AAC test and175 JVM cases passed onAPI26/API36. Its class-list invocation omitted playback/tab tests, so that run alone does not prove those features.
- Run38154489645 ata80c28e: eight required focused methods passed on BOTH APIs. Live Gecko second-page navigation timed out onAPI26; API36 was superseded before that method finished.
- Run38155131253 at6862fce: all13 non-Canvas focused methods passed on BOTH APIs, including live Gecko response bytes and decoded-image viewer. All3 Canvas methods passed onAPI36. API26 Canvas hit its five-minute timeout, with missing XML rejected rather than accepted.
- Artifact11708233336 and evidence-inspection run38156241410 confirm194 unique API36 cases:178 JVM plus16 focused device cases, zero failures/errors/skips. API26 report artifact11708089378 includes its13 completed non-Canvas cases and incomplete Canvas evidence.
- Local repair-contract fixtures, shell syntax and git diff whitespace checks passed before disconnection. No Android SDK or Gradle installation existed locally; Android compilation/tests ran in GitHub Actions.

## Current CI — inspect next

Application commit a3d4dca:
- Full Android acceptance: https://github.com/Tomex777/Build/actions/runs/38156374270
  - API36/build job114520500108.
  - API26 full job114520500284.
- Focused API26/API36 acceptance: https://github.com/Tomex777/Build/actions/runs/38156374301
  - API26 job114520093190.
  - API36 job114520093398.
At last inspection, API36 focused Kotlin/JVM/device-test compilation had passed and emulator installation was starting; API26 compilation and full builds were still running. Latest batch's new behaviors are NOT yet validated.

Inspect complete logs and XML, especially all3 Canvas methods onAPI26 and inlineImageDecodesIntoBoundedEditableBitmap on both APIs. Focused runner now requires20 device methods: previous16 plus3 file-picker cases and1 editor-decode case. Full expected suite is149 methods excluding the separate process-death package, followed by process-death and release-install checks. Confirm actual counts; do not assume coverage from job color.

## Signing and remaining limits

Permanent owner signing is configured. Original certificate SHA256:
4e7ab744bdbbcf6ddb577186e8163be4b0f4127d2123ed21108ca6e7dce70288
Original owner-signed artifact11706906228 contains production APKs/AAB/status/certificate; separate CI/debug-signed artifact11707240505 is a development APK. Neither is a newly fully validated release from this session. Do not publish or merge main without owner authorization.

Stock WebViews lacking MULTI_PROFILE fail closed for scoped browser authentication. Passing their security assertions does not prove authenticated scoped-WebView browsing works on unsupported engines. Preserve this isolation; Gecko standalone browsing is separate.

Real physical-device gesture/audio quality, network interruption recovery, broad offline media behavior, fullscreen image save UI and comprehensive feature polish remain unverified. Browser allows more user-created tabs than the12 retained in its snapshot; review that mismatch after immediate release blockers.

## Next engineering action

1. Freshly fetch the current development branch into a working Android-capable workspace. Remote GitHub is authoritative. Old scratch checkout remained at6862fce with prepared uncommitted edits because the executor went offline; all intended changes were reconstructed and pushed in a3d4dca. Preserve/compare any old local diff before resetting; a fresh clone is safest.
2. Inspect the two new runs and artifacts. If API26 still stalls, read continuous instrumentation-checkpoints.txt and screenshot capture requested/recovery complete/pixels captured stages; locate exact blocking call. Do not suppress missing-method checks or gameplay assertions.
3. Fix actual failures, rerun acceptance on exact commits, then inspect APK signatures, install/launch onAPI26/API36, and review image/Canvas screenshots.
4. After complete validation, consolidate the verified stack into the RC integration branch without touching main, updatePR121, and supply the exact APK artifact, commit, signing identity, installation instructions and remaining capability limitations.
