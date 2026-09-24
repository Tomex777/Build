# Annie

Annie is a command-first media chat app. The extension host routes deterministic commands without requiring AI.

## Build and test

- `npm test` runs extension and edge-case tests.
- `npm run check` checks source syntax.
- `npm run build` packages the static app into `dist/`.

GitHub Actions runs all three steps and uploads the `annie-media-web` artifact.

## Independent provider extensions

| Extension | Media | Current state |
|---|---|---|
| Weeb Central | Manga | Isolated slot; disabled pending an authorized API or content license |
| TFPDL | Movies and TV | Isolated slot; disabled pending an authorized API or content license |
| SubsPlease | Anime | Isolated slot; disabled pending an authorized API or content license |
| YouTube | Music | Official Data API search and embedded player |

Each adapter declares its own ID and media types. Routing tries the configured default first, then enabled fallbacks by priority. Empty results, malformed payloads, exceptions, timeouts, cancellation, and duplicate results are handled independently.

The YouTube extension only searches embeddable videos and plays them in the official YouTube player. It does not extract audio, download videos, or cache search results. Add a YouTube Data API key in `/extensions`; browser keys are visible to users, so restrict the key to this app's origin and the YouTube Data API. A public production deployment should proxy API requests through a server.

The three catalog slots are intentionally disabled until an authorized content API or license is available. The app reports that state rather than silently substituting sources.
