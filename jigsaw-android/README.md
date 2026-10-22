# Jigsaw — standalone Android game

A separate **native Kotlin/Android** application: `com.night.jigsaw`, `minSdk 26`. No WebView, JavaScript game code, accounts or ads. Optional INTERNET permission is only for user-selected picture downloads; gameplay and saved pictures work in airplane mode. The 105+ offline-games hub remains separate.

## Play
- Interlocking, deterministic, complementary tabs; 6 (3x2), 12 (4x3), 24 (6x4), 48 (8x6), 72 (9x8), 96 (12x8), 144 (12x12), 240 (20x12), 384 (24x16), and 600 (30x20) *real pieces*.
- A full-board tabletop with loose pieces scattered **above/below in portrait and to either side in landscape**, not hidden in a boxed bottom tray.
- Single-finger piece dragging; release near the actual target snaps and locks, misplaced pieces remain movable. Drag blank tabletop to pan; pinch to zoom.
- At high piece counts, zoom to enlarge pieces before moving them. Choose a piece by its *actual silhouette*.
- Three hints, a preview toggle initially OFF, reset-to-board viewport, puzzle progress, moves and completion.
- Saved on placement, menu actions, photo imports and app pause; resumes after process death.
- User photo imports are center-cropped at up to 2400×1800 and stored as **separate reusable library entries** in app-private storage; importing again does not overwrite the previous custom image.
- Optional curated downloads: 14 existing NIGHT Play photo subjects, downloaded **only after pressing Get** and then stored inside the same offline gallery. The starter catalog is not a huge library yet; future larger licensed categories/packs must include licensing and source provenance.
- 240-piece puzzles have 4 sorting zones; 384 and 600 have 6 sorting zones. Each of six 600-piece zones holds at most 100 *unplaced* pieces with non-overlapping selection hit shapes; already placed pieces remain on the shared final board. The active zone and puzzle progress are saved.
- The bundled starter is **original programmatic illustration**, not misrepresented as a photograph. Downloaded pictures are not shipped as application assets.
- Source licensing: 14 curated items are reused from `night-play/jigsaw/photo-gallery.js` and supplied by Unsplash under https://unsplash.com/license. The app is a puzzle experience, not an image distribution library. Personal imports stay private; app uploads nothing. Never scrape/redistribute an entire photo service.

## Validation
`cd jigsaw-android && gradle :app:testDebugUnitTest :app:assembleDebug`.
Unit tests check counts, edge interlock polarity, snap tolerance, hint cap and saved model restoration. GitHub Actions checks the debug APK application ID, Android 8+ support and absence of WebView. **Emulator installation, 600-piece touch precision, image-download success/failure, photo reuse, memory profiling and actual portrait/landscape screenshots are still required before release.**

## ABR
No duplicated controls, no default Android text inputs, minimal native layout, full-screen game viewport (status bar hidden), genuine offline play, proper Android package, smooth touch following and no fake completion claim. Maintain engine independence so later the standalone implementation may be shared with the hub without copying the UI.

## Source parity and release bar
The existing NIGHT Play website Jigsaw is the product reference, **not** code to embed. The website already has real photo gallery, challenge/memory rules, sound, confetti, timer, connected-piece groups and edge-only filtering. The native standalone currently has snapping to the board and custom photos but **does not yet have every website feature**. It must be at least equivalent in tactile feel, visuals, modes and puzzle variety before release. Landscape support has a native side-scatter board and compact side controls; rotation preserves completed progress, but currently re-scatters loose pieces, which needs polish. 384/600-piece performance and drag usability require real-device QA on affordable Android phones. Never equate a green build with device-tested completion.

## Photography policy — fixed catalogue, no scheduled refresh
Prefer a compact **offline starter pack** of rights-cleared photos, explicitly chosen downloadable collections, and user-owned pictures. Do not implement periodic image discovery, a recurring refresh job, remote manifest polling, or automatic image/background downloads. The curated catalogue is packaged as stable app code and changes only with a deliberately tested app update. If a source ceases to work, already saved images remain playable offline; failed user-initiated downloads show an error instead of affecting games. Publisher rights, license, attribution and provenance must accompany any new curated image. Avoid unnecessarily large bundled assets.
