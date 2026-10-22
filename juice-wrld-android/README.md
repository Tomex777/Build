# Juice WRLD — Android fan discography (foundation)

An **unofficial** local-first Android discography companion centered on Juice WRLD. Native Kotlin + Compose, designed around the project's App Building Rules (ABR). This is a new, isolated native app in `Tomex777/Build/juice-wrld-android` — not a modification of the Slumber piano app, NIGHT Social, or NIGHT Play.

### Real, shipped behavior
- Retrieves genuine artist release metadata from MusicBrainz (artist ID `4e4ebde4-0c56-4dec-844b-6c73adcdd92d`).
- Browse/filter/search albums, EPs, singles; album detail track lists when an official release provides them.
- Artwork from Cover Art Archive with a distinct original vector fallback for missing covers.
- Favorites persist on-device and can be opened without an internet connection if the release catalogue was cached.
- Clear offline/error states and manual refresh; no fabricated user accounts, playback, tracks, streaming, downloads, lyrics or engagement.
- Official artist listening link opens Apple Music externally, where listening is licensed and controlled by that provider.

### Build

Java 17 and Gradle 9.3.1 (Android Gradle Plugin 9.1.1). `compileSdk = 37`, `targetSdk = 36`, **`minSdk = 26`**.

```bash
cd juice-wrld-android
gradle testDebugUnitTest assembleDebug
```

App ID: `com.nice.juicewrld`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Integrity and licensing
MusicBrainz metadata is CC0; its public API limits clients to **one request per second**, requires a real User-Agent, and is free for non-commercial use. The app queues network calls and caches metadata rather than polling. Cover Art Archive hosts third-party artwork of varying rights; it is loaded only from their API and never bundled. No audio recordings or copyrighted lyrics are included. This is not an official Juice WRLD, Grade A, Interscope or Apple Music app.

### ABR test gate
A build pass alone isn't the finish line. Verify actual installation on API 26 and modern Android, tapping all filters/search/favorites/detail/back, offline behavior, release track loading, large text scaling, rotation, and a real screenshot. `.github/workflows/juice-wrld-android.yml` runs compilation, unit tests and installed-app smoke checks. No signed release should be claimed without signing/configuration.

### Source documentation
- MusicBrainz API: https://musicbrainz.org/doc/MusicBrainz_API
- Rate limits: https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting
- Artist: https://musicbrainz.org/artist/4e4ebde4-0c56-4dec-844b-6c73adcdd92d
