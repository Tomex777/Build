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

Integrated Regression #417 built both matching APKs and passed the Extensions and Integrations UI suites. The provider instrumentation installed and bound the AnimePahe service from the matching APK, but its raw descriptor IPC result was `{}`; the manager correctly marked the extension unusable at 0 tools / 0 message types. The source descriptor unit test expects 5 tools and 6 message types. Added service-side count/reply logging and provider-failure logcat artifact capture to identify whether descriptor construction or Messenger delivery loses the data on the next Actions run.

The #417 video regression again displayed a black preview. VLC parsed the fixture and started the H.264 decoder, but there was no Vout event; logs showed two surface attachment attempts a millisecond apart. A local uncommitted attach-generation guard cancels stale queued surface attachments and needs Actions verification.

The #417 Image Editor UI job was blocked by an Android System UI not responding dialog, and Memory UI hit an SDK archive installation error. Rerun those jobs after the next integrated workflow settles. Duplicate option-card behavior still needs on-device verification. The observed Groq `429 TPM` remains a provider quota failure, independent of these UI/extension issues. Black video preview and tiny image-editor preview/quality concerns remain open until reproduced and fixed.
