# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Handoff baseline: `c296e0fd9cfbffd2d70a2d94880e5237cdfe0d6a`
- Current source head: `504de94cf460c84030473f74bb36b4c7374975c0`
- Latest validation:
  - `57d3cef`: Night ARM64 APK and Groq Key Pool passed.
  - `57d3cef`: 31 provider tests ran; only AnimePahe service discovery failed. Android could see the installed service, but Night received a descriptor with zero tools and zero message types. All 13 UI suites passed, including Extensions, Integrations, Image Editor, and PDF Editor.
  - `57d3cef`: Integrated video job stopped during Android Emulator package download (`unknown archive`), before playback ran.
  - `539a31d`: ARM64 passed. Groq and Integrated Regression initially hit Java heap exhaustion during APK packaging; both workflows now use a 4 GB heap and one worker (`4051e61`, `504de94`).
  - `504de94`, Integrated Regression #416: APK packaging succeeded and artifacts were produced for both Night and AnimePahe. All 13 Android 16 UI suites passed. The separate provider job failed while compiling `NightExtensionToolIntegrationInstrumentedTest.kt` because its Messenger callback referenced `connection` before Kotlin could resolve it; this pass fixes that test harness issue, so AnimePahe's live descriptor still has no valid result.
  - `504de94`, video job: Android 36 emulator again showed a black video viewport after VLC attached its texture surface and started playback. The saved screenshot confirms the black frame; logs contain no decoder/Vout events.
  - `504de94`, image editor suite: original 4121×4116 image was preserved byte-for-byte; edited copy exported at 4089×4087. The in-app editor screenshot did not reproduce a tiny preview or export-quality loss.

## Current device issues

- AnimePahe service registration vs. prompt inventory: `504de94` built the matching APKs, but its focused device regression did not compile due to a test-harness scoping error. That error is fixed in the current working tree; rerun is pending.
- Repeated identical options cards: provider regression asserted one persisted card after two identical `create_options` calls; passed among the 30 successful tests at `57d3cef`.
- Black video viewport: reproduced on Android 16 emulator on #416; VLC surface attached and playback started, but the frame assertion saw an all-black viewport. Current logcat lacks VLC decoder/Vout events, so add targeted diagnostics before trying another rendering change.
- Image editor preview/quality: editor interaction suite passes on #416; its fixture preserves original dimensions/bytes and exports a full-resolution edited copy. The reported tiny preview may depend on a different entry point or user image.

## Completed Night platform phases

- Message types + rich-message ecosystem
- Extension Configuration
- Browser message + verification flow
- Full Night browser
- Memory & Summary
- Library + Tools integration
- Provider UX/Admin
- Real provider SSE streaming
- Groq flat key-pool rotation
- MCP runtime
- Extensions/MCP unified Integrations shell
- Open-ended extension capabilities + preferred provider/fallback routing
- Media Watch/Read Library + playlist action contract
- Media/image/video/PDF viewers/editors
- Mihon reader integration
- Live Voice
- App-wide system-inset audit
- Four-tab shell: Chat / Library / Scripts & Projects / U
- Theme/font/appearance controls + AI appearance tool
- Fluent Emoji renderer + latest 30 used emoji recents
- WhatsApp-style inline timestamp/read metadata

## Frozen extension-platform contract (v1)

Night Extension API v1 is now treated as a compatibility contract while real extensions are built.

Stable runtime boundary:
- Independently installed extension APK
- Exported bound service action:
  `com.example.whatsapp.action.NIGHT_EXTENSION_SERVICE`
- Messenger IPC with JSON-only payloads
- Schema version: `1`
- Requests:
  - DESCRIBE = 1
  - EXECUTE_TOOL = 2
  - EXECUTE_ACTION = 3
- Reply:
  - RESULT = 100
- Night never sends provider secrets, complete chat history, or another extension's state to an extension.

Stable descriptor concepts:
- `extensionId`
- `name`
- `type = extension`
- open-ended `capabilities` / `tags`
- `tools`
- namespaced `messageTypes`

Stable model-facing behavior:
- Extension tools are namespaced.
- Equivalent tools may be routed by shared open-ended capabilities.
- Preferred provider is exposed to the model; equivalent enabled providers remain available internally for deterministic fallback.
- Extension tool results may emit `night_message` or `night_messages`.
- Emitted message types must be declared and namespaced by the extension.

