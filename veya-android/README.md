# Veya

Veya is a native Android video app for searching, watching, saving, and resuming YouTube videos without duplicating YouTube extraction logic inside the app.

## Product surfaces

- Home with Continue watching and Watch again.
- Search with continuation paging from the shared YouTube Engine.
- Video details with title, channel, duration, description, chapters, playback, and download actions.
- Library backed by persisted watch history and resume position.
- Downloads with pause, resume, retry, delete, durable restart recovery, and offline playback.
- Settings for default playback/download quality.
- About with app/version information only.

## Playback

Veya uses the repository's shared YouTube Engine for resolution and refresh, then hands verified adaptive video/audio tracks to the native Android player layer. The player supports:

- adaptive video plus audio;
- quality switching;
- seeking with a custom VLC-style seek bar;
- fullscreen/orientation changes;
- refreshable WebVTT captions;
- chapter navigation when available;
- resume position persistence;
- local offline video/audio playback after process restart.

The engine remains a separate reusable module. Veya does not carry a second extractor implementation.

## Android baseline

- compileSdk 36
- targetSdk 36
- minSdk 26
- JDK 17

Canonical branch: `veya-android-ci` in `Tomex777/Build`.

## CI acceptance

GitHub Actions is authoritative. The Veya workflow:

- checks out the pinned shared-engine revision and records it;
- runs unit tests, lint, debug build, minified release APK build, and release bundle build;
- verifies package identity, min/target SDK, signatures, and required libVLC JNI classes;
- produces universal and ARM64 release candidates;
- installs the release candidate on API 26 and API 36;
- captures Home, Library, Downloads, Settings, About, live player, captions, completed download, restored download, and offline-player screenshots;
- on API 36, proves real live media playback with rendered frames + audio, caption selection while playback advances, a completed adaptive download, process restart, and offline playback.

The pinned shared-engine CI independently proves live search + continuation, details, chapters, real CDN bytes, expiry refresh, resume from checkpoints, and Android seek/playback acceptance.

## Release signing

CI always produces QA-signed installable release candidates for runtime acceptance. Production-signed APK/AAB outputs are produced only when the repository has the Veya release signing secrets configured:

- `VEYA_RELEASE_KEYSTORE_BASE64`
- `VEYA_RELEASE_STORE_PASSWORD`
- `VEYA_RELEASE_KEY_ALIAS`
- `VEYA_RELEASE_KEY_PASSWORD`

The private signing key must not be committed to this public repository.
