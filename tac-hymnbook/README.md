# TAC Hymnbook

Native, offline-first Yorùbá/English hymn reader for Android. The photographed printed TAC hymnbook determines the local hymn numbering. This is an independent project and not an official church release.

## Current implementation

- Kotlin + Jetpack Compose, Android **minSdk 26** (Android 8+), targetSdk 34.
- Custom-styled search input; lookup by number, title, English equivalent or any bundled lyric line. Yorùbá accent-insensitive search.
- Hymn list, availability labels, reader, previous/next, bookmarks, continue reading, persistent text-size buttons, light/dark themes.
- Genuine Android vector drawable icons (not Unicode symbols or emoji).
- All data bundled locally in `android/app/src/main/assets/hymns.json`. No account, ads, network dependency, remote API, or redundant volume sliders.
- Initial photographs cover pages **1–4**: local hymns **1–11** catalogued. **4** and **7** have Yorùbá transcription drafts pending orthographic verification. Others are indexed but no lyrics are displayed until accurately entered. English switch is implemented but appropriately disabled until an English text is added.
- Hymn 11 is incomplete in the photos; later pages or verified online equivalents can complete it.

## Build

GitHub Actions: `.github/workflows/tac-hymnbook-android.yml` validates JSON, builds a debug APK and installs/launches it on an Android emulator. APK is uploaded as `TAC-Hymns-debug-apk`.

Locally with Android SDK, JDK 17 and Gradle 8.9:

```sh
python3 -m unittest discover -s tac-hymnbook/tools -p 'test_*.py' -v
cd tac-hymnbook/android
gradle :app:assembleDebug
```

## Add photographed pages

1. Determine the original printed page numbers and the corresponding TAC hymn number(s). Do not use online hymnal numbering.
2. Extend `data/hymns-001-011.seed.json` for new photographed pages (later move to per-batch seeds).
3. For a hymn with complete checked photo-backed lyrics, create `data/drafts/hymn-NNN.yo.json`. Maintain the exact structure of hymn 4/7 drafts, including verse lists and source page references.
4. Use outside online collections only to help identify/match wording. Missing songs may use an alternate online text by user preference, but must be tagged as an alternate and satisfy source/reuse checks.
5. Run `python3 tac-hymnbook/tools/build_asset.py` to regenerate the bundled JSON, run tests, then build.
6. Preserve Yorùbá diacritics, verses, metre and attribution when available. Do not silently invent uncertain words or claim a full text is verified based on an opening-line match.

## Planned

- Import pipeline for later photo batches with per-collection keys for Appendices 1 and 2.
- Bilingual verified verses (only when a matching version is available).
- Continue expanding the real text database; accessibility and device-specific UI tests.

