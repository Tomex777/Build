# Yomi

Yomi is a standalone, local-first Android reader.

It is intentionally separate from Torri and does not provide online manga/source browsing. Yomi owns local files, image folders, library identity, reading progress, bookmarks, and the reader UI.

Supported local books:

- CBZ and ZIP archives
- image folders
- book folders containing image chapter folders
- book folders containing chapter archives

Reader features include:

- left-to-right, right-to-left, vertical paged, and Webtoon modes
- continuous cross-chapter reading
- exact chapter/page restore across relaunches
- crop borders, page-number display, volume-key navigation, scale modes, orientation, and reader backgrounds
- persistent bookmarks
- library search, sorting, cover thumbnails, relinking, and removal without deleting source files

Android baseline:

- compileSdk 36
- targetSdk 36
- minSdk 26

The production build emits a universal APK, an arm64-v8a APK, and an Android App Bundle. Third-party reader attribution and the Apache 2.0 license are bundled under `app/src/main/assets/licenses/`.
