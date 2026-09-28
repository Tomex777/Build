# Yomi reader extraction audit

Yomi is a separate local-first application. This audit records the reusable reader seam taken from the pinned Mihon v0.19.9 reader used by Torri, without importing Torri product identity or Torri's source/library model into Yomi.

## Pinned upstream reader baseline

Commit: `7a917968e3bf71c4a665e6655a550877d81ead1d`.

The mature implementation to preserve includes:

- `ui/reader/viewer/pager/*`
- `ui/reader/viewer/webtoon/*`
- `ui/reader/viewer/ReaderPageImageView`
- `ui/reader/viewer/ViewerNavigation`
- reader scale, crop, orientation and reading-mode settings
- page loading/cancellation behavior

## Coupling found before extraction

Three model-level couplings block a clean standalone host:

1. `ReaderPage` inherits `eu.kanade.tachiyomi.source.model.Page`.
2. `ReaderChapter` owns a Tachiyomi database `Chapter`.
3. pager/webtoon code reaches through `ReaderChapter.chapter` for identifiers/order/gaps.

At the activity/view-model layer, `ReaderViewModel` and `ReaderActivity` additionally coordinate source lookup, downloads, history, tracking, DB state, navigation and Torri/Mihon host actions.

## New neutral boundary

The `reader-core-contract` module introduces brand-neutral:

- `ReaderBook`
- `ReaderChapter`
- `ReaderPage`
- `ReaderPageSource`
- `ReaderProgressSink`
- `ReaderSessionHost`
- `ReaderHostActions`
- `ReaderSettings` / reading modes / scale modes

These contracts contain no Torri, Mihon, source-manager, tracker, remote-site or manga-database type.

## Extraction order

1. Keep Torri's current reader behavior green.
2. Adapt reader models to neutral IDs/data while preserving viewer behavior.
3. Make pager/webtoon depend on a small reader host surface instead of `ReaderActivity`/download manager access.
4. Add a Torri adapter that maps existing chapter/download/history data into the neutral contract.
5. Add a Yomi adapter backed only by SAF documents/trees, archives, image folders and Yomi progress storage.
6. Move the mature viewer implementation into a reusable Android module only after both adapters pass runtime smoke tests.

No giant extraction commit is planned.
