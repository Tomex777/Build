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
