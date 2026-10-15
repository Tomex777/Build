# Poetry — Android app

This is the dedicated project branch for **Poetry**, the offline-first native Android poetry writer and portrait-card designer.

- Repository: `Tomex777/Build`
- Branch: `poetry-android`
- App source directory: `poetry-android/`
- Android stack: Kotlin + Jetpack Compose
- Planned QA: GitHub Actions debug build, API 35 emulator and Compose smoke tests, UI screenshots, logcat and artifact capture.

**Project status (2026-10-15):** Branch created. This directory currently contains only bootstrap documentation; the full V8 project source from the ChatGPT working files has **not yet been committed**. Do not treat a workflow declaration as evidence that native build/emulator QA passed.

## Intended development sequence

1. Bring in the existing Poetry V8 Kotlin/Compose source and design assets under `poetry-android/`.
2. Compile and fix errors in GitHub Actions.
3. Run on Android emulator, capture logs/screenshots and test real gestures and text input.
4. Compare with Canva's documented editing workflow, prioritize on-canvas text controls, object selection, layers and carousel export.
5. Only lock the UI after native visual review and tests.

The HTML preview is a review companion, **not** the production Android app. Do not mix this project with MISE/drawing.
