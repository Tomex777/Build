# Renderer and production decisions

Status date: 2026-09-30.

Artist Scene Studio keeps its durable scene model independent from renderer objects. SceneView / Filament is an adapter, not the project format, so projects survive renderer recreation, process death, and future renderer changes.

## Renderer selection

SceneView Android 3.6.0 with Google Filament 1.70.0 is the production baseline for the Android API 36 target. Newer SceneView releases that require compileSdk 37 are intentionally not adopted while the app baseline remains compileSdk / targetSdk 36.

The app owns all scene, rig, camera, lighting, timeline, and asset-library state. SceneView nodes and Filament instances are disposable runtime objects.

## Coordinate and persistence model

- World units are meters and +Y is up.
- Actors may be characters, props, vehicles, environments, lights, cameras, or effects.
- Camera, light, asset reference, rig pose, world, reference-image, and animation data are versioned project state.
- JSON project persistence is app-private and schema-migrated.
- Animation tracks target durable actor IDs and property paths.
- Imported model payloads are copied into the managed asset library and addressed by SHA-256.

## Production acceptance gates

The Android CI workflow is the source of truth. A successful run proves:

1. Unit tests and both debug / release compilation.
2. Android API 26 and API 36 app launch with a non-black real renderer frame.
3. Bundled PBR GLB loading plus a GLB selected through Android's real document picker and persisted into My Assets.
4. Direct move / rotate / scale manipulation reaching the renderer and autosave.
5. Real glTF skin discovery, joint rotation, visible skin deformation, finger semantics, and two-bone wrist/ankle IK.
6. Two character instances retaining independent rig state.
7. Authored transform and complete rig-pose timeline keyframes plus scene playback.
8. Embedded animation discovery and playback controls.
9. Directional / point / spot lighting and shadows where the Android / GPU backend supports them.
10. Clean PNG reference export through Android's document destination picker.
11. Explicit save, force-stop, fresh-process reopen, model reload, pose restore, and timeline restore.

## File-format boundaries

GLB is preferred. JSON glTF is accepted only when buffers and images are embedded data URIs; external sibling files are rejected before the renderer with an actionable GLB/export message.

VRM 0.x / 1.0 files are accepted when they are valid GLB containers. The importer retains embedded author, license, credit, expression names, and humanoid metadata where present. Version 1.0 uses the generic glTF skin/morph runtime for editing. VRM-only spring-bone physics, gaze, and first-person runtime behavior are intentionally outside the 1.0 scene-posing contract rather than being silently simulated.

## External release evidence

CI emulator acceptance cannot substitute for a final physical-device install/performance pass on the target Galaxy A16-class device, and GitHub cannot create the owner's private production signing identity. Those are distribution checks, not missing editor functionality.
