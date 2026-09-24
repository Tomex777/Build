# Nami

Nami is an anime application with Nami-owned domain models, storage, and source contracts. The pinned Aniyomi checkout at `nami-upstream-baseline-v0.18.2.1` is retained separately as a UI/UX and compatibility reference; it is not the Nami application base.

## Current skeleton

The app opens on an empty Library state and exposes Library, Browse, and More navigation. It does not ship fake anime, fake sources, or fake playback. Source search and real screens will be connected as their corresponding modules are implemented.

## Modules

- `:app` — Android/Compose shell; minSdk 26.
- `:core:domain` — Nami-owned anime, episode, and resolved-media models.
- `:core:source-api` — normalized source contract shared by native and adapted sources.
- `:core:source-runtime` — source registry boundary and source-isolated global search.
- `:data:local` — Nami-owned SQLite schema for library, categories, progress, and history.
- `:extensions:aniyomi-compat` — isolated boundary for the Aniyomi extension adapter. No Aniyomi classes are copied into Nami yet; compatibility proof is the next implementation step.

mpv integration is intentionally a later module so Nami's app and domain do not depend on the Aniyomi application architecture. No downloader is implemented in this skeleton.

## Aniyomi source reference

The compatibility target is Aniyomi v0.18.2.1, commit `97414446b8a95994c72dd33c41c971a89d4d25b8` (extensions-lib v17). The pinned checkout is maintained separately. See `THIRD_PARTY_NOTICES.md` and `licenses/ANIYOMI-APACHE-2.0.txt` before reusing source code.
