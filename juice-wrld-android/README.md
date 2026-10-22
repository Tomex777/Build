# Juice WRLD — NIGHT's unofficial fan app (Android v0.2.0)

A fan-first space for Juice WRLD's released music, organized by **how the songs feel**, not by streaming-app tabs. Designed for listening, memories and personal favorites.

**Single main home:** an editorial 999 hero, feeling collections (**All / Love / Sad / Heartbreak / Hype / Chill**), song search, released song rows, and a **Favorites** filter. There are no Discover/Saved tabs or fake social metrics.

## Listening works (with files you own or can use)

Use **Find songs on my phone** to select a folder, automatically matching released songs by their actual filenames (including up to 3 nested folders) with Android's persistent document-tree permission. Or tap a song, choose **Add audio file**, and select an audio recording from your phone's Android file picker. The app retains the selected URI using Android's official persistent-grant mechanism, then plays the file *inside the app* with play, pause, seek, previous and next. The mini-player only appears for a real selected recording. Favorite hearts are persistent and the Favorites category is an in-page filter. Every song also has a private **Your Memory** note saved on-device plus a native share action, giving the fan collection meaning beyond a list.

Without a local file, the song sheet opens an **official YouTube Music search** for that song. This does not pretend to play the full song or offer illegally mirrored audio. No Juice WRLD recordings, streaming URLs, or copyrighted lyrics are bundled.

## Data

- 110 known released songs, from Goodbye & Good Riddance, Death Race for Love, Legends Never Die, Fighting Demons, and other official releases. A subjective fan-curated list: moods overlap and are not official artist genres.
- Album art fetched at runtime from Cover Art Archive for known documented release IDs, with original gradient 999 fallback. Art is not redistributed in the APK.
- Fast offline browsing/filtering even without permission or a connection.
- No risky scraping or heavy backend dependency.
- Extensible for future official licensed media and fan-community functionality.

## Engineering

- Kotlin + Jetpack Compose, Android minSdk **26**, package **com.nice.juicewrld**.
- Build with Java 17 and Gradle 9.3.1:

```sh
cd juice-wrld-android
gradle testDebugUnitTest assembleDebug
```

- GitHub Actions runs JVM tests, APK install, emulator screenshots and Compose UI smoke tests for API 26 and 35.
- **ABR acceptance** means an actual build, installation, visual inspection, functional gestures, sound playback with a user-provided test clip, reliable offline/favorite persistence and zero ANRs. Compilation alone does not clear this gate.

## Sources and rights

Music credit and song/album names cross-checked against MusicBrainz and official releases:
- https://musicbrainz.org/artist/4e4ebde4-0c56-4dec-844b-6c73adcdd92d
- https://musicbrainz.org/doc/MusicBrainz_API
- https://coverartarchive.org/
- https://music.youtube.com/

This is an **unofficial** fan project, not endorsed by Juice WRLD's estate or his music labels. Users are responsible for lawful use of imported recordings.

## v0.2.0 progress / release gate

- 110 confirmed released song entries and fan-curated emotion groups.
- Responsive compact home plus verified Commons portrait URL and release-specific art fallbacks.
- In-app local playback supports emotion-based queues, shuffle and repeat one/all, with playback progression to the next song.
- Favorites and private song memories persist; scan your own music folder through SAF (manual song import also available).
- Do not consider the fan app production-ready until the latest branch is compiled and tested on API 26/35 emulators, the screenshots are visually inspected, imported audio is actually played, and owner-controlled release signing is configured.
- GitHub Actions debug APKs use runner debug certificates; they are test builds and are **not guaranteed to update** an APK built on a different runner without uninstalling. Never mistake these artifacts for a signed production release.

## 2026-10-22 ABR implementation / release gate

The fan-first mood card layout has been visually inspected on real API 26/35 startup
screenshots. The previous 110-song and 999 text duplication in the hero, footer
and section has been removed. Song search accepts apostrophe/ampersand variants
and word prefixes; Import is separate from playback. The separate companion
audio and search patches were reconciled into the same feature branch, including
WAV playback smoke, favorites persistence, safer filename matching, audio focus,
headphone-unplug handling and URI replacement.

**Do not claim release-complete from APK compilation alone.** Remaining acceptance
evidence must come from an *uncancelled* full Android workflow run, on **both**
API 26 and API 35, with verified installation, all instrumentation tests green,
five genuine full-screen PNGs from each emulator, visual inspection of
every page, and no relevant ANRs/crashes in logcat. Also verify the Android
document picker's real persistable URI grant and audio playback on a phone
before shipping a signed production APK. Current GitHub Actions debug APKs
are test builds and are not guaranteed to update each other in place.

## Download-first listening direction (October 2026)

The main fan interface intentionally **does not show Import**. A song offers a genuine
**Download** button *only* when an authorized, individually licensed HTTPS audio
file and its expected SHA-256 fingerprint exist in `assets/licensed_audio.json`.
Until recordings are licensed, the primary song action opens the official YouTube
Music search. This is **not** an in-app download or stream. No copyrighted Juice
WRLD recordings are scraped, bundled or fetched without permission. Previously
attached local media remain playable for migration; the import controls are no
longer presented. Downloads are stored privately and integrity-checked before
being shown as available. App now exposes a full Now Playing screen for tracks
with authorized local audio.
