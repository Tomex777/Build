# MirrorChess 1.2

A **Night** app.

A native Android chess-learning app built with Kotlin + Jetpack Compose. MirrorChess uses a play-first interface: choose a Maia-strength opponent or a progressively learned **Mirror Me** opponent, play, and use the human move-comparison coach when wanted.

## Android baseline

- package: `com.night.mirrorchess`
- minSdk: **36**
- targetSdk: **36**
- compileSdk: **36**
- AGP: **9.1.1**
- Kotlin / Compose compiler plugin: **2.2.10**
- Compose BOM: **2026.02.01**
- Java toolchain: **17**

## Included product flows

### Navigation
- Play is the only root screen
- A settings gear opens full-screen drill-down settings
- No permanent bottom navigation

### Play
- Maia 1200 / 1800 / 2200 opponents
- Play White, Black, or random
- Tap or drag pieces
- Legal move markers plus restrained translucent selected/last-move highlighting
- Castling, en passant, promotion chooser
- Checkmate, stalemate, threefold repetition, fifty-move and conservative insufficient-material handling
- Full-turn undo, board flip, PGN export and resign in the in-game overflow menu; rematch at game end
- Automatic recovery of the exact active position after process death
- Human move-comparison coach and Maia W/D/L output when the real model is installed
- Compact recent-game archive, position-by-position review and PGN export

### Mirror Me
- Learns automatically from the human player's moves in every played game
- Keeps learning while the player is facing Maia or Mirror Me
- Never trains from the opponent's generated moves
- Tracks exact position -> move memories plus capture/check/castling/center/piece-use tendencies
- Shows a learning-progress/readiness indicator before Mirror Me unlocks
- Optionally imports Chess.com/Lichess/other PGNs as a head start
- Merges imported history into existing learning instead of replacing it
- Re-ranks Maia's human move distribution toward the player's learned habits

Mirror Me is a **personalization layer around Maia**, not neural-network weight retraining on the phone.

### Local Maia model
The app works without the neural model by falling back to its built-in preview opponent. Open **Play → Settings → AI Model** to download Maia-3 5M directly in-app. Manual ONNX import remains available as an advanced fallback.

The app verifies the expected SHA-256 before installing it. Once installed, chess inference is local.

Model file: `maia3-5m.fp16.onnx`

Expected SHA-256:
`ca22fc3031975932e693f9758149302efc177749165443ed52de828add8864fa`

See `MODEL_SETUP.md` and `THIRD_PARTY_NOTICES.md`.

## Open in Android Studio

1. Extract the ZIP.
2. Open the `MirrorChess` folder in Android Studio.
3. Let Gradle sync.
4. Use an Android 16 / API 36 device or emulator.
5. Run `app`.
6. Tap the settings gear on Play, then open **AI Model** to install Maia when wanted.

## Validation performed in this source snapshot

This snapshot is verified in a clean Android 36 build and emulator environment. See `BUILD_STATUS.md` for the exact test matrix and evidence.

Validated:
- starting-position perft: **20 / 400 / 8,902 / 197,281**
- checkmate detection
- FEN round-trip
- PGN multi-game parsing
- Mirror profile build + serialization round-trip + continuous-learning merge smoke
- conservative insufficient-material behavior
- Maia move-vocabulary encoding including Black-to-move normalization
- checksum-pinned Maia-3 model startup and live inference
- Android process-death recovery, full-turn undo, archive/review, and no-crash screen tour

See `BUILD_STATUS.md` for the detailed results.