Stable configuration behavior:
- Configuration is a generic schema, never provider/anime/music-specific Night code.
- Supported field families:
  - toggle
  - single choice
  - multi choice
  - number
  - range
  - text
  - multiline text
  - secret
  - action
- Sections/groups, descriptions, suffixes, required fields, Advanced and task overrides are generic.
- Example: `parallel_downloads` is only a generic number field declared by an extension.

Stable standard actions:
- Add/Remove from media library
- Add/Remove from playlist
- Browser verification/session actions remain extension-specific through the action channel.

## Product rules now locked

- Latest required bottom navigation: Chat / Library / Scripts & Projects / U. Move the existing Scripts/Projects workspace into its own tab; consolidate configuration into U.
- Integrations belongs in Settings/You, never as a fourth tab.
- Mihon Reader defaults to true immersive fullscreen and hides system bars.
- Normal Night screens respect status/navigation safe insets.
- Full Night Browser is separate from mini browser verification cards.
- Groq keys are one rotating pool, never Primary/Backup/Burst roles.
- Default app accent is Night wine/pink, not hard-coded WhatsApp green.
- Recent emoji means the latest 30 USED emoji.
- Fluent Emoji is the app-wide emoji renderer.
- Text/caption message timestamps use WhatsApp-style trailing inline metadata.

## Current phase: real-device cleanup

Work the user's latest real-device priorities in order: persistent chat history, provider prompt/context correctness, chat/tool/browser ordering and scrolling, voice transcription diagnostics, then main navigation and appearance/provider/filter cleanup. Keep model-context bounds separate from the unbounded visible/persisted message history.

The confirmed baseline has no visible message-flow cap: Room observes the complete chat, and the `takeLast(60)` bound exists only while assembling model context. Earlier fixes already addressed user-message persistence (`a0e2a5a2`) and restoring the current-chat summary into prompts (`fc357f36`). No prior root-cause fix for visible history disappearance was found in the conversation record.

The visible-history regression was traced to Room replacement semantics: appending or finishing a message called `upsertChat()` on an existing parent row. That DAO uses `@Insert(REPLACE)`, which deletes and reinserts the chat and cascades deletion to its messages and summary checkpoints. The append and finish transactions now update the existing chat row with `@Update`. An Android instrumentation regression crosses 128 messages with timestamp ties and verifies that all visible messages and the summary checkpoint remain present.

Provider request assembly now carries the restored chat summary, bounded recent context, exact older-message recall, reply references, and relevant rich-message/extension context into the request. The MockWebServer regression inspects the actual provider payload after 65 intervening messages and verifies that a reply can include its older extension result without exposing provider secrets. Streaming completion preserves chronological tool-result/assistant ordering. Voice STT now quotes the WAV codec parameter correctly; a service regression checks multipart upload and transcript parsing. STT failure keeps the recorded audio message available for playback.

This cleanup pass moves the existing Scripts/Projects workspace to the third main tab, removes its Library shortcut and the duplicate Settings entry from the chat-list menu, keeps U as the configuration home, persists Library filter state, and propagates the saved accent through major app surfaces. Provider add/edit/key/model forms use bottom sheets with improved fields. Appearance accepts validated custom accents and registered/imported TTF/OTF fonts; appearance tool calls use validated setting/value actions.

GitHub CI is the Android build and device-test runner for this pass. Commit `0c0aced` passed the Gradle build, Android 16 provider instrumentation (history, request context, and STT), and the main-tab, Browser, Memory, and supporting UI checks; the provider editing UI exposed a nested-scroll crash. Commit `ab3c4be` removes the nested scroll containers. Its Night Integrated Regression and Groq Key Pool workflows both passed. Android 16 provider instrumentation, provider-sheet interaction, Browser, main tabs, Extensions, Video, Mihon Reader, and every other UI suite passed on that source head.

## Extension rollout checkpoint

The shared framework is frozen unless a real extension exposes a concrete missing capability.

Reference extension order:
1. AnimePahe extension v0.2.0
   - search
   - anime/media card output
   - details / episode discovery
   - extension-owned configuration
   - Night browser verification/session handoff
   - playback/download handoff
   - Add to Library / Playlist
2. Music extension(s)
   - search/results
   - music card
   - extension-owned playback/download configuration
   - Lyrics
   - Add to Library / Playlist
