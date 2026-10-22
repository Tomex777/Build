# Juice WRLD — NIGHT's unofficial fan app (Android)

A fan-first space for Juice WRLD's released music, organized by **how the songs feel**, not by streaming-app tabs. Designed for listening, memories and personal favorites.

**Single main home:** an editorial 999 hero, feeling collections (**All / Love / Sad / Heartbreak / Hype / Chill**), song search, released song rows, and a **Favorites** filter. There are no Discover/Saved tabs or fake social metrics.

## Listening works (with files you own or can use)

Use **Find songs on my phone** to select a folder, automatically matching released songs by their actual filenames (including up to 3 nested folders) with Android's persistent document-tree permission. Or tap a song, choose **Add audio file**, and select an audio recording from your phone's Android file picker. The app retains the selected URI using Android's official persistent-grant mechanism, then plays the file *inside the app* with play, pause, seek, previous and next. The mini-player only appears for a real selected recording. Favorite hearts are persistent and the Favorites category is an in-page filter. Every song also has a private **Your Memory** note saved on-device plus a native share action, giving the fan collection meaning beyond a list.

Without a local file, the song sheet opens an **official YouTube Music search** for that song. This does not pretend to play the full song or offer illegally mirrored audio. No Juice WRLD recordings, streaming URLs, or copyrighted lyrics are bundled.

## Data

- 58+ known released songs, from Goodbye & Good Riddance, Death Race for Love, Legends Never Die, Fighting Demons, and other official releases. A subjective fan-curated list: moods overlap and are not official artist genres.
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
