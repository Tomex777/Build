# Offline Games — Android foundation (temporary app title)

Android app for an original offline game collection, expanding in tested batches toward 105+ games. The permanent public name is not yet chosen.

- **Application ID:** `com.nice.offlinegames`, following the required `com.nice.<appname>` convention. Change the final segment consistently at renaming time.
- **Platform:** native Android / Kotlin / Compose, `minSdk 26`.
- **Current playable catalog:** five games, not 105 placeholders: native 2048; the original NIGHT Play arcade implementation of Wing Hop, Block Drop, Minesweeper, and Maze Chase, bundled as offline assets.
- **No INTERNET permission.** The embedded arcade loads under WebViewAssetLoader on the reserved `appassets.androidplatform.net` virtual host, with network loads blocked.
- **Progress:** 2048 persists the board and best in device preferences; arcade game scores persist in the WebView's local storage; hub remembers recent games and favourites.
- **Style:** no ad SDK, online account requirement, global volume slider, placeholder tiles, or duplicate back buttons.
- **Build:** `cd offline-games && gradle :app:assembleDebug` (Gradle 8.13, Java 17, Android SDK 36).
- **Validation:** `gradle :app:testDebugUnitTest`; GitHub Actions additionally checks APK package name, min SDK, offline files, and absence of the Internet permission.

## Reused game source and credits

The four browser games are copied from **this same owner's NIGHT Play** source tree, `night-play/arcade` in `Tomex777/Build`, development branch `feature/night-play-ludo-motion-polish-20261014`. Each source and CSS file is bundled inside `app/src/main/assets` rather than fetched at runtime. Game code is maintained separately here so further NIGHT Play releases cannot silently break the offline app.

Wing Hop embeds Kenney Animal Pack Remastered / Background Elements Remastered sprites as data URIs; source comments identify these as CC0 1.0. Keep asset provenance in future updates. Do not import unverified third-party games, photos, trademarks or copyrighted sprites on the assumption that the mechanics are open-source.

## Next game batches

Implement separately and test before listing in the hub: Snake, Tic-Tac-Toe, Sudoku, Memory Match, Jigsaw, then card/board and action games. The NIGHT Play monolithic `index.html` depends on the website's account/channel shell and should **not** be copied blindly into the offline APK. Extract and adapt each game deliberately.

## Manual device review needed

CI can check compilation, native board logic and package contents. Real-device checks are still required: install on Android 8+, airplane-mode launch, swipe responsiveness, pause/resume, rotation, webview back navigation, battery usage, and storage restoration after force stop.
