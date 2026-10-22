# Annie

**Browser source attribution:** Annie Browser incorporates source from [Omni Browser by RebelRoot](https://github.com/REBEL-ROOT/omni-browser) (Copyright © 2026 RebelRoot Ltd, GPLv3). See [the source manifest and retained license](annie-android/third_party/omni-browser/README.md) for the exact imported files and current integration limitations. Annie is not affiliated with or endorsed by RebelRoot.

The native Kotlin Android application is in `annie-android/`. Its current feature and test status is recorded in [the native roadmap audit](annie-android/docs/ROADMAP_AUDIT_2026-10-12.md). The section below describes the separate web prototype.

Annie is a command-first media chat web prototype. Commands work without requiring AI.

## Current scope

- **Anime and manga:** AniList catalog search with image cards, status, year, and catalog episode/chapter counts. AniList is metadata only; it does not provide playback, chapter pages, or downloads.
- **Music:** YouTube Data API search and playback in YouTube's official embedded player.
- **Movies, episodes, manga reading, downloads, and watch/read progress:** UI entry points are present, but these need authorized content providers and local persistence before they can work end to end.

The excluded source adapters are not loaded by the app.

## Build and test

- `npm test` runs extension and edge-case tests.
- `npm run check` checks source syntax.
- `npm run build` packages the static app into `dist/`.

GitHub Actions runs all three steps and uploads the `annie-media-web` artifact.

## YouTube setup

Add a YouTube Data API key in `/extensions`. The key is stored for the current browser tab. Browser keys are visible to users, so restrict the key to the app origin and the YouTube Data API. A public production deployment should proxy API requests through a server.

The YouTube extension only searches embeddable videos and plays them in YouTube's official player. It does not extract audio, download videos, or cache search results.
