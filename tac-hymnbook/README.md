# TAC Hymnbook

Native, offline-first Yorùbá/English hymn reader for Android. The photographed printed TAC hymnbook determines the local hymn numbering. This is an independent project and not an official church release.

## Current implementation

- Kotlin + Jetpack Compose, Android **minSdk 26** (Android 8+), targetSdk 34.
- Custom-styled search input; lookup by number, title, English equivalent or any bundled lyric line. Yorùbá accent-insensitive search.
- Hymn list, availability labels, reader, previous/next, bookmarks, continue reading, persistent text-size buttons, light/dark themes.
- Genuine Android vector drawable icons (not Unicode symbols or emoji).
- All data bundled locally in `android/app/src/main/assets/hymns.json`. No account, ads, network dependency, remote API, or redundant volume sliders.
- Photographed spreads now cover pages **1–6**: local hymns **1–17** catalogued. **1–16** have bilingual working drafts from photos and traditional sources. **11** continues onto page 5 and is now present through its fourth stanza. **17** has a photographed opening and an online-matched continuation, pending comparison with the following printed page. Draft spelling and tone marks still require review.
- Hymn 17 continues beyond the current photo; its imported RH text is provisionally complete pending page 7.
- Reader swipe left/right moves between numbered hymns; language choice persists while browsing. Literal `\\n` in legacy texts is normalized to real line breaks.
- Dark mode uses warm charcoal, ivory and brass, rather than green-on-black.
- Build version: 0.2.0 (versionCode 2).

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


## Batch 2 provenance (pages 5–6)

Hymns 12–17 use the original TAC numbering and matching English Redemption Hymnal identities. Full Yoruba text for 12–16 is drawn from the user-supplied spread, with uncertain spelling still marked in source metadata. Hymn 17 uses the matching source for verses not yet photographed. Source status is retained in JSON but not shown as developer counters or badges in the public UI. No song is silently considered verified solely because a matching opening line was found.


## Index-first pilot: photographed page 437

- `data/index-page-437.json` captures **40 original printed hymn numbers** and visible Yorùbá opening lines from the alphabetical index; 21 have English hymn identities cross-checked against the same-number Redemption Hymnal entry, with remaining entries queued for matching.
- Original index readings that are shadowed, blurry, or uncertain stay tagged `needs_review`. Never invent Yorùbá diacritics as though verified.
- Hymn **50** is the **first index-only bilingual lyrics import**, via `data/hymns-050.seed.json` and `data/drafts/hymn-050.yo.json`: a prior user check confirmed the online Yorùbá opening verse against the physical book. Further verses/redistribution status remain working-draft rather than asserted exact TAC text.
- Previous speculative matches were corrected: **Rock of Ages = 341 (not 733)**; **To the Work = 568 (not 424)**; **The Mercy of God Is an Ocean Divine = 424**; **733 = Thine Arm, O Lord, in Days of Old**. Treat pairings by number **and** meaning.
- Only entries with actual bilingual verses belong in the public hymn reader. Unfilled index entries remain in internal research data, not broken user-facing hymn pages.


## Whole Redemption Hymnal and Àkọkún (October 2026)

The source `data/redemption-hymnal-catalogue.json` contains **all canonical hymn number identifiers 1–800**, in the correct Redemption Hymnal order. It is independently bundled for future offline indexing in `android/app/src/main/assets/redemption_index.json`.

**Do not confuse an index with completed lyrics.** At this stage:
- Number slots: **800/800**.
- Titles confirmed directly against the public RH2006 numbered first-line index: **177/800** (hymns 1–177).
- A further ~18 English names are tentative matches from the user's photographed page 437, and are explicitly tagged candidate, not verified.
- Complete bilingual working-draft hymn entries in the playable reader: **18** (1–17 and 50).
- No guessed lyrics or blank entries from the remaining numbered slots are presented as finished hymns.

