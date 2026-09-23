# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Current source head: `ab3c4bec1f65e8ec56b80d469e96e9d16efcdbaa`
- Original real-device handoff baseline: `04c78f69c01192022daaeadc5ea73f92e673f06a`
- Latest validation:
  - Night Groq Key Pool: PASS
  - Night Integrated Regression at `ab3c4be`: GREEN

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
