# Poetry — native Android writing & card studio

The Poetry app lives in `Tomex777/Build` on the **`poetry-android`** branch,
under the `poetry-android/` directory.

- Kotlin + Jetpack Compose, target/compile API 35, **minSdk 26 (Android 8.0)**.
- Two distinct workflows: distraction-free poem writing and portrait-card design.
- Cards: curated templates, home-template quick start, custom backgrounds,
  searchable SVG-derived Android VectorDrawables, editable objects and layers,
  and multi-page poem slides.
- Offline draft persistence using Android AtomicFile, with an emergency recovery
  copy for unreadable libraries.
- PNG card export at 1080×1920 or 2160×3840; Android 10+ uses MediaStore,
  Android 8–9 uses app-owned storage + FileProvider for sharing.
- View-only pinch zoom and two-finger pan, with a single **Zoom to fit** reset.
- Element browser categories are generated from real library entries. Only one
  insertion action exists per element tile, with a separate favourite action.
- Empty card export does not silently insert preview sample prose.
- The editor uses custom-styled Compose inputs and Android vector imagery.

## ABR acceptance criteria

1. No duplicate actions or nonfunctional controls; avoid filler labels.
2. Avoid default platform text-input visual styles **and default Android sliders**. Every slider must use the bespoke branded rail, knob, accessible tap/drag handling, and a readable live value. See root `ABR.md`.
3. Keep icons vector-based, with legible tap targets.
4. **Never raise minSdk above 26.**
5. Verify successful native compile, launch, UI interactions, and persistence.
6. Exercise API 26 and API 35 emulator tests and capture screenshots/logcat.
7. Treat **build passing** and **functional testing passing** as separate checks.

## GitHub Actions QA

`.github/workflows/poetry-android-qa.yml` assembles a debug APK and Android
test APK, runs unit tests, and uses API 26/35 emulators for the write → design
→ elements filtering smoke path. It uploads APKs, screenshot and logcat outputs.
Check the latest GitHub Actions run before claiming testing has passed.

HTML design previews are only review companions, not the production Android app.
Drawing tools and MISE are intentionally excluded from Poetry.
