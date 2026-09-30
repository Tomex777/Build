# Mise

Mise is a native Android scene-building and artist-reference studio. The 1.0 scope is viewport-first: compose multi-actor scenes, import local 3D models, pose real skinned characters, animate transforms and poses, frame cameras and lights, add image references, save projects, and export a clean PNG reference.

## Production scope

- Kotlin + Jetpack Compose
- compileSdk / targetSdk 36, minSdk 26
- SceneView 3.6.0 / Google Filament 1.70.0 renderer adapter
- GLB, VRM-as-GLB, and self-contained glTF 2.0 import through Android's document picker
- Durable checksum-addressed My Assets library
- Real glTF skin discovery, direct joint posing, finger selection, morph weights, and two-bone IK
- Multiple independently posed characters in one scene
- Move / rotate / scale gizmos, hierarchy parenting, visibility, locking, duplication, and undo / redo
- Perspective and orthographic cameras with persisted orbit framing
- Directional, point, and spot lights with color, intensity, range, cone, and supported shadows
- Embedded model animation playback plus authored scene transform / pose timeline keyframes
- 3D reference-image planes and clean PNG reference export
- Autosave, explicit save, schema migration, and force-stop / reopen restoration
- Portrait-first UI with landscape continuity and a dark launch surface

## Verification

The canonical acceptance gate is .github/workflows/artist-scene-studio-android-ci.yml. It runs unit tests, builds debug and ARM64 release artifacts, then launches the real app on Android API 26 and API 36. The renderer smoke covers a real PBR GLB frame, a user-selected GLB through Android's picker, direct transforms, real skin deformation, two-bone IK, two independent character poses, timeline authoring/playback, clean PNG export, persistence, force-stop, and fresh-process restoration.

The smoke fixtures are pinned and license-tracked. scripts/fetch-test-assets.sh verifies their SHA-256 digests before a build; the app has no runtime download dependency.

## Build

Use JDK 17 and Android SDK 36.

    scripts/fetch-test-assets.sh
    gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease

Version 1.0.0 is the production code baseline. CI publishes debug acceptance builds plus an ARM64 release candidate, a release AAB, signature verification, checksums, and an Android 16 release-install smoke. When the Mise release-signing secrets are configured, the same workflow emits production-signed APK/AAB artifacts; private signing material is never generated or committed.
