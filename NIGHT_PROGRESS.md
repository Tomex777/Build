# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Current confirmed baseline: `04c78f69c01192022daaeadc5ea73f92e673f06a`
- Baseline status supplied by the latest real-device handoff:
  - Night Groq Key Pool: PASS
  - Night Integrated Regression: GREEN
  - Browser Android-16 failure passed on rerun at the same SHA without source changes; treat the earlier failure as CI/emulator flakiness.

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

Current local cleanup changes add deterministic message ordering, monotonic timestamps for new inserts, chronological completion of streamed assistant messages after tool results, reply/rich-extension context in provider requests, and a playable-audio-preserving STT failure message. Focused Android regressions have been added; they still need to run in CI.

This cleanup pass also moves the existing Scripts/Projects workspace to the third main tab, removes its Library shortcut and the duplicate Settings entry from the chat-list menu, keeps U as the configuration home, persists Library filter state, and propagates the saved accent through major app surfaces. Provider add/edit/key/model forms now use bottom sheets. Appearance accepts validated custom accents and imported TTF/OTF fonts; appearance tool calls use validated setting/value actions. Android unit/instrumentation and UI regression runs remain pending because this workspace has no Gradle distribution or `adb`, and the wrapper download is blocked by network access.

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
