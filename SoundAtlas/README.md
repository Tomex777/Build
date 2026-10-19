# NIGHT Sound Atlas — native Android acoustic worlds

Separate Android 8+ acoustic-world builder. EchoSpace continues independently.

## Implemented
- Native immersive Canvas editor with pinch/pan, movable listener and emitters, built-in original SVG vectors, custom controls and no default Android SeekBars.
- **10 explorable acoustic worlds**, including Storm Cathedral, Ancient Forest, Rain Garden, Village at Dawn, Starship Meridian, and Ocean Cavern.
- **18 distinct audio types** with fourteen individually credited, offline CC0 field recordings, alongside procedurally varied ambience, blended with seeded irregular event scheduling and peak-safe normalization.
- Material-specific occlusion, distance attenuation and low-pass filtering, room-dependent reverberation, measured seven-direction **MIT KEMAR non-personalized horizontal-plane HRTF** for four prominent sources (asset available), and finite-wall single-bounce reflection delays.
- Click-resistant gain, pan and HRTF kernel smoothing across editor/listener movement, with lower-cost binaural fallback for more distant voices.
- A* Explore navigation through real doors/openings, read-only acoustic status panel, live editable wind/rain/storm conditions.
- Validated compact versioned JSON environment import/export and secure `.satlas.json` file sharing via scoped Android FileProvider. Offline-first; no account required.
- Auto-saving and restored worlds, JVM acoustic geometry, image-source reflection, HRTF interpolation, portability and audio-level tests.
- GitHub workflows for Android builds and screenshot QA, including a PNG System UI modal guard so OS crash dialogs cannot pass unnoticed as valid screenshots.

## Key code
- `World.kt` defines world schema, geometry, material acoustics and ten built-in worlds.
- `EarlyReflections.kt` computes finite segment specular reflections with other-wall obstruction checks.
- `MeasuredHrir.kt` loads measured seven-angle KEMAR response bins (0°–180°) and interpolates head-relative directions.
- `AudioEngine.kt` drives realtime 44.1 kHz stereo PCM, reverb/early reflections, mixed binaural rendering and offline sounds.
- `Navigation.kt` implements A* walkable pathways.
- `MainActivity.kt` implements the fullscreen native editor and secure import/export/share.
- `tools/png_modal_guard.py` fails UI tests when screenshots contain blocking bright platform dialogs rather than the dark Sound Atlas app.

## Build
`cd SoundAtlas && gradle testDebugUnitTest assembleDebug`

Java 17, Android SDK35 and Gradle 8.11.1 are used in CI; minSdk26. GitHub Actions bundles the CC0 recordings and measured KEMAR HRIR from EchoSpace's already-verified original audio pipeline and signs a disposable test build. A test-signing key is **not** a durable production release key.

## Audio/asset provenance
Offline sound and HRTF preparation reuse the project's existing EchoSpace CC0 recording pipeline and MIT KEMAR measured filter dataset; the original recordings and metadata are available through the EchoSpace pipeline. Binary environment configuration files do not embed or upload audio samples. Custom future audio cannot be shared without explicit license and asset transfer.

## Remaining work
This is an experimental but buildable native foundation, **not** a certified measurement-grade acoustic simulator. Current single-bounce geometry and statistical diffuse reverb are approximations; HRTF is generic, horizontal-plane and limited to the most prominent sources. Missing: 3D elevation/vertical binaural modeling, multibounce diffraction, custom recordings/asset pack management, acoustic material measurements, visual event timeline, community catalog/cloud publishing, long-play listening validation on actual headphones and a persistent signing key. UI screenshot smoke tests do not prove audio quality or all gestures on real hardware.
