# Shared reader Android upstream

This module contains the brand-neutral Android reader technology used by Yomi.

The interaction/view primitives were adapted from Mihon commit:

`7a917968e3bf71c4a665e6655a550877d81ead1d`

Relevant upstream areas include pager/webtoon viewers, `ReaderPageImageView`, navigation behavior, page loading/cancellation, scale, crop, orientation, and reading-mode behavior.

The Yomi adaptation now includes the shared pager and webtoon viewers, image decoding, host callbacks, continuous chapter windows, display controls, and standalone local-file/folder integration. Package names and host boundaries were changed where needed to remove product-specific coupling.

Mihon is distributed under the Apache License 2.0. Yomi bundles the license and third-party notice in `app/src/main/assets/licenses/`. `WebtoonLayoutManager` intentionally remains in `androidx.recyclerview.widget`, matching upstream, because it uses package-protected RecyclerView layout APIs.
