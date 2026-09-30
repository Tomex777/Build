# Veya architecture

Veya is a native Android video client built around the repository's reusable YouTube Engine. The app owns Android UI, playback presentation, watch history, downloads, and offline media. The engine owns YouTube discovery, metadata, format resolution, transport verification, URL refresh, subtitles, chapters, and resumable transport primitives.

## App shell

`MainActivity` hosts the Compose application and accepts normal launches plus supported YouTube deep links/shares. The primary destinations are Home, Search, Library, Downloads, and Settings. Video details and About are focused secondary surfaces.

## Shared YouTube Engine boundary

Veya links the pinned `youtube-engine-api` and `youtube-engine-core` projects from the separately checked-out shared-engine revision.

Veya does not contain a second YouTube extractor. The app requests search pages, video details, verified adaptive formats, refreshed media, captions, chapters, and transport proofs through the shared engine boundary.

The pinned engine revision is recorded by CI so every Veya APK can be tied to the exact resolver implementation that was accepted.

## Playback

`VeyaPlayerActivity` is the Android playback host. It:

- resolves or refreshes transport-ready adaptive video and audio;
- attaches libVLC to a real `VLCVideoLayout`;
- adds the adaptive audio representation as a media slave;
- exposes play/pause, a custom VLC-style seek bar, quality switching, captions, chapters, and fullscreen/orientation controls;
- records watch position/duration for resume and completion state;
- can reopen a completed local download after process restart.

The player never treats a URL string alone as playback proof. CI requires rendered frames, advancing playback position, and the adaptive audio attachment.

## Captions and chapters

Caption metadata comes from the shared engine. When a user selects a track, Veya asks the engine for refreshable WebVTT content, writes the bounded subtitle payload to app-private storage, and attaches it to the active player.

Chapter metadata also comes from the shared engine and is presented as seek targets in the player when available.

## Downloads

`VeyaDownloadManager` and its WorkManager worker persist download state and checkpoints. Adaptive video and audio are downloaded separately with the engine's stable format identities and refresh semantics.

Downloads support queueing, pause, resume, retry, delete, restart recovery, and offline playback. Completed files remain app-owned and are played through the same player presentation rather than a separate offline player.

## History

`WatchHistoryStore` persists title, thumbnail, position, duration, and completion state. Home derives Continue watching and Watch again from this store, while Library exposes the durable history list.

## Release and validation

The Android baseline is compile/target SDK 36 and minimum SDK 26.

The canonical Veya CI:

1. checks out the exact shared-engine revision;
2. runs unit tests, lint, APK and AAB release builds;
3. produces universal and ARM64 release APK candidates;
4. creates deterministic QA-signed APKs for emulator acceptance;
5. optionally production-signs APK/AAB artifacts when release credentials are configured;
6. installs the release APK on API 26 and API 36;
7. captures real UI/player screenshots;
8. on API 36, proves live rendered playback + audio, captions while playback advances, a completed adaptive download, process restart, and offline playback.

Production signing material is supplied only through repository secrets and is never committed to the public repository.
