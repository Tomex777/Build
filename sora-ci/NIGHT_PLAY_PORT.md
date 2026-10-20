# Sora's NIGHT Play game and story integration

Android Sora includes its existing 12 Compose games plus **21 distinct NIGHT Play
games** and the complete **The Last Call** interactive fiction (34 entries total).
No launcher entry is a link to an uninstalled web app.

Original game sources come from:
- `Tomex777/Build` at `coord/night-play-wave2-integration-resume-20261020`:
  arcade, action, puzzles, strategy, club, jigsaw, piano-tiles, stories and game-shell.css.
- `Tomex777/Build` at `feature/night-play-sling-siege-20261020`: Sling Siege.

Imported Git tree SHAs are preserved in the Sora branch so release builds
reproduce the original game engines and story without a live network fetch.
WebView maps `https://appassets.androidplatform.net/<route>` to
`app/src/main/assets/night-play/<route>` using intercepted Android assets,
without enabling `file://` or cross-origin file access.

The Last Call uses the **real 38-chapter manuscript**, engine, autosave,
choice/memory/heart systems, three authored endings, journal, player identity,
and story options. Its progress stays inside Android WebView private storage
on this device. It is not an online NIGHT Play account save.

The extra card and board games are **offline versus CPU or local tabletop**,
not online multiplayer. Optional Unsplash gallery photography and Three.js
Ludo dice need connectivity; Jigsaw can use its local procedural themes.
Game sound remains optional. Sora's fullscreen shell hides the Android
status bar and routes in-game back navigation to its native Games tab.

### Checks
- `sora-night-play-android-acceptance.yml`: compile and run all bundled
  engines and The Last Call progression tests, start 22 WebView experiences
  and Water Sort on Android 16, perform touch/Back navigation, take real screenshots
  and disable Wi-Fi/data to prove local content loads.
- `sora-arcade-android-acceptance.yml`: native interactive gameplay and
  2048 score restore on Android 8/16.
- `sora-svg-visual-review.yml`: verifies all 12 hand-drawn SVG/native
  vector pairs, including Water Sort, across 40/96/256px.
- `sora-signed-release-acceptance.yml`: signed APK build and Android 16
  installation test. No permanent Sora signing key means this workflow
  can only produce a temporary QA key; not a production upgradable release.

Do not claim Play Store release or cross-device story sync unless separately
implemented and tested. User-initiated real source authentication is still
necessary for challenged external YouTube Music playback.
