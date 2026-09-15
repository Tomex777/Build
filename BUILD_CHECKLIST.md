# Keyboard — aggressive build and QA checklist

This checklist is a release gate, not a marketing list. An item is checked only when the current source or a real build/test result proves it. Planned behavior stays unchecked.

## 1. Repository and architecture
- [x] Work isolated on `keyboard-ci`; `main` is untouched.
- [x] Native Android project, Kotlin + Jetpack Compose.
- [x] Real `InputMethodService` declared with `BIND_INPUT_METHOD`.
- [x] Multi-module foundation: `app`, `core:model`, `core:data`.
- [x] Room, DataStore and Hilt foundations wired.
- [ ] CI unit/lint/APK gate passes.
- [ ] Android 16 emulator instrumentation gate passes.

## 2. Keyboard layout and real input
- [x] Borderless default visual model inspired by SwiftKey Beta structure.
- [x] QWERTY letter pane.
- [x] Permanent bottom-left `123` mode key, independent of optional number row.
- [x] Main symbols pane.
- [x] Secondary symbols pane.
- [x] `ABC` return key from symbol panes.
- [x] Optional number row preference.
- [x] Shift / one-shot capitalization source path.
- [x] Double-tap Shift / caps-lock source path.
- [x] Unicode-codepoint backspace source path.
- [x] Editor-action-aware Enter source path.
- [x] Secondary-character long press source path.
- [ ] Backspace hold/repeat verified on emulator/device.
- [ ] Email, URL, number, multiline, search, password and chat fields verified end-to-end.
- [ ] RTL behavior verified.

## 3. Cursor / spacebar trackpad
- [x] Long-press/drag spacebar gesture implemented.
- [x] Gesture calls the real `InputConnection.setSelection` path.
- [x] Selection is clamped to document bounds.
- [ ] Caret remains visible and moves correctly in several real Android text fields.
- [ ] Multiline and selected-text cursor movement verified.
- [ ] No accidental spaces while trackpad mode is active.

## 4. Setup lifecycle
- [x] Enable action opens Android IME settings.
- [x] Select action opens Android input picker.
- [x] Setup completion is derived from enabled IMEs + `DEFAULT_INPUT_METHOD`, not a fake local flag.
- [x] `Finish setup` is conditionally removed once setup is complete.
- [ ] Setup disappearance/persistence verified in emulator after relaunch.

## 5. Toolbar and suggestions
- [x] Toolbar and prediction strip are separate surfaces.
- [x] Clipboard toolbar entry.
- [x] Custom-vector emoji toolbar entry.
- [x] Voice entry.
- [x] Editor entry.
- [x] Tone entry.
- [x] Contextual Research entry.
- [x] Input-method picker entry.
- [x] Small on-device suggestion engine provides a non-empty prototype path.
- [ ] Production prediction model.
- [ ] Autocorrect engine and aggression levels.
- [ ] Undo-autocorrect interaction.
- [ ] Swipe typing and trail.

## 6. Clipboard
- [x] System clipboard capture path.
- [x] Sensitive password fields suppress capture.
- [x] Search.
- [x] Pin / unpin.
- [x] Pinned clips do not expire.
- [x] Configurable default retention: 1h, 2h, 6h, 12h, 24h, end-of-day, never.
- [x] Per-item retention control.
- [x] Link, phone, address and text categorisation in repository.
- [x] Maximum unpinned history enforcement.
- [x] Pinned-at-top / free-order preference.
- [x] Hold-drag reorder gesture.
- [x] Swipe left to delete.
- [x] Undo snackbar after item deletion.
- [x] Clear-unpinned repository operation.
- [ ] Address/text filter chips completed in app UI.
- [ ] Batch clear UI with Undo.
- [ ] Custom-duration retention input.
- [ ] Real Android 16 clipboard capture restrictions/behavior verified.
- [ ] Expiry verified across process death and clock/day boundary.

## 7. Per-key editor
- [x] Tap individual keys to select.
- [x] Type-to-select keys (`sybuaiwkve` style).
- [x] Selection persists while tweaking controls.
- [x] Base theme + per-key override/delta data model.
- [x] Live HSL slider color updates.
- [x] Borderless style.
- [x] Ghost style.
- [x] Invisible-fill style.
- [x] Border enable source path.
- [x] Corner radius per key.
- [x] Label size per key.
- [x] Bold / italic per key.
- [x] Secondary-character visibility.
- [x] Theme persistence in Room.
- [x] Theme JSON codec foundation.
- [ ] Drag-across keyboard bulk selection.
- [ ] Full color wheel UI, swatches and hex entry.
- [ ] Border thickness/color controls exposed in editor UI.
- [ ] Shadow controls.
- [ ] Font-family picker and label-color UI.
- [ ] Per-key width/height/layout editing.
- [ ] Per-key image/sticker/emoji decoration.
- [ ] Spacebar custom decoration.
- [ ] Theme duplicate/import/export/share/screenshot-card flows.
- [ ] Seasonal theme scheduling.

## 8. Emoji
- [x] Emoji toolbar/panel exists.
- [x] Picker artwork uses custom Compose vector/Canvas shapes rather than rendering system emoji artwork.
- [x] Selecting art inserts the corresponding Unicode emoji into the target app.
- [ ] Full custom emoji library.
- [ ] Categories/search/recents/favorites in native app.
- [ ] Rendering/performance stress test with large pack.

## 9. AI and voice scope
- [x] AI scope kept to Editor, Tone and Contextual Research only.
- [x] Server URL preference exists.
- [x] No fake AI response is shown when backend is absent.
- [ ] Explicit send/consent interaction before online text processing.
- [ ] Editor client and replacement flow.
- [ ] Tone modes and replacement flow.
- [ ] Contextual Research client/results flow.
- [ ] Whisper recording/upload/transcription client.
- [ ] Text-to-Speech client/playback.
- [ ] Translation client.

## 10. Privacy and security
- [x] Cleartext network traffic disabled.
- [x] App backup disabled.
- [x] Password input types detected as sensitive by IME.
- [x] Clipboard capture suppressed in sensitive fields.
- [ ] Payment/private-field matrix broadened and tested.
- [ ] Incognito mode implementation.
- [ ] Sensitive fields suppress prediction learning and every online tool.
- [ ] Network requests require the intended user action only.

## 11. UI / visual QA
- [x] Default IME source is borderless rather than boxed 3D keys.
- [x] `123` is present in the correct bottom utility row.
- [x] Main app has Home, Editor, Clipboard, Settings surfaces.
- [ ] Actual emulator screenshots compared against the approved SwiftKey Beta references.
- [ ] Toolbar icon optical size/stroke consistency pass.
- [ ] One-handed mode.
- [ ] Floating/resizable keyboard.
- [ ] Portrait + landscape QA.
- [ ] Small-screen and large-screen QA.

## 12. Accessibility / performance / regression
- [ ] TalkBack/content-description audit.
- [ ] Touch target audit.
- [ ] Font-scale audit.
- [ ] Haptic preference respected by all key paths.
- [ ] IME cold-start latency measured.
- [ ] Keypress latency sanity test.
- [ ] Clipboard/emoji panel memory stress test.
- [ ] Logcat crash/error scan after full QA flow.
- [ ] Fixed issues rerun through regression checklist before handoff.
