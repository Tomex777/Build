# Shared reader Android upstream

This module is brand-neutral reader technology intended to be consumed independently by Yomi and Torri.

The initial interaction/view primitives are adapted directly from Mihon commit:

`7a917968e3bf71c4a665e6655a550877d81ead1d`

Original paths:

- `ui/reader/viewer/GestureDetectorWithLongTap.kt`
- `ui/reader/viewer/pager/Pager.kt`
- `ui/reader/viewer/webtoon/WebtoonFrame.kt`
- `ui/reader/viewer/webtoon/WebtoonLayoutManager.kt`
- `ui/reader/viewer/webtoon/WebtoonRecyclerView.kt`
- `ui/reader/viewer/webtoon/WebtoonSubsamplingImageView.kt`

Mihon is distributed under the Apache License 2.0. Package names were changed only where they do not need AndroidX package-private access. `WebtoonLayoutManager` intentionally remains in `androidx.recyclerview.widget`, matching upstream, because it uses package-protected RecyclerView layout APIs.

This is an incremental extraction. Pager/webtoon holders, page-image decoding, settings, transitions and host callbacks remain to be moved after this low-coupling slice is independently green.
