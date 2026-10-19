# Offline Games — Android foundation (temporary app title)

Android app for an original offline game collection, expanding in tested batches toward 105+ games. The permanent public name is not yet chosen.

- **Application ID:** `com.night.offlinegames`, following the required `com.night.<appname>` convention. Change the final segment consistently at renaming time.
- **Platform:** native Android / Kotlin / Compose, `minSdk 26`.
- **Current playable catalog:** 67 implemented games, not 105 placeholders: native 2048; six existing NIGHT Play arcade/puzzle games bundled offline; and 60 newly implemented locally bundled logic, board, card and action games in `classics/`.
- **No INTERNET permission.** The embedded arcade loads under WebViewAssetLoader on the reserved `appassets.androidplatform.net` virtual host, with network loads blocked.
- **Progress:** 2048 persists the board and best in device preferences; arcade game scores persist in the WebView's local storage; hub remembers recent games and favourites.
- **Style:** no ad SDK, online account requirement, global volume slider, placeholder tiles, or duplicate back buttons.
- **Build:** `cd offline-games && gradle :app:assembleDebug` (Gradle 8.13, Java 17, Android SDK 36).
- **Validation:** `gradle :app:testDebugUnitTest`; GitHub Actions additionally checks APK package name, min SDK, offline files, and absence of the Internet permission.

## Reused game source and credits

The six browser games are copied from **this same owner's NIGHT Play** source tree, `night-play/arcade` and `night-play/puzzles` in `Tomex777/Build`, development branch `feature/night-play-ludo-motion-polish-20261014`. Each source and CSS file is bundled inside `app/src/main/assets` rather than fetched at runtime. Game code is maintained separately here so further NIGHT Play releases cannot silently break the offline app.

Wing Hop embeds Kenney Animal Pack Remastered / Background Elements Remastered sprites as data URIs; source comments identify these as CC0 1.0. Keep asset provenance in future updates. Do not import unverified third-party games, photos, trademarks or copyrighted sprites on the assumption that the mechanics are open-source.

## New original classic modules

Added `classics/` with separate logic modules: Tic-Tac-Toe, Reversi, Dots & Boxes, Mancala, Hex, Battleship, Pentago, Lights Out, Sliding Puzzle, Tower of Hanoi, Color Sort, Nonogram, Simon, Pattern Recall, Take Away, Word Search, Higher or Lower, War, Snap, Crazy Eights, Pong, Breakout, Space Invaders and Fruit Slash. Each has interactive rules and a real win/score condition. The canvas action games use local requestAnimationFrame render loops with cleanup. These original games are implemented in-browser but bundled in the APK and blocked from requesting the Internet.

## Further independently playable games

Added original Snake, Frogger, Stack Tower, Whack-a-Bunny, Quick Draw, Coin Catcher, Bubble Pop, Gravity Flip; five complete solitaire variants (Golf, Pyramid, Tri Peaks, FreeCell and Klondike); Go 5×5 and Nine Men's Morris; Maze Runner, Darts, Basketball Shoot, Penalty Shootout, Ski Slalom and Bomb Diffuser. All are bundled as local HTML/JS game assets and run with WebView networking disabled. The board games enforce legal moves and have computer opponents where specified. Classic variants are in-app adaptations, not copied proprietary designs.

## Batch expansion: eight more completed game engines

Added Car Dodge, Zigzag Path, Laser Dodge, Archery, Golf Putt, ten-frame Bowling (with strike/spare bonus scoring), Swimming Race, and American Checkers (mandatory capture and chain jumps). All are separate implementations, using original offline JavaScript/Canvas logic and no web services.

## Rules-complete chess and first racing batch

Bundled the BSD-2-Clause `chess.js` v0.10.3 rules engine for legal chess (including castling, en passant, check, mate, promotion and stalemate), rendered with original SVG pieces and an offline computer opponent. Also added five original racing mechanics: Drag Race, Bike Trail, Boat Race, Drift Hunt and Horse Derby. Licensing is included under `assets/classics/CHESS-LICENSE.txt`. All games launch offline and do not request internet access.

## Themed Memory Match (offline artwork)

Added real illustrated memory cards rather than shape placeholders, with 18 distinct pictures each in Animals, Vehicles and Space. The default is 6×4; choose 4×4, 6×4, 6×5 or 6×6 only from the dedicated in-game Settings control. Cards flip smoothly, unmatched cards return face-down, matching pairs stay visible, and play state, move count and elapsed time can be resumed locally. Art is individually bundled SVG from Twemoji and attributed under CC BY 4.0, with the entire graphics license kept inside the app. No images are loaded over a network.

## Next game batches

Continue with the requested, curated visual Memory Match and Jigsaw games, then complete the remaining board, action, sports and racing catalog. Never claim completion from a tile or stub. Memory Match uses locally bundled illustrated picture sets rather than shape-only placeholder cards. The NIGHT Play monolithic `index.html` depends on the website's account/channel shell and should **not** be copied blindly into the offline APK. Extract and adapt each game deliberately.

## Manual device review needed

CI can check compilation, native board logic and package contents. Real-device checks are still required: install on Android 8+, airplane-mode launch, swipe responsiveness, pause/resume, rotation, webview back navigation, battery usage, and storage restoration after force stop.
