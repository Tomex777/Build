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

## Production signing

Normal Yomi CI builds a non-debuggable, production-like `candidate` APK for API 26/API 36 runtime validation. It is deliberately not presented as a production release because GitHub-hosted runner debug certificates are not stable between runs.

Permanent public releases use `.github/workflows/yomi-production-release.yml`. Configure these repository Actions secrets with one long-lived Android signing key:

- `YOMI_RELEASE_KEYSTORE_BASE64`
- `YOMI_RELEASE_STORE_PASSWORD`
- `YOMI_RELEASE_KEY_ALIAS`
- `YOMI_RELEASE_KEY_PASSWORD`

The production workflow refuses to build the `release` variant without those credentials, verifies the APK signatures, records the SHA-256 certificate fingerprint and package metadata, emits checksums, and can publish permanent GitHub Release assets for portfolio downloads.
