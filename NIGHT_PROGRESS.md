# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Last fully verified app commit: `f5fd3bb283129c0dd3a62f37a98996e4cee55c08`
- Verified CI:
  - Night Integrated Regression `35633953916`: PASS
  - Android 16 integrated build: PASS
  - Android 16 Extension Configuration UI: PASS
  - Android 16 message blocks: PASS
  - Android 16 Live Voice UI: PASS
  - Android 16 provider HTTP failover: PASS
  - Android 16 video: PASS
  - Android 16 PDF editor: PASS
  - Android 16 image editor: PASS
  - Android 16 Mihon reader: PASS

## Message system
- Extension-owned message types are explicit and namespaced. Extensions declare the message type/template the AI/runtime may emit; Night validates ownership instead of guessing from arbitrary payloads.
- Extension tool results can emit validated `night_message` / `night_messages` payloads which Night persists and renders in the chat.
- Generic structured Night blocks are versioned and persisted across reloads, including text, code, copy, table, progress, level/XP, tool, error/retry, sources, confirmation, permission, question/options, diff, connection/auth, and extension blocks.
- Older rich result cards now have persisted restoration coverage for buttons, file result, anime, rich link, generated image, creation progress, image search, download, and generic tool result.
- Existing manga/choice/extension-specific restoration remains in place.
- Level/XP progression is now a reusable structured message block rather than a hard-coded role-only message.

## Extension Configuration
- First-class extension template: `configuration_card`.
- Supported field families: toggle, single choice, multi-choice, number, range, text, action, and Advanced fields.
- Configuration values persist per extension/configuration ID.
- Precedence: explicit task override -> saved extension setting -> extension-provided default.
- Save rewrites the existing card without bumping the chat timestamp or marking a new message event.
- Configuration actions dispatch typed values back through the extension message-action registry.
- Android 16 emulator regression verifies toggle, single choice, multi-choice, Save, Advanced expansion, and crash/ANR safety.

## Other locked Night rules
- Night calling is user ↔ AI Live Voice only. No person-to-person/WebRTC/FCM calling and no Calls bottom-navigation tab.
- PDF remains full screen; media/PDF edits create derivative messages rather than mutating originals.
- minSdk remains 26.
- No stickers.

## Next phase
- Browser message type.
- Full Night browser/session sharing.
- Verification/authentication flow built on the browser message.
- Then Memory & Summary.
- Then Library + Tools integration.
- Then Provider UX/Admin.
