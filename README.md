# Keyboard

Native Android keyboard project built in Kotlin + Jetpack Compose. The current product name is intentionally just **Keyboard** until branding is finalized; the abandoned Hboard/Homiesphere name is not used.

## Current foundation

- Real `InputMethodService` with a Compose input view.
- SwiftKey-Beta-inspired borderless default layout.
- QWERTY pane plus a permanent bottom-left **123** mode key, symbol pane, extra-symbol pane and ABC return key.
- Toolbar + separate suggestion strip.
- Long-press/drag spacebar calls the real `InputConnection.setSelection` path for cursor movement.
- Room clipboard history with configurable expiry, per-item retention, pinning, search, categorisation, swipe-delete, undo and reorder support.
- DataStore settings.
- Per-key theme deltas and an initial editor with tap/type selection, live HSL controls, borderless/ghost/invisible styles, label styling and secondary characters.
- AI scope is intentionally limited to **Editor, Tone, Contextual Research**. The app stores a user-configured server URL but does not fake a backend response.

## Toolchain

- AGP 9.1.1 / Gradle 9.3.1 / JDK 17
- Kotlin 2.4.20
- Compose UI 1.12.1 / Material 3 1.4.0
- Room 2.8.5
- DataStore 1.2.1
- Dagger/Hilt 2.60.1
- compileSdk 37 / targetSdk 36 / minSdk 26

## CI

Pushes to `keyboard-ci` run:

1. static project-contract checks;
2. pure/unit tests;
3. Android lint;
4. debug APK assembly and artifact upload;
5. Android 16 emulator instrumentation on macOS.

See `BUILD_CHECKLIST.md`. Unchecked items are not claimed complete.
