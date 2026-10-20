# TAC Hymnbook — text-only Android reader

Independent offline-first **Yorùbá and English hymnbook** project for The Apostolic Church Nigeria. This project is not an official TAC publication.

## Current scope — v0.8.0

**No songs, audio recordings, streaming, YouTube searches, audio downloads or optional listening/learning screens are included in the app.** The user chose to concentrate on the hymn texts. The Android manifest requests **no INTERNET permission**.

- Kotlin / Jetpack Compose; package `com.night.tachymns`; `minSdk 26` (Android 8+).
- Read hymn verses offline with adjustable text size, Yorùbá/English selection when a translation actually exists, selection/copy, swipe or button navigation, bookmarks and continue reading.
- Search by printed number, first line, title and imported lyric text, ignoring Yorùbá tone marks for search.
- Light and dark themes, custom-styled search input and vector icons.
- **Redemption** and **Àkọkún** are separately numbered, not interchangeable.

## Content status — be accurate

| Collection | Numbered entries | English first-line index | Yorùbá first lines / complete text |
| --- | ---: | --- | --- |
| Redemption | 800 (1–800) | 800 titles indexed from RH2006 (requires independent printed-edition check) | **57** Yorùbá first-line titles identified, but only **18** bilingual working lyric drafts (hymns 1–17 and 50). No other complete Yorùbá lyric texts imported. |
| Àkọkún | 305 (1–305) | 8 linked candidate equivalents | 299 opening-line titles from photographed index; **no full lyric texts** imported yet. |

In Redemption, **7 additional historical English-only alternative full texts** are readable (178, 187, 188, 190, 195, 200, 207), and their stanzas are **not** claimed to exactly match the printed TAC edition. Therefore **25/800** numbered Redemption hymns have any readable full or draft verses; **18/800** have bilingual working drafts. A numbered title **is not the complete hymn**.

The source index is `data/redemption-hymnal-catalogue.json`, exported identically to `android/app/src/main/assets/redemption_index.json`.
- English numbered first lines derived from RH2006 Hymnary index: https://hymnary.org/hymnal/RH2006
- Yorùbá working drafts from the photographed TAC book: `data/drafts/`. Tone marks, stanza order and reuse rights still require review.
- Àkọkún independent numbering: `data/akokun-collection.json` and `android/app/src/main/assets/akokun_index.json`.

## Completing Yorùbá Redemption

The remaining **782** numbered Redemption hymns have no complete Yorùbá text in our library yet. Obtain a legitimate TAC Nigeria Yoruba/English digital master or clear photographs/scans with publishing permission. Match each first line to its **printed Redemption number**, verify tone marks, stanza count and choruses against the physical edition, and keep differences labelled (historical/English alternatives cannot silently replace TAC translations).

For authorized bilingual batches:
1. Prepare an approved JSON document with both language verses, source-page numbers, credited reviewer and permission reference.
2. Validate with `python3 tac-hymnbook/tools/redemption_import.py approved-batch.json --output tac-hymnbook/data/approved-redemption.json`.
3. Generate `python3 tac-hymnbook/tools/build_asset.py`, then test and review the APK visually.
4. Do not set `rights_verified` or `printed_edition_verified` merely because a random website or competing hymn app has the same first line.

The importer is **not** a substitute for actual licence permission. `data/approved-redemption.json` remains empty until verified permitted sources are received.

## Build and tests

```bash
python3 -m unittest discover -s tac-hymnbook/tools -p 'test_*.py' -v
gradle -p tac-hymnbook/android :app:assembleDebug :app:assembleDebugAndroidTest
```

GitHub Actions: `.github/workflows/tac-hymnbook-android.yml` builds the APK, runs offline data and source checks, then installs the APK and runs **three Android instrumentation tests** on the API 28 emulator. The UI smoke verifies Yoruba and English hymn readers plus the independent Àkọkún index. Never equate a successful APK build with fully verified hymn content.

## Research archive

Older `data/audio-research-queue.json` and `data/audio-source-candidates.json` are dormant historical research records **not shipped in the APK**. No recording discovery, media libraries, song player, download or network capability is part of v0.8.0. None of their links establishes rights to bundle songs.

## App Building Rules

No duplicate controls, no filler copy, custom-styled inputs, real vectors, clear lyrics, correct Yorùbá diacritics, Android minSdk 26 and real emulator/device validation.