**Àkọkún** means addendum/supplement in Yoruba hymnbook use. It may have its own numbering, distinct from the Redemption section. Its independent source record is `data/akokun-collection.json`, bundled in the app as `akokun_index.json`; its size and hymn identity have **not yet been established from this user's edition**. The existence of Methodist/Baptist Àkọkún lists does **not** establish that their hymns or numbering match this TAC edition.

Hymnary RH2006 enumerates the complete 800, but direct automated requests from GitHub Actions received HTTP 403. The automated external fetch is **disabled** rather than bypassing access controls. We manually verified the first 94 first-line entries, and the remaining metadata can come from photographs or appropriately licensed structured sources. Full lyrics require verse-level source checks and any necessary reuse rights: a traditional hymn's age does not automatically clear a later translation or arrangement.

To continue digitizing, use:
- Full public-domain or licensed English text matched to the Redemption title and hymn number.
- The printed TAC page or a rights-cleared Yoruba translation, preserving its stanza count.
- Separate collection keys for Àkọkún; do not give a shared number the same identity in both sections.
- Quality checks for text, accents, section boundaries, and physical-device UX before release.

### Àkọkún numbered ending confirmed

The user checked the physical TAC book and confirmed that the last Àkọkún hymn is **305**. Treat the separate collection as numbered 1–305, provisionally assuming no missing numbers. This is **not** a claim that its 305 titles or lyrics have already been imported: those remain empty until source material is available. Àkọkún numbering is distinct from the 800-hymn Redemption section.


## Àkọkún complete alphabetical index (photographed pages 459–467)

The user has now supplied **five photographs covering all nine consecutive alphabetical index pages, 459–467**. The nine raw photo-backed drafts live in `data/akokun-index-page-{459..467}.json`; they are combined into `data/akokun-collection.json` and bundled offline as `android/app/src/main/assets/akokun_index.json`.

