# Real Aniyomi extension compatibility probes

The emulator job installs the published AnimeSogo v16.8 APK from the maintained Mojuru extension index and exercises discovery, `Bleach` search, details, episodes, hosters, stream resolution, and normalization into Nami-owned models.

## v17 contract probe provenance

At pinned upstream source commit [25a6a2963164a600c802e15b1b4d06d2f4e27f3a](https://github.com/yuzono/anime-extensions/commit/25a6a2963164a600c802e15b1b4d06d2f4e27f3a), maintained AnimeSogo declares `libVersion = 16`; the checked Mojuru APK index contains no published v17 extension package. The v17 APK in CI is therefore **source-built from the real maintained AnimeSogo implementation**. CI changes only its package, display label, and extension metadata version, then compiles the source against the AAR built from Nami's `aniyomi-compat` module. The v16 extension library is removed from the source build's compile bundle so the build cannot silently resolve the old `Video` ABI.

The compatibility API includes the v17 `Video.memo` constructor and copy ABI, alongside the legacy v16 signatures. The built APK is installed and exercised against live AnimeSogo search, details, episode, and stream endpoints. This proves the maintained source compiles and runs against Nami's v17 contract; it is not represented as an upstream-published v17 release. The synthetic v17 fixture remains a separate deterministic ABI regression test and is not counted as real ecosystem proof.

Smoke diagnostics record source/package, counts, selected anime/episode, hoster and quality labels, header names, and track counts. Resolved URLs and header values are not logged.
