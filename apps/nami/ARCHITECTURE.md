# Nami architecture and integration boundaries

This document records the initial architecture decision. The Nami skeleton is a new application. The pinned Aniyomi checkout is a source and behavior reference, not Nami's parent project.

## Runtime dependency direction

```text
app (Compose UI and Android lifecycle)
  ├── core:domain (Nami-owned models and use cases)
  ├── core:source-api (NamiAnimeSource contract)
  ├── core:source-runtime (registry and fan-out search)
  ├── data:local (Nami-owned SQLite persistence)
  ├── extensions:aniyomi-compat (isolated legacy adapter boundary)
  └── player:mpv (planned isolated player module)
```

The Nami source contract returns Nami domain values. UI, database, and player code must not depend on `SAnime`, `SEpisode`, `Video`, or other Aniyomi implementation types.

## Source contract and global search

`NamiAnimeSource` exposes metadata, paged search, details, episodes, and media resolution. `AnimeRef` and `EpisodeRef` carry a source ID plus an opaque source-owned key; extensions do not choose Nami database IDs. `ResolvedMedia` preserves the URL, MIME/quality, request headers, subtitle/audio tracks, expiry time, and an opaque refresh token for later re-resolution work.

`GlobalAnimeSearch` fans out to each registered source concurrently under a supervisor scope. Each result is keyed by source ID; failures are recorded per source and do not cancel other searches. The source registry must enforce unique stable IDs before exposing a source list.

Native Nami extensions implement `NamiAnimeSource` directly. Aniyomi sources are adapted at the compatibility boundary and produce the same Nami models.

## Aniyomi extension compatibility boundary

The chosen compatibility target is the pinned app's extensions-lib v17 contract. Keep legacy package names where extension bytecode requires them, inside a dedicated compatibility module. Do not expose these classes from `core:domain` or the main app API.

The minimum API surface to validate against the pinned checkout is:

- Factory/discovery: `AnimeSourceFactory` and extension manifest metadata needed to instantiate a source.
- Source contracts: `AnimeSource`, `AnimeCatalogueSource`, `AnimeHttpSource`, and `ConfigurableAnimeSource`.
- Catalog models: `SAnime`, `SEpisode`, `AnimesPage`, anime filters/filter lists, season/episode update results, and relations.
- Playback models: `Hoster`, `Video`, `Track`, `ThumbnailInfo`, and `HttpServer` where used by the selected extension.
- Host runtime classes imported by extension bytecode, especially the `eu.kanade.tachiyomi.network` APIs (`NetworkHelper`, request builders, response helpers, and relevant interceptors).

This list is the initial compatibility test surface, not a claim that every class is already ported. Before loading an APK, inventory the actual selected extension's referenced classes and methods, compare them with the exact v17 stubs and pinned source API, and add only the needed surface. Preserve binary/package compatibility and license notices for any vendored implementation. The adapter translates calls/results and maps request metadata; it must not let legacy models leak into Nami storage or UI.

An extension APK is a separately installed Android package. The runtime needs to discover installed packages/metadata, verify the declared factory, load extension classes through a controlled classloader, inject a Nami-backed network/runtime context, and isolate failures. APK download/installation and trust UX should be separate from the source adapter itself.

## UI reuse boundary

Reuse/adapt the actual anime-side Aniyomi Compose presentation components where the user-visible structure and interactions are being preserved. Candidate component areas are:

- Library content, grid/list/compact layouts, cards, category controls, toolbar, selection actions, and empty states.
- Anime source/extension browsing and global search result presentation.
- Anime details header/content, episode rows, sorting/filter controls, and episode actions.
- More content and relevant settings rows/sheets.

Do not import Aniyomi screen models, DI graph, database repositories, navigation root, preference stores, or manga screens just to reuse presentation. Create Nami view models/use cases that map Nami domain values to small UI-facing state models. Copy only focused UI source after checking each file's imports and transitive dependencies; retain original copyright headers and Apache-2.0 attribution. No Aniyomi UI implementation is copied into the initial skeleton.

## mpv boundary

Create a `player:mpv` Android module only when playback integration begins. Keep mpv initialization, native library packaging, lifecycle, surfaces, and player controls inside it. Its public input should be a Nami playback request built from `ResolvedMedia` (URL, headers, subtitles/audio, and playback identity), not `AnimeSource` or Aniyomi `Video`. Start by reusing the maintained `aniyomi-mpv-lib` integration at the pinned version as a focused player dependency; adapt only the player activity/view/control code needed. Do not pull in Aniyomi's app-wide player view model, preferences, navigation, storage, or source loader. Verify a real resolved stream on an emulator before claiming integration complete.

## Persistence boundary

`data:local` owns `nami.db` and the initial schema. Future repositories should map Nami models at the persistence edge and use explicit forward-only migrations. Source-owned IDs remain paired with source IDs. No Aniyomi database or storage path is shared.

## Phase order

1. Build and launch this skeleton in CI with minSdk 26.
2. Implement the extension package discovery/compat adapter against one pinned, real v17-compatible anime extension; verify listing, search, details, episodes, and stream resolution.
3. Implement one real native Nami source and run both through global search.
4. Adapt the Aniyomi anime UI components against Nami view models.
5. Integrate mpv behind the `ResolvedMedia` playback boundary.
6. Finish persistent Library workflows and test restart/category/watch-state behavior.
7. Only then audit and implement Nami downloads.
