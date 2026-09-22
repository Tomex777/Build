# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Current app head when this roadmap was refreshed: `1f2d6367ae47225f2116742766a1ec738bec2214`
- Exact-head status at refresh:
  - Night Groq Key Pool: PASS
  - Night Integrated Regression: build/unit tests PASS
  - Android 16 main tabs/FAB/status-bar: PASS
  - Android 16 Providers: PASS
  - Android 16 Memory: PASS
  - Android 16 Extensions: PASS
  - Android 16 Message Blocks: PASS
  - Android 16 Extension Configuration: PASS
  - Remaining Android 16 suites were still running; do not mark the head fully verified until the workflow completes.

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
- Three-tab shell: Chats / Library / You
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

- Bottom navigation is exactly Chats / Library / You.
- Integrations belongs in Settings/You, never as a fourth tab.
- Mihon Reader defaults to true immersive fullscreen and hides system bars.
- Normal Night screens respect status/navigation safe insets.
- Full Night Browser is separate from mini browser verification cards.
- Groq keys are one rotating pool, never Primary/Backup/Burst roles.
- Default app accent is Night wine/pink, not hard-coded WhatsApp green.
- Recent emoji means the latest 30 USED emoji.
- Fluent Emoji is the app-wide emoji renderer.
- Text/caption message timestamps use WhatsApp-style trailing inline metadata.

## Current phase: real extension rollout

The shared framework is frozen unless a real extension exposes a concrete missing capability.

Reference extension order:
1. First anime extension APK
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
