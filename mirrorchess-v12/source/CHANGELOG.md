# Changelog

## 1.2.0 — Reliability and finish pass

- Repaired the Gradle wrapper with the complete official Gradle 9.3.1 launcher.
- Added rollback-safe atomic storage for active games, the recent-game archive, and Mirror learning data.
- Made archive recovery skip an individual damaged entry instead of discarding every saved game.
- Hardened FEN, UCI, PGN line-comment, promotion, and en-passant validation.
- Corrected full-turn undo for both player colors and prevented undo before Black has made a move.
- Kept a completed active game recoverable when archiving fails and made the game-over message truthful about save status.
- Prevented a recovered completed game from being counted twice in Mirror learning.
- Made model download/import cancellation-safe, checksum-cached, atomic, and runtime-validated before reporting success.
- Prevented a corrupt app-private model from taking precedence over a trusted bundled model.
- Added visible model-busy states, install/remove guards, and confirmation before model or Mirror-profile deletion.
- Added exact-username enforcement so PGN imports never silently learn both players' moves.
- Polished Night branding, typography, dark-surface content colors, system bars, cards, selection states, board palette, progress states, and setting previews.
- Added clear preview-engine labeling when Maia is absent without returning persistent navigation clutter.
- Added accessibility descriptions for settings, game actions, switches, and all 64 board squares.
- Added move-history auto-scroll, a thinking indicator, accurate starting-position review text, full recent-game access, and confirmation before replacing an unfinished game.
- Added Android/JUnit regression tests for perft, mate/draw rules, castling, en passant, promotion, FEN, PGN, Mirror learning, and Maia encoding.

## 1.1.0 — Play-first redesign

- Replaced the permanent Play / Mirror / Settings bottom navigation with a single Play home and a settings gear.
- Rebuilt Play around compact opponent chips, a focused selected-opponent card, Play As controls, resume, and compact recent games.
- Moved Mirror Me into the Play opponent list; its management now lives under Settings.
- Added Mirror learning progress and a readiness gate before Mirror Me becomes playable.
- Mirror now continuously learns only the human player's moves during normal Maia games and Mirror games.
- PGN imports now merge into existing Mirror learning instead of replacing it.
- Added Settings drill-down screens: AI Model, Mirror Me, Board & Pieces, Gameplay, Coach, Game Data, and About & Privacy.
- Moved PGN import/export into Game Data; kept end-of-game/review export where it is contextually useful.
- Kept direct Maia model download as the normal path and moved manual ONNX import into the AI Model screen.
- Removed repeated LOCAL/OFFLINE/PREVIEW status clutter from Play and the live game header.
- Moved Undo, Flip board, Export PGN, and Resign into the live-game overflow menu to free board space.
- Reworked selected-square and previous-move highlights to translucent overlays instead of replacing the square color.
- Added piece appearance presets and optional subtle piece shadows.
- Preserved and re-ran the chess/core smoke suite successfully through perft depth 4 (197,281).

## 1.0.0

- Added Play / Mirror / Settings product shell.
- Added White, Black and random-color game starts.
- Added proper promotion chooser.
- Added resign, rematch, full-turn undo and finished-game review.
- Added PGN parsing/export and FEN serialization.
- Added threefold, fifty-move and conservative insufficient-material draw handling.
- Added active-game recovery and recent-game archive.
- Added in-app Maia model download/import/delete with SHA-256 verification.
- Changed Maia loader to prefer app-private model storage while retaining development asset support.
- Added Mirror Me PGN personalization profile and exact-position memory.
- Added board palettes, legal-move/coordinate/coach/haptic settings.
- Added custom launcher vector and privacy hardening (`allowBackup=false`, HTTPS-only cleartext policy).
- Kept Android baseline at minSdk/targetSdk/compileSdk 36.

## 0.2.0

- Custom vector chess pieces.
- Tightened game layout and coach panel.
- Maia opponent presets and learning comparison UI.

## 0.1.0

- Initial playable chessboard, rules engine and Maia inference boundary.