3. Review Extension API v1 only after at least two materially different real extensions are working.

## Calling rule

Night calling remains user ↔ AI Live Voice only.
Do not add human-to-human WebRTC/FCM calling or a Calls bottom tab to Night.

## 2026-09-23 device follow-up

Continue on `night-groq-key-pool-ci` at `fada822cd62947d0b6e5f69a667064a2a0208b57`; do not use the older `04c78f…` handoff SHA. Use GitHub Actions for Android builds and emulator/device checks, install the matching Night and AnimePahe artifacts, and leave `main` untouched.

Integrated Regression #417 built both matching APKs and passed the Extensions and Integrations UI suites. The provider instrumentation installed and bound the AnimePahe service from the matching APK, but its raw descriptor IPC result was `{}`; the manager correctly marked the extension unusable at 0 tools / 0 message types. The source descriptor unit test expects 5 tools and 6 message types. On #418, the separate AnimePahe regression and integrated build both passed their descriptor unit tests, and their APK DEX files matched byte-for-byte, but the installed service still replied with `{}`. The instrumentation failure now reports the Messenger reply keys, result length, OK flag, and service error to narrow down the runtime mismatch.

The #417–#419 video regressions displayed a black preview. VLC parsed the fixture and started the H.264 decoder, but there was no Vout event. The atomic claim removed duplicate `attachViews()` calls for one player; #419 showed the two remaining attachments belong to different player/layout instances composed together. The active player still has no Vout using TextureView. A local SurfaceView rendering-path change is the next Actions test.

On #418, Memory and Image Editor UI suites passed; Image Editor exported a successful evidence artifact. The Providers UI shard hit a `Quickstep isn't responding` system dialog. Its artifact name overlapped the provider instrumentation artifact, so the UI artifact name is now separated. Duplicate option-card persistence remains covered by the provider emulator instrumentation, while visual repetition still needs screenshot verification. The observed Groq `429 TPM` remains a provider quota failure, independent of these UI/extension issues. Black video preview and tiny image-editor preview/quality concerns remain open until reproduced and fixed.

Integrated Regression #420 ran from `1889c56efd99557f81fe2b2432f7ab9825775b67`; APK packaging and most UI suites passed. The AnimePahe/provider instrumentation job stopped at Android-test Kotlin compilation: the empty-JSON check used `JSONObject.length` instead of `length()`, and its diagnostic called Kotlin `String.length()` instead of the `length` property. Both expressions are corrected for the next run; #420 did not reach extension IPC, so it provides no new runtime descriptor result. The Video shard again produced a black preview with VLC software H.264 decode, despite SurfaceView. It attached two full-size surfaces for adjacent pager pages within 2 ms, then started playback only for the active page. The next test mounts/attaches a SurfaceView only for the active page, since Android may composite offscreen pager surfaces outside Compose clipping. The Browser shard timed out before finding the expected inline browser accessibility node; it was a UI assertion timeout, not an ADB failure. Providers, Integrations, Extensions, Memory, Image Editor, PDF Editor, Mihon Reader, and the other UI suites passed. The APKs were installed in the matching GitHub Actions Android 16 emulator jobs; do not use a local emulator/build for this follow-up.

## 2026-09-23 device cleanup after Integrated Regression #421

The verified remote branch tip was `27c0bef6dc378e771de5ef9c05d5b0e55ab61bba`; the branch had not advanced when this follow-up began. Integrated Regression #421 confirms the remaining runtime issues:

- The Android 16 provider job installed and bound `com.night.extensions.animepahe/.AnimePaheNightExtensionService`, and received a successful Messenger reply with `resultJson`, `ok`, and `requestId`. The JSON was `{}` (2 characters), with no service error. The extension contract remains 5 tools and 6 message types. This is a runtime service-reply issue, not an Integrations enabled-state or unit-test-only issue.
- Night only registers tools and declared message types after it has parsed a non-empty descriptor. The integration UI's enabled toggle is therefore not sufficient evidence that the model prompt can see the extension.
- The duplicate `create_options` persistence test passed in the provider instrumentation suite. Visual repetition still needs screenshot/interaction verification. Groq 429 TPM output is a provider quota result.
- The active-only SurfaceView change removed simultaneous pager attachments, but #421 still had a fully black synthetic-MP4 viewport. VLC opened the file, added streams, and reported playback; there was no Vout event. The code had treated `attachViews()` returning as surface readiness without waiting for a valid native `SurfaceHolder`. The current follow-up waits for `surfaceCreated()` / `surface.isValid` before starting playback and records readiness separately.
- Image Editor passed its UI suite, but the screenshot artifact still needs a deliberate preview-size and export-quality review. Do not close that concern from a passing UI test alone.

