# Jigsaw — standalone Android game

A separate **native Kotlin/Android** application: `com.nice.jigsaw`, `minSdk 26`. No WebView, JavaScript game code, Internet permission, accounts or ads. The 105+ offline-games hub remains separate.

## Play
- Interlocking, deterministic, complementary tabs; 24 (6x4), 48 (8x6) and 144 (12x12) *real pieces*.
- A full-board tabletop with loose pieces scattered **above and below** the large board, not hidden in a boxed bottom tray.
- Single-finger piece dragging; release near the actual target snaps and locks, misplaced pieces remain movable. Drag blank tabletop to pan; pinch to zoom.
- At 144 pieces, zoom to enlarge crowded pieces before moving them. Choose a piece by its *actual silhouette*.
- Three hints, a preview toggle initially OFF, reset-to-board viewport, puzzle progress, moves and completion.
- Saved on placement, menu actions, photo imports and app pause; resumes after process death.
- User photo import via Android photo picker; image is cropped and copied into app-private storage for offline replay.
- The bundled starter is **original programmatic illustration**, not misrepresented as a photograph. User-imported photos are real; bundled rights-cleared photo gallery is a separate expansion.

## Validation
`cd jigsaw-android && gradle :app:testDebugUnitTest :app:assembleDebug`.
Unit tests check counts, edge interlock polarity, snap tolerance, hint cap and saved model restoration. GitHub Actions checks the debug APK application ID, Android 8+ support and absence of Internet/WebView. **Emulator installation, on-device drag QA, memory profiling, image import and visual review are still required before release.**

## ABR
No duplicated controls, no default Android text inputs, minimal native layout, full-screen game viewport (status bar hidden), genuine offline play, proper Android package, smooth touch following and no fake completion claim. Maintain engine independence so later the standalone implementation may be shared with the hub without copying the UI.
