# TAC Hymnbook — first photographed batch

## Source of truth

The user's physical Yorùbá TAC hymnbook; the first two photos cover *printed pages 1–4*. Each photo shows two facing pages. The local book numbering is **not** the number used by external hymn collections.

## What is in this commit

`data/hymns-001-011.seed.json` indexes local hymns **1–11**. Each has its original TAC number, a title/opening line, photographed page references, a candidate English equivalent, a possible online source, and a review state.

External source presence means only **a candidate hymn was found**, not that we have verified every line or imported full lyrics. The photo of hymn 11 ends partway through the song; additional pages are required for a photo-exact edition.

## Matching policy

1. Match by Yorùbá opening line and verse structure, not external hymnal number.
2. Keep original TAC number and collection, including appendix identifiers, unchanged.
3. Prefer words from photographed pages where legible. Consult online hymn sources for unclear words and variations, without silently replacing the TAC edition.
4. If no exact version can be found, an alternate version is acceptable at the user's request, but mark it **alternate** in data and UI.
5. Never claim a complete-lyrics match when only the opening line matches.
6. Preserve Yorùbá diacritics; support diacritic-insensitive search.
7. Track source and reproduction rights before importing or distributing text from third-party sites.

## Next tasks

- Resolve every line for hymns 1–11, verify against photos and online sources.
- Add hymn 11's continuation from page 5 or mark it incomplete if an alternate is used.
- Create Android minSdk 26 offline hymn reader following ABR (custom controls, minimal UI, no redundancy).
- Process subsequent photo spreads as user supplies them.

## Validation

A seed is valid when hymn numbers are unique and contiguous, all have visible source pages, and none are marked as fully verified without lyrics.
