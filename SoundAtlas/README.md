# NIGHT Sound Atlas — native Android

A separate Android 8+ acoustic-world builder expanding the spatial-audio foundation of EchoSpace.

## Implemented foundations
- Fullscreen native Canvas editor: walls, zones, draggable listener and sound emitters, pinch zoom, pan, custom controls.
- 6 self-contained world templates and 12 synthesized/procedural sound species.
- Stereo audio with interaural timing/level differences, material-based occlusion, distance filtering and room-tail reflections. **Approximation**, not ray-traced acoustics or individualized HRTF.
- Living sound behavior: seeded irregular events, moving emitters and environmental modulation; no 30-second ambiance loops.
- Explore mode: movement through world with changing acoustics.
- JSON scene format with schema validation, compact preset-independent saves and share/export/import via Android's native document framework.
- Full offline playback, no account or server required. Native controls, no Android default SeekBars/sliders.

## Architecture
- `World.kt`: geometry, scenes, compact JSON import/export, acoustic coefficients.
- `AudioEngine.kt`: realtime off-main-thread PCM renderer and spatial/reverb processing.
- `MainActivity.kt`: immersive Canvas-based editing/exploring surface and file actions.

## Build
`cd SoundAtlas && gradle testDebugUnitTest assembleDebug`
Requires Java 17, Android SDK 35, Gradle 8.11.1. GitHub Actions builds/smoke-tests and produces a temporary-test-signed APK.

## Scope / known limitations
This is a working **native foundation**, not the entire planned Sound Atlas product. No sophisticated mesh ray tracing, measurement-grade material simulation, imported custom recordings, personalized HRTF, online community publishing, true 3D world rendering, or visual scripting yet. Those need separate implementations and careful device listening tests. Scenes export as configuration only; built-in synthesis is available on any Sound Atlas install.
