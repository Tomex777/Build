# Nami

Nami is an anime application with Nami-owned domain models, storage, source API, downloader, and
VLC/libVLC playback. Optional legacy extension compatibility is isolated and does not define the
Nami platform.

## Modules

- `:app` — Android/Compose shell; minSdk 26.
- `:core:domain` — Nami-owned anime, episode, and resolved-media models.
- `:core:source-api` — normalized source contract shared by native and adapted sources.
- `:core:source-runtime` — source registry boundary and source-isolated global search.
- `:data:local` — Nami-owned SQLite schema for library, categories, progress, and history.
- `:extensions:aniyomi-compat` — isolated, optional boundary for legacy Aniyomi extension compatibility.
- `:extensions:kayoanime` — first-party real source APK built directly against Nami's source API.
- `:test-fixtures:nami-native-extension-fixture` — first-party example APK and executable example of Nami's extension API.

See [NAMI_EXTENSION_API.md](NAMI_EXTENSION_API.md) for the manifest, API compatibility rules,
and extension authoring guide. External compatibility jobs are optional and are not part of Nami's
release acceptance.
