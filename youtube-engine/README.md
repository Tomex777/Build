# Shared native YouTube engine — foundation

Independent Kotlin/Android modules: API, core, and a small transport probe app. Baseline: compile/target 36, min 26. No Veya, Annie, or Lyra integration here.

The probe app searches, resolves a video, reads actual bytes from one video and one audio representation where available, and refreshes a format by stable itag. A format URL alone is **not** transport proof. Failure and challenge cases must be reported explicitly.

## Current limitations

- `SessionProvider` supplies request headers scoped to each URL and receives response `Set-Cookie` values so the host can persist them in its own origin-aware cookie store. The engine installs no process-wide cookie handler. Innertube retries rotate through the configured client strategies. HTTP 400/403 invalidates the bootstrap and forces a fresh web key/client version before the next strategy attempt. Search uses bounded client fallback for empty renderer responses; details can fall back to the watch-page player response when Innertube is challenged. Direct URL formats only. Ciphered signatures, n-sig transformations, PoToken, SABR, account sessions, and complex challenges are not yet implemented. These formats are omitted rather than claimed playable.
- Search continuation, subtitle retrieval, and timestamp chapters are covered by live CI checks. Channel pages, playlist paging, comments/replies and broader search renderer variants still need implementation and tests.
- The API can refresh an expired signed URL by stable itag/container/codec identity and retry the same byte range. CI now forces the local expiry guard and proves refreshed CDN bytes; expiry after hours and full long-running download persistence are still not proven.
- Chunk reads return the actual bytes and a transfer checkpoint for the host app to persist. The engine does not own a download database or claim crash/restart persistence until a host persists and resumes that checkpoint.
- No third-party extractor code has been copied. Only original code is in these modules; no external extractor license obligations have been introduced.

CI runs network instrumentation tests on an API 36 emulator. They must search and page results, load video details, fetch a live caption, extract ordered timestamp chapters from a chaptered video, resolve a compatible 1080p+ adaptive video/audio pair, read at least 512 non-HTML bytes from both CDN URLs, resume a nonzero byte range, refresh an expired descriptor by stable identity, and read refreshed bytes at the same offset. The tests fail if any stage fails. A separate deterministic test verifies `SUPPORTED_AND_PROVEN`, `CHALLENGED`, `CIPHERED`, `SABR_ONLY`, `EXPIRED`, `RATE_LIMITED`, and `UNSUPPORTED` classification. The live transport run on 2026-09-28 read 2160p VP9 and Opus bytes on an API 36 emulator. GitHub Actions also builds an ARM64 probe APK for Galaxy A16.

`resolve()` returns unverified descriptors. `resolveVerified()` performs both CDN byte probes and is the only operation that returns `SUPPORTED_AND_PROVEN`. No client response with only SABR metadata or ciphered formats is reported as playable. Alternate client identities are bounded to four attempts; the current web key/version is rediscovered after 15 minutes or an HTTP 400/403.
