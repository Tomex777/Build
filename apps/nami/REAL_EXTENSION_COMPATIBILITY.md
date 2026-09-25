# Real Aniyomi extension compatibility probes

The emulator compatibility job downloads and installs the published AnimeSogo v16.8 APK from the maintained Yuzono extension repository index, then exercises discovery, `Bleach` search, details, episodes, hosters, and resolved media through Nami models.

## Real v17 probe provenance

The maintained source repository at pinned commit [25a6a2963164a600c802e15b1b4d06d2f4e27f3a](https://github.com/yuzono/anime-extensions/commit/25a6a2963164a600c802e15b1b4d06d2f4e27f3a) currently declares AnimeSogo with `libVersion = 16`; its published index has no v17 APK. The v17 emulator probe therefore builds that real maintained AnimeSogo source module against extensions-lib 17 in CI. The CI-only transformation is limited to:

- target `libVersion = 17`;
- rename the module/package to `animesogov17`, so it can be installed beside the published v16 APK;
- label the app as a v17 compatibility probe.

The extension implementation and live AnimeSogo endpoints are the maintained source; this artifact is a source-built compatibility APK, **not** an upstream-published v17 release. The synthetic v17 fixture remains a separate deterministic ABI regression test and is not counted as real extension proof.

The emulator test validates API 17 discovery, search, details, episodes, stable Nami episode identity, and a final HTTP stream. Logs include safe metadata (hoster names, quality, header names, track counts) and omit resolved URLs and header values.
