# Shared native YouTube engine — foundation

Independent Kotlin/Android modules: API, core, and a small transport probe app. Baseline: compile/target 36, min 26. No Veya, Annie, or Lyra integration here.

The probe app searches, resolves a video, reads actual bytes from one video and one audio representation where available, and refreshes a format by stable itag. A format URL alone is **not** transport proof. Failure and challenge cases must be reported explicitly.

## Current limitations

- Innertube client versions need live validation and rotation. Direct URL formats only. Ciphered signatures, n-sig transformations, PoToken, SABR, account sessions, and complex challenges are not yet implemented. These formats are omitted rather than claimed playable.
- Search and video details are initial implementations. Channels, playlists, comments, chapters and continuation coverage need further work.
- The probe's refresh requests a new descriptor by itag; expiry over hours and download resume are not yet proven.
- No third-party extractor code has been copied. Only original code is in these modules; no external extractor license obligations have been introduced.

CI runs a network instrumentation test on an API 36 emulator. It must search, load details, resolve a compatible 1080p+ adaptive video/audio pair, read at least 512 non-HTML bytes from both CDN URLs, resume a nonzero byte range, refresh by a stable itag/container/codec identity, and read refreshed media bytes. The test fails if any stage fails. A separate deterministic test verifies `SUPPORTED_AND_PROVEN`, `CHALLENGED`, `CIPHERED`, `SABR_ONLY`, `EXPIRED`, and `UNSUPPORTED` classification. The successful live run on 2026-09-27 read 2160p VP9 and Opus bytes on an API 36 emulator. GitHub Actions also builds an ARM64 probe APK for Galaxy A16.

`resolve()` returns unverified descriptors. `resolveVerified()` performs both CDN byte probes and is the only operation that returns `SUPPORTED_AND_PROVEN`. No client response with only SABR metadata or ciphered formats is reported as playable. Alternate client identities are bounded to four attempts; the current web key/version is rediscovered after 15 minutes or an HTTP 400/403.
