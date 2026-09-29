# Artist Scene Studio

New scene-first Android project for artist reference and scene construction. Empty scenes, environments, props, lights, cameras, and animation remain valid without humanoids.

This is the start of a feasibility foundation, not a viable product checkpoint. The viewport is being advanced from the cube fixture to a pinned, licensed GLB engineering prop. CI runtime proof is still required before claiming the import path works.

- Kotlin + Jetpack Compose
- compileSdk / targetSdk 36, minSdk 26
- Portrait-designed, landscape-supported
- Initial renderer adapter: SceneView 3.6.0 / Google Filament
- Neutral namespace: `studio.artistscene.app`

Build with JDK 17 and Android SDK 36 using `gradle :app:testDebugUnitTest :app:assembleDebug`.

Before building, run `scripts/fetch-test-assets.sh`. It obtains the small pinned offline starter set and test fixtures, checking each SHA-256 digest; the app has no runtime download dependency. Read [feasibility decisions](docs/FEASIBILITY_DECISIONS.md) and [test asset intake](docs/TEST_ASSETS.md). Do not expand content until the renderer, persistence, import, rigging, animation, shadows, and device performance are proven.
