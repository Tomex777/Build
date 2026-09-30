# Veya architecture

## Intake

`MainActivity` accepts normal launches and Android `ACTION_SEND` text shares. Shared HTTPS links are sent directly to the Home screen.

## Resolution

`MediaResolver` has two safe resolution paths:

1. Direct HTTPS audio/video files.
2. Media explicitly exposed by ordinary webpage metadata: OpenGraph video/audio, `twitter:player:stream`, and HTML5 `<video>`, `<audio>`, or `<source>` tags.

The resolver caps webpage reads at 2 MiB and probes at most 10 media candidates. It does not implement DRM bypass, encrypted-player signature deciphering, hidden playback APIs, or other service-specific circumvention.

## Downloading

`VeyaDownloadManager` creates one unique WorkManager job per download. `DownloadWorker`:

- uses HTTP `Range` to resume a private partial file;
- runs as a foreground data-sync worker for long downloads;
- publishes completed files through scoped MediaStore on Android 10+;
- uses an app-specific FileProvider-backed downloads directory on API 26-28;
- supports pause, resume, retry, cancel, and bounded automatic retry.

## Persistence

`DownloadStore` keeps a small JSON queue in app-private storage and exposes it as a `StateFlow`.

## UI

The Compose UI has Home, Downloads, and Settings surfaces. Veya uses its own violet/teal design system, supports light/dark mode, and requests only network/foreground-download permissions.
