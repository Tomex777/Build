# Aether Android

Aether is a native Android meme browser built from the original React/Express prototype, but redesigned around the phone experience rather than copied literally.

## Product rules locked in

- Native Kotlin + Jetpack Compose, compile/target SDK 36, min SDK 26.
- OLED-black continuous media feed. Untapped posts show media only: no title, subreddit, points, comments, or media-type badge.
- Tap media to reveal metadata and actions: Save, Download, Comments, AI, Share/Play, Source.
- Original gesture contract is preserved: swipe right saves; swipe left dismisses and adds the post to seen history.
- Persistent seen IDs are filtered before new feed items are shown, reducing repeats across sessions.
- Images + GIFs are the normal/default feed. Images are deliberately blended more heavily. Videos are off by default and enter the same feed only when enabled in Settings.
- Category pills are user-owned feed groups. Tap selects. Long-press edits. `+` creates a new group.
- Category groups can contain subreddit sources and optional keyword/tag filters.
- Manual subreddit additions are validated against Reddit before being accepted.
- `Find with AI  uses a tool chain: AI intent -> Reddit discovery -> Reddit existence validation -> recent-post sampling -> media-fit/relevance ranking. The model never gets authority to add an invented subreddit directly.
- Reddit comments open as a bottom sheet. There is no Load More button; the UI reveals the next loaded chunk as the user approaches the bottom.
- Reddit browsing/comments/downloads are independent of the AI server.

## Layout

- `aether/` - Android application.
- `aether-server/` - optional AI/discovery service. Node 24+; Groq key pool with DeepSeek fallback.
- `.github/workflows/aether-android.yml` - GitHub Actions build, server syntax check, emulator launch and screenshot artifact.

## AI server env

Copy `aether-server/.env.example` and set one or both providers. The app's Settings sheet accepts the deployed server base URL. No AI provider secret is compiled into the APK.