- **305** independently numbered Àkọkún hymn identities reserved (different from Redemption 1–800).
- **302 printed index lines transcribed**, representing **299 distinct numbered hymn identities** in the current first pass.
- **Six numbers without a confidently identified first line yet:** 128, 188, 211, 219, 272, 281.
- **Three repeated numbers with distinct index lines** (potential multiple titles or transcription alignment errors): 138, 218, 288. The raw strings and page references are preserved for follow-up, not silently resolved.
- All Yorùbá titles and tone marks are **first-pass transcriptions**, not final approved readings.
- Online first-line candidates found for Àkọkún hymn **1** (“B'orúkọ Jésù ti dùn tó”) and hymn **47** (“Agbára ń bẹ nínú ẹ̀jẹ̀ Jésù”); they still need verse-level matching and reuse permission checks.
- **0 Àkọkún full lyrics currently imported**. The Android public hymn list does not expose blank pages for these index-only entries.
- Once lyrics are cleared and added, section-safe IDs `akokun:<number>` and the existing reader will keep bookmarks and navigation independent from `main:<number>`.

The index is now sufficient to begin source matching for almost the entire Àkọkún section. Additional page photography is only useful for correcting the six unresolved numbers or confirming individual variant lyrics, not for re-photographing this index.


## New in v0.5.2 — Index browsing with no blank hymn screens

- The real Android hub now exposes **Redemption** and **Àkọkún** as separate collections.
- Index browsing loads `redemption_index.json` and `akokun_index.json` from bundled assets; the 299 currently identified Àkọkún first lines are locally searchable by number or Yoruba text (with or without tone marks).
- Redemption has **Read**, **Index**, and **Saved** modes; Àkọkún opens straight to the **Index** until its lyric text is available.
- Only already-imported hymn texts open the verse reader. Index-only rows are reference entries, not dead-end reader pages. No progress counters, transcription statuses, or research badges are shown to worshippers.
- For each section, bookmarks, most recently read hymn, and previous/next controls use `collection:number` identities so overlapping numbers stay independent.
- Nine Àkọkún source comparisons (nos. 1, 37, 45, 47, 65, 101, 104, 130, and 303) are recorded in `data/akokun-source-matches.json`. Eight exact/near-exact English titles have been added to the index. This is **metadata matching**, not full lyric import: words/verse counts and rights still require validation.
- An existing Google Play app, `com.deltreetech.tachymns`, is available as an independent public reference for the Apostolic Church hymnbook. Do not copy its proprietary database without explicit rights.



## Audio playback and optional Learn Hymns (v0.6.0)

- Every readable hymn has a small **Recording** panel directly under the Yorùbá / English choice.
- Recording identity uses all four dimensions: `collection:number:language:voice`. Choose **Solo** or **Choir**, and the existing text-language choice determines the recording language. Overlapping Redemption and Àkọkún numbers never share recordings or bookmarks.
- Audio genuinely plays through Android `MediaPlayer`, with play/pause, seek, time display, and correct release on screen exit. No fake playback placeholders or text-to-speech presented as a singer.
- **Add recording** opens Android's system file picker for an audio file the user already has permission to use; persistable URI grants let the selection work offline after restarting. **Change recording** replaces the selected solo/choir/language version. No platform storage permission required.
- When rights-cleared MP3 audio becomes available from a permitted source, add a `rights_verified` manifest entry to `android/app/src/main/assets/audio_catalog.json` containing `collection`, `number`, `language` (`yo` or `en`), `voice` (`solo` or `choir`), `url` (HTTPS MP3), `sha256`, `credit` and `rights`. The **Download** action saves and verifies the MP3 into private app storage. Playback then works without internet. Never add a track solely because an external recording's title resembles the TAC hymn.
- **There are currently zero shipped recordings**: no licensed recordings have been supplied or approved yet. Audio playback is fully wired for legitimately imported user recordings and for future approved catalogue downloads. Do not describe all hymns as having audio coverage.
- **More → Learn hymns** is deliberately separate from ordinary reading. It provides slow/normal playback and repeat of a user-marked passage (mark the start and end while listening). It does not misrepresent absent verse timing data as synchronized highlighting.
- Bundled lyrics and indices remain offline and unaffected by audio availability. Android minSdk remains 26, and the app ID remains `com.night.tachymns`.

### Audio acceptance checks

1. Import a real MP3 through the device picker for Redemption 1, Yorùbá/Solo. Play, pause, seek, leave/return, and restart the app: the selected file should remain accessible and the player should stop on leaving.
2. Change to choir or English and confirm the imported solo track does not incorrectly appear in another slot. Repeat in Àkọkún with the same number once text is available.
3. Enter **More → Learn hymns** and practise a passage with start/end markers at 0.75× / 0.85× / 1×. Confirm repeat is confined to Learn Hymns and leaving the page stops playback.
4. Supply a rights-cleared MP3 with a correct SHA-256 entry; download, disconnect the network, reopen, and verify playback. Reject mismatched hashes and oversized/non-MP3 responses.
5. Perform on-device speaker/headphone playback and visual input testing in addition to the existing CI build, data tests and emulator screenshots before calling the feature production-complete.

## Recording sourcing and APK-bundled audio (October 19, 2026)

- **User-facing default remains one-tap offline hymn playback** whenever a real rights-cleared singing recording exists. A personal audio import is an optional fallback, not a request for the user to sing.
- The app now supports three genuine local playback sources: **APK-bundled MP3**, a user-imported audio file, and a previously downloaded approved MP3. They share the same simple reader player; Learn Hymns remains optional under More.
- Add approved starter-pack MP3s to `android/app/src/main/assets/audio/`, with entries in `bundled_audio_catalog.json` listing unique section/number/language/voice, `rights_verified: true`, owner/performer credit, permission basis, asset path and SHA-256. Test script rejects missing files or corrupted tracks. **The bundled manifest is intentionally empty until a recording can legally be shipped.**
- Recommended distribution: a modest APK starter pack of cleared high-use hymns, followed by optional downloadable audio packs. Prebundling *every* 1,105 hymn × two languages × solo/choir would entail as many as 4,420 recordings; at roughly 2 MB apiece this is about 8.8 GB, so do not inflate the initial APK. Only actual available and authorized recordings count.
- `data/audio-research-queue.json` has research entries for all **800 Redemption + 305 Àkọkún** identities. It includes known first lines, section-safe IDs, and discovery links. These are **search queries**, not evidence of recorded performances. Blank/unverified titles remain blank: do not invent titles or claimed recordings.
- `data/audio-source-candidates.json` tracks externally discovered recordings and publisher/channel research separately; candidates must pass sung-audio inspection, actual TAC stanza/tune checks, language classification, and recording/distribution-rights checks before adding to either manifest.
- YouTube links may be used for compliant online viewing, but **YouTube-sourced audio cannot be downloaded, stripped, or repackaged inside the APK through the YouTube API** without platform authorization. To support offline audio from YouTube performers, request an independent MP3 master and an explicit redistribution licence from the relevant rights holders rather than extracting the YouTube stream.
- A hymn's text/tune being old or public domain does **not** automatically put modern choir/solo recordings, translations, arrangements or performances into the public domain. A Commons CC0 tag from a third-party uploader likewise needs rights-origin confirmation.


## v0.7.0 — lightweight online listening and offline audio library

This release implements the requested **small-APK hybrid strategy**. Bundling thousands of recordings is **not** the default.

- **Normal hymn reader:** two vocal arrangement choices, solo/choir, in the chosen Yorùbá/English language. Actual approved recordings stream over HTTPS with native play/pause/seek. Where an approved file can be downloaded, **Save offline** makes a checksum-verified local copy. A saved version plays locally first. Playback from the internet requires an approved audio catalogue entry, not merely a matching YouTube title.
- **No licensed files yet:** if no approved recording is available, **Find on YouTube** opens an external YouTube search for that hymn instead of claiming the hymn has playable in-app audio. This is a discovery path, not downloading YouTube recordings.
- **Index search:** for index-only hymns whose verses have not been imported yet, the audio icon opens a labelled external search rather than an empty lyric page. The full original numbering stays intact for Redemption 1–800 and Àkọkún 1–305.
- **More → Offline audio:** lists legitimately downloaded or locally imported recordings with their exact section, number, language and arrangement; shows local storage usage; deletes only app-owned audio copies. Removing a locally imported file link never deletes the source file on the phone. Confirmation is required.
- **More → Learn hymns** is optional and separated from normal reading; offers passage repeat and slower playback.
- CI now assembles native Android audio instrumentation tests and runs them on the API 28 emulator using a synthesized audio tone. The test audio is not shipped with the app and is not passed off as sung hymns.

### Honest content status / release blocker

- **18** Redemption hymn texts are available as local working drafts (1–17 and 50), some still requiring verification against the physical printed edition.
- **800** Redemption numbers are reserved in the index, but only an incomplete subset has verified first-line titles.
- **305** Àkọkún numbers are reserved, with **299** first-line identities in the photographed index draft; **zero** verified full Àkọkún lyrics imported.
- **Zero** published singing recordings have approved distribution rights or are bundled/downloadable in-app. Research links and YouTube search results are **not** licensable media assets.
- As a result, **the software feature set can be built and tested, but it would be false to label the entire 1,105-hymn bilingual content library or singing catalogue complete**. Completing the published content requires a legitimately reusable text source and authorized sound recording masters, with verse/tune verification.

## v0.7.1 — Redemption text and index expansion (20 October 2026)

The Redemption edition contains **800 distinct hymn numbers**. In this release:
- **234/800** English numbered first lines have been compared against the Redemption Hymnal 2006 index on Hymnary (the first continuous 234), including seven songs with public-domain English text; these are **metadata matches, not yet 234 lyric texts**.
- **18** photo-backed TAC Yorùbá/English working lyric drafts: nos. 1–17 and 50, pending remaining edition/diacritic checks.
- **7** additional public-domain English-only alternate full texts with transparent source attribution: **178, 187, 188, 190, 195, 200, 207**. They are searchable and readable in English, but should not be misrepresented as original printed TAC stanzas; the Yorùbá translations have not been fabricated or sourced.
- Consequently **25/800 Redemption hymn entries now have at least some readable lyrics**, and 18 have bilingual working drafts. All other numbered records remain index-only, not falsely listed as completed reading pages.
- Authenticated recording rights remain independent of text rights. No YouTube material is bundled or converted without appropriate permissions.
- `tools/build_asset.py` keeps provenance-tagged public-domain English alternates separately in `data/english-public-domain.json`; source editions and translation status are checked by offline tests.
- A genuine full 800-hymn **Yorùbá + English** text release requires a legitimate digital/photographic source for the remaining TAC words, permission for copyrighted translations, and review against the printed edition. Other denominations' hymn numbers or translations cannot simply be substituted.

Sources: numbered index `https://hymnary.org/hymnal/RH2006?page=2`; historic text provenance in each `data/english-public-domain.json` record.


## v0.7.2 — complete English Redemption number/title index (20 October 2026)

- **All 800/800 numbered Redemption hymns now have indexed English first lines**, checked against the `Redemption Hymnal` RH2006 numbered index on Hymnary. They are indexed metadata, **NOT 800 readable hymn texts**. Hymnary itself warns its transcribed index is not yet proofread. Independent checking against the user's printed TAC bilingual edition is still necessary.
- The known first-line catalogue was expanded from 234 to 800 across the indexed sections 235–800 while keeping the independently numbered **Àkọkún 1–305** separate. Underlying files are `data/redemption-hymnal-catalogue.json` and `android/app/src/main/assets/redemption_index.json`; both must remain identical.
- **Readable lyrics remain 25/800**: 18 existing working bilingual drafts and 7 public-domain English-only alternate drafts. No additional Yorùbá translation was invented. Completing text requires genuine printed-edition texts plus the necessary permissions to publish translations and arrangements.
- `tools/redemption_import.py` and `data/approved-redemption.json` now provide an independent **bilingual rights-reviewed bulk-import path** when an approved text source is obtained. The importer rejects duplicate/out-of-range hymn numbers, incomplete language versions, malformed stanzas, decomposed Yorùbá Unicode, undocumented rights or missing printed-edition verification. Existing working drafts may be superseded only by an explicitly reviewed replacement; the compiler includes only approved entries. The manifest is deliberately empty for now.
- The church-hosted [TAC North America Cincinnati Redemption Hymnal page](https://tacona-cincy.org/tac-redemption-hymnal) links to a **20-page PDF printout of a directory listing** showing hundreds of numbered MP3 filenames (including 001–800 and alternate arrangements). This is a promising **audio source research lead**, NOT a set of authorized files. No source server or permission to redistribute those MP3 recordings is established. See `data/audio-source-candidates.json`. Obtain original hosts, audio rights, and actual performed-verses verification before offering audio downloads.
- The Android app remains minSdk 26, follows ABR, and contains no third-party recordings represented as bundled vocals.

### Proposed authorised content handoff

For hymn texts provided with express permission, prepare a JSON batch:
```json
{
  "schema_version": 1,
  "collection": "main",
  "hymns": [
    {
      "number": 350,
      "title_yo": "Exact photographed Yoruba first line",
      "title_en": "Exact photographed English first line",
      "verses_yo": [["Verified Yoruba line 1", "Verified Yoruba line 2"]],
      "verses_en": [["Verified English line 1", "Verified English line 2"]],
      "source_pages": [123],
      "reviewer": "Authorised editorial reviewer",
      "permission_reference": "Identifier for signed rights-grant or written permission",
      "rights_verified": true,
      "printed_edition_verified": true
    }
  ]
}
```
The above strings are **schema examples**, not real hymn texts. Run `python3 tac-hymnbook/tools/redemption_import.py batch.json --output tac-hymnbook/data/approved-redemption.json` and `python3 tac-hymnbook/tools/build_asset.py`; all validations are covered by CI. A metadata flag alone does not establish that rights actually exist; retain the signed permission in private project records.
