# Juice WRLD — ABR and fan-app design agreement

- This is a **Juice WRLD fan app**, not a generic music discovery service or discography browser.
- **No Discover/Saved tabs.** Fan home centers on songs and their emotions. Keep All, Love, Sad, Heartbreak, Hype, Chill; Favorites is a filter, not a second app section.
- Never create fake playable songs, download links, lyrics or unlicensed full-track streams.
- Real in-app playback uses approved local audio via Android SAF and MediaPlayer, with honest empty states. Keep audio focus and durable file permissions; do not simulate playback.
- New fan songs need confirmed released recording credits and plausible manually curated mood tags.
- Do not return redundant controls, default Android text fields, filler copy or generic app-template style.
- minSdk 26, namespace/applicationId `com.nice.juicewrld`; retain proper vectors.
- The original screenshots showed an Android System UI ANR; verify repeatable device launch and capture clean screenshots on API 26 and modern Android.
- All new work stays within `juice-wrld-android/` and its workflow.
- Never call an Android product finished only because CI compiles; inspect device UI and interaction before release.
