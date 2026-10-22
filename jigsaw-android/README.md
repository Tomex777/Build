# Jigsaw — standalone Android game

A separate **native Kotlin/Android** application: `com.night.jigsaw`, `minSdk 26`. No WebView, JavaScript game code, Internet permission, accounts or ads. The 105+ offline-games hub remains separate.

## Play
- Interlocking, deterministic, complementary tabs; 6 (3x2), 12 (4x3), 24 (6x4), 48 (8x6), 72 (9x8), 96 (12x8), 144 (12x12), 240 (20x12), 384 (24x16), and 600 (30x20) *real pieces*.
- A full-board tabletop with loose pieces scattered **above/below in portrait and to either side in landscape**, not hidden in a boxed bottom tray.
- Single-finger piece dragging; release near the actual target snaps and locks, misplaced pieces remain movable. Drag blank tabletop to pan; pinch to zoom.
- At high piece counts, zoom to enlarge pieces before moving them. Choose a piece by its *actual silhouette*.
- Three hints, a preview toggle initially OFF, reset-to-board viewport, puzzle progress, moves and completion.
- Saved on placement, menu actions, photo imports and app pause; resumes after process death.
- User photo import via Android photo picker; image is cropped and copied into app-private storage for offline replay.
- The bundled starter is **original programmatic illustration**, not misrepresented as a photograph. User-imported photos are real; bundled rights-cleared photo gallery is a separate expansion.

## Validation
`cd jigsaw-android && gradle :app:testDebugUnitTest :app:assembleDebug`.
Unit tests check counts, edge interlock polarity, snap tolerance, hint cap and saved model restoration. GitHub Actions checks the debug APK application ID, Android 8+ support and absence of Internet/WebView. **Emulator installation, on-device drag QA, memory profiling, image import and visual review are still required before release.**

## ABR
No duplicated controls, no default Android text inputs, minimal native layout, full-screen game viewport (status bar hidden), genuine offline play, proper Android package, smooth touch following and no fake completion claim. Maintain engine independence so later the standalone implementation may be shared with the hub without copying the UI.

## Source parity and release bar
The existing NIGHT Play website Jigsaw is the product reference, **not** code to embed. The website already has real photo gallery, challenge/memory rules, sound, confetti, timer, connected-piece groups and edge-only filtering. The native standalone currently has snapping to the board and custom photos but **does not yet have every website feature**. It must be at least equivalent in tactile feel, visuals, modes and puzzle variety before release. Landscape support has a native side-scatter board and compact side controls; rotation preserves completed progress, but currently re-scatters loose pieces, which needs polish. 384/600-piece performance and drag usability require real-device QA on affordable Android phones. Never equate a green build with device-tested completion.
