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
## Release acceptance

Production signing and publishing are allowed only for a commit that already has a successful **Nami Android** acceptance run. That acceptance covers the regular build, release-candidate packaging, API 26 smoke, API 36 launch/persistence/product UI proof, and the real KayoAnime download/VLC smoke; optional legacy compatibility probes remain outside the production gate.