The focused service change now calls AnimePahe's descriptor builder explicitly and logs runtime id/tool/message counts plus serialized length. Verify through the matching APK IPC test on Actions; do not infer success from the source descriptor unit test. The video attachment gate also needs confirmation on the synthetic fixture before calling playback fixed.

## 2026-09-23 validation follow-up: Integrated Regression #422

Patched source was built and tested from `0bffe7b0bd23a18c0e55bd06589863240e4612f4`.

- Integrated APK build passed; ARM64 APK #103 passed; AnimePahe extension regression #42 passed.
- Provider instrumentation still received a successful Messenger reply containing `resultJson={}` (2 characters), `ok=true`, and no service error. Calling `AnimePaheNightExtensionService.buildDescriptor()` explicitly did not change the runtime response. Source unit tests and extension packaging alone therefore do not explain the issue.
- Video instrumentation now proved the SurfaceView's `surfaceCreated` callback ran and its native surface was valid before VLC playback started. VLC opened the fixture and initialized its H.264 decoder at 640×368, but the screenshot remained black and no Vout event appeared. Surface creation timing was a real race but not the remaining rendering failure.
- The emulator Extensions UI shard failed during ADB/emulator setup before a useful UI assertion; Extensions UI had passed on #421. Other #422 UI shards, including Providers, Integrations, Browser, Memory, and Image Editor, passed.
- Repeated option-card instrumentation remains green. The visual card-repetition question and Image Editor preview/export-quality review remain open; the test screenshots have not been visually confirmed.

Next diagnostic pass: print extension-process descriptor logs in the provider job log so the runtime descriptor count/serialized length is visible without opening the ZIP artifact. Try TextureView only on virtual Android devices while physical devices keep SurfaceView, and log the actual native-view dimensions/visibility to determine why the valid-surface SurfaceView path produces no visible frame.


## 2026-09-23 validation follow-up: Integrated Regression #423

The branch started this pass at verified remote tip `e858de2ad9da893dc13ecb1de2c17376ac7eb64b`. Integrated Regression #423 built successfully; ARM64 APK #104 passed. Provider instrumentation ran 31 tests and again failed only the installed AnimePahe descriptor check: the matching APK service bound successfully, but Night received `resultJson={}` (2 characters), `ok=true`, and no service error. The extension-side descriptor log lines were not present in the provider job output or uploaded test report, so the runtime mismatch remains unresolved. The persisted failure message still includes the reply keys, result length, and service error.

The live runtime path refreshes installed APK services before taking the model prompt snapshot. It parses the descriptor, registers extension tools/message types only for a valid enabled descriptor, builds provider schemas from the registered integration registry, and adds live extension inventory plus tool/message summaries to the system prompt. The #423 Integrations UI screenshot is from `NightIntegrationsPreviewActivity`, which hard-codes an enabled AnimePahe sample at 4 tools / 2 message types; it does not represent the installed APK registry. Do not treat that preview or enabled label as evidence the model can see the extension.

For video, #423 used a TextureView on the Android 16 emulator. Logs show its Android view was available, attached, shown, and 709×1536 before playback; VLC opened the fixture and initialized software H.264 decode at 640×368, but no Vout event followed. The screenshot remains black, with a transient Quickstep system dialog over the player. The app previously gated start on Android surface availability alone. The current change waits for libVLC's own `IVLCVout.Callback.onSurfacesCreated` and logs its readiness state before playback. This must be verified with the synthetic MP4 on Actions; video remains open pending that run.

I visually inspected the #423 Image Editor screenshot: the preview spans the available 709-pixel screen width and is clear at emulator scale. The exported JPEG is 4,089×4,087 pixels from a 4,121×4,116 source, and the artifact reports the original image remained byte-for-byte unchanged. The UI test passed. Keep this screenshot-based check separate from the automated UI pass; a physical-device preview check remains outstanding.

