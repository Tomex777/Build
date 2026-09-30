# Nami architecture and integration boundaries

This document describes Nami's current module boundaries. Nami is its own anime application; the
Aniyomi checkout is only an optional compatibility reference.

## Runtime dependency direction

```text
app (Compose UI and Android lifecycle)
  ├── core:domain (Nami-owned models and use cases)
  ├── core:source-api (NamiAnimeSource contract)
  ├── core:source-runtime (registry and fan-out search)
  ├── data:local (Nami-owned SQLite persistence)
  ├── extensions:kayoanime (first-party Nami API extension)
  ├── extensions:aniyomi-compat (optional legacy adapter boundary)
  └── app:NamiVlcPlayer (VLC/libVLC playback surface)
```

The Nami source contract returns Nami domain values. UI, database, and player code must not depend on `SAnime`, `SEpisode`, `Video`, or other Aniyomi implementation types.

## Source contract and global search

`NamiAnimeSource` exposes metadata, paged search, details, episodes, and media resolution. `AnimeRef` and `EpisodeRef` carry a source ID plus an opaque source-owned key; extensions do not choose Nami database IDs. `ResolvedMedia` preserves the URL, MIME/quality, request headers, subtitle/audio tracks, expiry time, and an opaque refresh token used when playback or downloads need re-resolution.

`GlobalAnimeSearch` fans out to each registered source concurrently under a supervisor scope. Each result is keyed by source ID; failures are recorded per source and do not cancel other searches. The source registry must enforce unique stable IDs before exposing a source list.

Native Nami extensions implement `NamiAnimeSource` directly. KayoAnime is the real-source
reference implementation and exercises search, details, episodes, structured stream headers,
downloads, and offline VLC playback. Optional Aniyomi sources are adapted at the compatibility
boundary and produce the same Nami models.

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

## VLC playback boundary

Nami playback is implemented with libVLC in the app layer. The player consumes a Nami playback request built from `ResolvedMedia` (URL, request headers, subtitles/audio tracks, quality metadata, expiry/refresh information, and playback identity); it does not consume legacy Aniyomi `Video` or source implementation types. Streaming and downloaded episodes use the same VLC playback surface and controls. Player lifecycle, SurfaceView attachment, fullscreen/orientation handling, seeking, subtitle/audio selection, speed, resume persistence, transient retry, and expired-URL re-resolution remain isolated behind this boundary. Visible VLC branding is not part of Nami's product UI; attribution stays in About/third-party notices.

## Persistence boundary

`data:local` owns `nami.db` and the initial schema. Future repositories should map Nami models at the persistence edge and use explicit forward-only migrations. Source-owned IDs remain paired with source IDs. No Aniyomi database or storage path is shared.

## Production acceptance

Release acceptance is based on Nami-owned behavior, not optional legacy compatibility. The main workflow builds debug and release artifacts, exercises Android 8 / API 26 persistence and storage behavior, exercises Android 16 / API 36 launch, process-restart and product UI flows, runs the real KayoAnime source end to end, verifies VLC playback, and captures visual evidence. Optional external compatibility jobs are allowed to remain outside the production gate.

Production release outputs are minified, persistently signed APK/AAB artifacts. The ARM64 APK is the primary device-install artifact; a universal APK and Play-ready AAB are produced alongside checksums.
