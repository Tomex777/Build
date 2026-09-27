# Shared native YouTube engine — foundation

Independent Kotlin/Android modules: API, core, and a small transport probe app. Baseline: compile/target 36, min 26. No Veya, Annie, or Lyra integration here.

The probe app searches, resolves a video, reads actual bytes from one video and one audio representation where available, and refreshes a format by stable itag. A format URL alone is **not** transport proof. Failure and challenge cases must be reported explicitly.

## Current limitations

- Innertube client versions need live validation and rotation. Direct URL formats only. Ciphered signatures, n-sig transformations, PoToken, SABR, account sessions, and complex challenges are not yet implemented. These formats are omitted rather than claimed playable.
- Search and video details are initial implementations. Channels, playlists, comments, chapters and continuation coverage need further work.
- The probe's refresh requests a new descriptor by itag; expiry over hours and download resume are not yet proven.
- No third-party extractor code has been copied. Only original code is in these modules; no external extractor license obligations have been introduced.

Run `gradle -p youtube-engine :youtube-engine-testapp:assembleDebug` in Android CI. Use the ARM64 artifact on Galaxy A16 to collect real transport results.