The provider regression suite completed 31 tests; the only failure was AnimePahe descriptor discovery. The repeated `create_options` side-effect regression remains covered and passed, but #423 did not provide a screenshot/interaction artifact proving whether identical option cards repeat visually. Keep Groq 429 TPM output classified as provider quota failure.

On #423, Integrations, Extensions, Extension Config, Providers, Browser, Memory, Image Editor, PDF Editor, Mihon Reader, and the other completed UI suites passed; Main Tabs failed. Do not push another commit until the relevant Actions jobs for the current source SHA finish.


## 2026-09-23 validation follow-up: Integrated Regression #424

The branch was verified at `73c70b22ec5e18e6b8633425152af13cc3776b69` before this run. [Integrated Regression #424](https://github.com/Tomex777/Build/actions/runs/35918645955) built both APKs successfully. Night ARM64 Test APK #105 and Night Groq Key Pool #383 passed.

- Provider instrumentation ran 31 tests; only `installedAnimePaheIsRefreshedIntoTheEnabledModelInventory` failed. The bound component was the installed AnimePahe service. Its Messenger reply was `ok=true`, keys `[resultJson, ok, requestId]`, `resultJson={}` (length 2), and no service error. The persisted failure includes these fields.
- The #424 AnimePahe APK artifact contains `AnimePaheNightExtensionService`, its `buildDescriptor` implementation, and the Night SDK `NightExtensionService` Messenger serializer. Source inspection confirms the extension calls `buildDescriptor()`, the SDK serializes the returned JSONObject with `toString()`, and sends that string under `resultJson`. The captured provider failure log still has no extension-side descriptor log lines, so the point where the runtime descriptor becomes empty remains unresolved. Do not infer model visibility from the enabled UI state.
- All other provider tests passed, including the repeated `create_options` side-effect regression. Visual repeated-card behavior remains unverified; the #424 provider artifact is a Providers settings UI suite, not a chat-options interaction screenshot. Groq 429 TPM remains a separate provider quota failure.
- The #424 Android 16 video check again showed the TextureView attached and shown at 709×1536, Android surface ready, and libVLC `onSurfacesCreated` / `areViewsAttached()` readiness true before playback. VLC opened the synthetic MP4 and initialized software H.264 decode at 640×368, but emitted no Vout event and the screenshot still had 0 colored viewport pixels. Surface timing and view type alone do not resolve the black preview.
- I reviewed the #424 Image Editor screenshot directly: the preview occupies the available 709-pixel screen width and is clear at emulator scale. The exported JPEG is 4,089×4,087 pixels and the artifact reports the original was preserved byte-for-byte. This closes the emulator screenshot review only; physical-device comparison remains open.
- Main Tabs, Browser, Integrations, Live Voice, Mihon Reader, MCP Servers, Memory, Message Blocks, Providers, Extension Config, and Image Editor UI suites passed. PDF Editor timed out waiting for its composer content description. Extensions UI failed during SDK/emulator setup with an unknown-archive error before exercising the app.

No further commit was pushed while a #424 job was running. The video, runtime descriptor, and visual repeated-card questions remain open for focused follow-up.


## 2026-09-23 validation follow-up: Integrated Regression #425

[Integrated Regression #425](https://github.com/Tomex777/Build/actions/runs/35921471722) was built from `b62d3b1faa6ab8a4868a4f5cb1110771de2399a9`.

- The provider job again ran 31 tests with one failure: `installedAnimePaheIsRefreshedIntoTheEnabledModelInventory`. The matching extension APK bound and replied with `ok=true`, `resultJson={}` (2 characters), and no service error.
- The new device-side IPC log showed `Received DESCRIBE ... what=1`, followed by `Sent empty-looking descriptor reply ... ok=true, chars=2`. Neither the SDK's descriptor-built/serialized logs nor AnimePahe's descriptor log appeared. Source tracing found that `NightExtensionService.handle(Message)` queued a lambda which later reread `message.what`; Android recycles a Message after its Handler callback returns, so the worker could fall through to the default `JSONObject()` reply branch. The focused fix snapshots the request code and copies the Bundle before executor dispatch. Verify that fix on the next Actions run; do not count source tests alone as runtime proof.
- The repeated `create_options` side-effect test passed among the 30 successful provider tests. No chat-options visual interaction evidence was captured, so identical visible option-card repetition remains open. Groq 429 TPM remains a provider quota result.
- Video evidence again showed a black synthetic-MP4 viewport despite an attached TextureView, valid native surface, and libVLC `onSurfacesCreated` callback. VLC opened the file, selected software H.264 decoding, and reported `playing`; no Vout event followed. This remains unresolved.
- ARM64 APK #106, Groq Key Pool #384, and the AnimePahe extension regression passed. All 13 Android 16 UI shards passed on #425, including Extensions, PDF Editor, and Image Editor.



## 2026-09-23 validation follow-up: Integrated Regression #426

The branch tip was verified as `7de8094a6eba645cab94370077db74267ada5a7b` before this follow-up. Integrated Regression #426 built the matching Night and AnimePahe APKs successfully and completed 31 Android 16 provider instrumentation tests successfully. This includes installed AnimePahe service discovery after the SDK snapshots Messenger request code and Bundle data before asynchronous dispatch; the installed descriptor regression is now green. The same run's ARM64 APK #107, Groq Key Pool #385, and all 13 Android 16 UI shards passed, including Image Editor.

The synthetic-MP4 video interaction test still failed with 0 colored viewport pixels. Logcat confirms the TextureView was 709×1536, attached/shown, and had a valid native surface; libVLC's surfaces-created callback ran and playback began. The H.264 software decoder initialized, but there was no VLC Vout event. This narrows the remaining failure to video-output selection/creation, after the native surface readiness gate.

The provider suite's repeated `create_options` persistence regression remains green. The screen-level question about duplicate option cards still lacks screenshot/interaction evidence; do not classify it as visually verified. Groq 429 TPM remains a provider quota failure. The #426 Image Editor UI suite passed; the earlier #424 screenshot review showed a full-width clear preview and full-resolution export, while physical-device comparison remains outstanding.

Next video experiment: on virtual devices only, explicitly select VLC's `android_display` vout and retain the synthetic-MP4 pixel assertion. Physical devices continue to use their existing native playback path. Verify this through GitHub Actions before calling video fixed.


## 2026-09-23 validation follow-up: Integrated Regression #427

Integrated Regression #427 was built from `172021d37630f794d46c254074ac0ade41ea2375`. The build passed, Night ARM64 APK #108 and Groq Key Pool #386 passed, and provider instrumentation completed all 31 Android 16 tests successfully. The installed AnimePahe service discovery regression passed, confirming the Messenger request snapshot fix against the matching APKs. The separate AnimePahe extension regression #44 also passed at the preceding SHA.

The #427 synthetic-MP4 playback check still failed with 0 colored viewport pixels. The active TextureView was 709×1536, attached and shown, with native surface and libVLC surfaces-created readiness true; the H.264 software decoder started, but no VLC Vout event followed. Explicit `--vout=android_display,none` did not change the result. This experiment is not a video fix.

All Android 16 UI shards passed except Extensions. Its preview launched, but three attempts at the same SHA failed because the UI hierarchy never contained the expected `Extensions` heading; this suite passed on #426. The Providers UI shard's initial Android Emulator package download failed with `unknown archive`, then passed on same-SHA retry. Image Editor passed on #427; the earlier #424 screenshot review still confirms a full-width clear preview and full-resolution export, with physical-device comparison outstanding.

The repeated `create_options` persistence test remains green. Identical visible option-card repetition still lacks screen-level screenshot/interaction evidence. Groq 429 TPM remains a provider quota failure.

Next video experiment: virtual devices use TextureView, so switch their VLC output from Android Surface to GLES (`gles2,none`); keep the software H.264 synthetic-MP4 fixture and colored-pixel assertion. Physical devices continue on the native VLC Surface path. Run the full matching APK/video Actions checks before drawing a conclusion.


## 2026-09-23 validation follow-up: Integrated Regression #428

Integrated Regression #428 was built from `35cb1590547345bb86804167089c0162cc476c5b`. The build passed, Night ARM64 APK #109 and Groq Key Pool #387 passed, and all 31 Android 16 provider instrumentation tests passed, including live AnimePahe descriptor discovery from the installed matching extension APK. Every Android 16 UI shard passed, including Extensions and Image Editor.

The synthetic-MP4 video check remains red: 0 colored viewport pixels despite a 709×1536 attached/shown TextureView, valid native surface, libVLC surfaces-created callback, and H.264/AAC decoder startup. The explicit virtual-device `gles2,none` output did not produce a VLC Vout event. This does not fix video playback.

The repeated `create_options` persistence test remains green; duplicate visible option cards still have no screenshot/interaction proof. Image Editor UI passed; prior #424 screenshot review found the preview clear/full-width and export full resolution, with physical-device comparison outstanding. Groq 429 TPM stays classified as a provider quota failure.

Next diagnostic: raise VLC verbosity on virtual devices while keeping GLES vout and print the matching VLC/logcat lines from the video Actions shard. Use the resulting module-selection or output-creation error to choose a renderer change. Do not call this fixed while the pixel assertion is zero.


## 2026-09-23 validation follow-up: Integrated Regression #429

Integrated Regression #429 ran on `1e8dfe996a4f6308e497e92dbc8438c64141cce1`. APK packaging succeeded; Night ARM64 APK #110 and Groq Key Pool #388 passed. Android 16 provider instrumentation passed all 31 tests, including installed AnimePahe discovery into the enabled model inventory. Every UI shard passed, including Providers, Extensions, Integrations, Browser, and Image Editor. The provider artifact reports `OK (31 tests)`.

- The repeated-options persistence regression remains green. The provider UI artifact is for provider settings, not chat option selection, so whether identical cards visibly repeat remains unverified. Keep Groq 429 TPM classified as provider quota failure.
- I inspected the #429 Image Editor screenshots. The preview is clear and spans the available screen width. The edited JPEG exported at 4089×4087 from a 4121×4116 source; the untouched export matches the source dimensions and remains byte-for-byte preserved. Physical-device comparison remains open.
- The video screenshot remains entirely black (0 colored viewport pixels). Logcat confirms the TextureView was 709×1536, attached, shown, and surface-ready before VLC playback. Software H.264 decoding started and selected yuv420p, but no Vout event or video-output module selection appeared. Verbosity 3 exposed no useful output-creation error.

Next experiment: keep the synthetic MP4 and surface-created gate, retain VLC verbosity 3, and remove the forced `gles2,none` option on virtual devices so libVLC can negotiate its default output. This tests default output selection after both forced `android_display,none` and `gles2,none` failed. The physical SurfaceView path is unchanged.


## 2026-09-23 validation follow-up: Integrated Regression #430

Integrated Regression #430 ran on `15417a829f370e07c0b214bab344b30e6c63d9e1`. The Gradle build succeeded; ARM64 APK #111 and Groq Key Pool #389 passed. Android 16 provider instrumentation passed all 31 tests, including `installedAnimePaheIsRefreshedIntoTheEnabledModelInventory`. The descriptor is visible to Night's enabled model inventory from the installed matching APK. Providers, Extensions, Integrations, Browser, Memory, PDF Editor, Image Editor, and the other completed UI suites passed. Main Tabs failed before app interaction after repeated ADB exit-code-1 failures during emulator setup.

- The repeated `create_options` persistence regression remains green in the provider test coverage. The #430 Providers UI artifact covers provider settings and does not show chat option selection; whether identical option cards visibly repeat remains unverified. Keep Groq 429 TPM separate as provider quota failure.
- The #429 Image Editor screenshot review remains valid: clear full-width preview; edited export 4089×4087 from 4121×4116 source; untouched export matches the source exactly. Physical-device comparison remains open.
- #430 video tested libVLC's default renderer selection on the virtual device, with verbosity 3 and the native-surface readiness gate retained. This also yielded 0 colored viewport pixels. The screenshot is black; the 709×1536 TextureView is attached/shown and surface-ready, software H.264 decode starts, and no Vout event follows. Default negotiation does not resolve the output failure; prior forced Android display and GLES selections also failed.

Next diagnostic should compare a non-libVLC Android renderer with the same generated H.264/AAC fixture. That will determine whether the failure is isolated to Night's libVLC output path or also affects the emulator's app-window video surfaces before selecting another production playback change. Do not call the video issue fixed. The physical device remains unverified in this CI pass.


The next diagnostic source pass adds a controlled Android `VideoView` baseline to the video Actions job. It plays the same generated local MP4 before launching Night's VLC viewer and records its screenshot/pixel result and platform decoder logs. This is a diagnostic route in the existing preview Activity only, selected by a test intent extra; the normal Night viewer remains on its current code path. The #431 Actions result is pending.
