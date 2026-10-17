# Poetry — offline Android poetry and portrait card studio

**Version:** 1.0.0 · **Minimum Android:** 8.0 (API 26)

Poetry is built under `poetry-android/` on the `poetry-android` branch of
`Tomex777/Build`. It is Kotlin + Jetpack Compose, without accounts,
network permissions, paid services, or AI models. It does not include
drawing tools or MISE Studio.

## Complete launch scope

- Private, offline poem library with editor, author field, draft recovery,
  lifecycle saves and persistent card designs.
- Distinct writing and Canva-like portrait card design experiences.
- Built-in theme templates, saved custom backgrounds, vector elements,
  favorite/recent elements, layered editing and accessible bespoke sliders.
- Multi-page cards, stanza splitting, custom text and layout controls.
- Portrait PNG rendering at 1080×1920 or 2160×3840, with full-resolution
  text/vector rasterization and error handling when text cannot fit.
- Android 10+: standard Pictures / Poetry save. Android 8–9: choose a document
  or folder via the system file picker. Sharing works across supported versions.
- Portable **JSON backup and restore**, including poem text, all slide
  overrides, card designs, placed vector elements and custom backgrounds.
  Import validates the archive and requests confirmation before replacement.
- Appearance themes and vector launcher icon; `minSdk = 26` is locked.

## Release acceptance / ABR

Every default Android/Material slider has been replaced by a functioning
custom control with a branded rail/thumb, live values and accessibility
semantics. See [ABR](../ABR.md).

GitHub workflow: `.github/workflows/poetry-android-qa.yml`.

Checks required before an **unsigned 1.0 release** appears as a GitHub artifact:
1. Compiled debug APK, instrumentation APK and unsigned release APK.
2. JVM tests and Android lint checks.
3. Android 8 (API 26) and Android 15 (API 35) emulator tests for the writer,
   selected controls, persistence, restart, JSON backup/restore, HD/4K export,
   real `content://` output streams, multiple pages and overflow errors.
4. Capture actual emulator screen images and 1080/4K native PNGs.
5. Verify `minSdk 26` and ensure INTERNET/ACCESS_NETWORK_STATE are absent.

**Signing:** The workflow deliberately does not request or store a production
keystore. The production artifact remains unsigned until a permanent key is
created and secured. A debug-signed artifact is available for direct installation
and testing, but must not be confused with the production release APK.

**Physical-device QA:** CI verifies two Android emulators, not every handset.
Before distributing publicly, perform an install/upgrade and document-picker
round-trip on a real Android 8–9 handset if available.

No separate AI step is needed for offline app operation.
