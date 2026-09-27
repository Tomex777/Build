# Feasibility foundation decisions

Status date: 2026-09-28. This work log does not claim the product is viable.

## Initial renderer selection

Use SceneView Android 3.6.0 as the first integration adapter and Google Filament 1.70.0 as the renderer. SceneView is Apache-2.0 and provides a Compose viewport backed by Filament with glTF/GLB support. CI showed SceneView 4.45.0 resolves AndroidX artifacts requiring compileSdk 37, so that release cannot be used with the required API 36 baseline. Version 3.6.0 declares a Compose BOM from June 2025 and Filament 1.70.0; runtime/API compatibility still requires CI verification.

This is a first choice, not a permanent lock. Native renderer stability, import fidelity, lifecycle, licensing of transitive artifacts, and Galaxy A16 performance need runtime evidence. The canonical scene model belongs to this app; SceneView nodes remain disposable renderer objects.

## Scene and coordinate baseline

- Scene graph supports heterogeneous actor types and empty scenes.
- World units are meters and +Y is up.
- Camera, light, asset reference, rig, world, and animation data are part of the versioned project format.
- JSON persistence is app-private. Renderer objects never serialize.
- Animation tracks target actor IDs and property paths, not only humanoid bones.

## Current viewport

The viewport now loads a real PBR Boom Box GLB fixture from app assets, with a floor, directional sun light, and a point fill light. CI is being extended to assert the real model loads, adjust and save its transform, force-stop the app, and verify the restored project. Until that runtime run is green, this remains an implementation target rather than a proven capability.

## Open gates

1. API 36 build and emulator renderer smoke.
2. Real legally redistributable GLB prop loads and renders from app assets; user-selected local SAF import remains open.
3. App-owned selection and transform updates reaching the renderer.
4. Rigged humanoid including hands/fingers, facial morphs, hair and clothing.
5. Joint/morph control, skeletal playback and IK.
6. Directional/point/spot lighting and cast/receive shadows.
7. Force-stop/reopen save-load validation.
8. Clean viewport export.
9. Portrait/landscape continuity and Galaxy A16 performance.
