# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Last fully verified app commit: `f5fd3bb283129c0dd3a62f37a98996e4cee55c08`
- Verified CI:
  - Night Integrated Regression `35633953916`: PASS
  - Android 16 build: PASS
  - Android 16 provider HTTP failover: PASS
  - Android 16 video: PASS
  - Android 16 message blocks: PASS
  - Android 16 PDF editor: PASS
  - Android 16 Live Voice: PASS
  - Android 16 Extension Configuration: PASS
  - Android 16 image editor: PASS
  - Android 16 Mihon reader: PASS

- Message system status:
  - Extension Configuration is implemented and emulator-verified.
  - Supported config controls: toggle, single choice, multi-choice, number, range, text, action, Advanced, Save.
  - Saved extension settings persist by extension/config ID.
  - Task overrides take precedence over saved settings, then extension defaults.
  - Extensions can declare their own namespaced Night message types.
  - Night validates extension ownership/template instead of guessing message type.
  - Extension tool results can persist/render declared Night messages.
  - Structured Night blocks are versioned and persist across reload.
  - Older rich cards now have persistence paths.
  - Reusable Level/XP progression block added.
  - Extension configuration updates replace the existing card without bumping chat chronology.

- Night calling rule: user ↔ AI Live Voice only. No human-to-human/WebRTC/FCM calling and no Calls bottom tab.

- Current next phase:
  1. Browser message type.
  2. Full browser/session sharing.
  3. Verification flow.
  4. Memory & Summary.
  5. Library + Tools integration.
  6. Provider UX/Admin.
