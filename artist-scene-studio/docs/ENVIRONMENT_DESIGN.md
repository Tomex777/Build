# Mise — Floor & Sky Design (Artist Review)

## Motivation and benchmark

The previous XZ floor used a near-skin neutral-green `#AAAFA5`, which caused light humanoid bodies to blend into the ground. The creative direction is the *feeling* of the Magic Poser web editor: continuous horizon, outdoor atmosphere, studio-like lighting, and a readable subject. The repo `TheAjaykrishnanR/Magic-Poser-Webapp-Offline` is not used as an asset source; its public readme does not establish licensing rights for redistributed Magic Poser content.

## Implemented (PR #221)

- Matte PBR ground at y=-0.01m, full 2km XZ extent, ground-cast shadow reception and existing contact shadows retained. All preset ground tones are neutral low-chroma slate for legibility against human skin, including the existing MakeHuman model and brighter clothing.
- Original seamless procedural cubemap at 128x128 per face, orientation-aware horizon, per-preset upper/lower skies, atmospheric horizon, controlled cloud structure, sun scattering where appropriate; stays stationary in world coordinates while camera orbits. The generated cubemap is fully offline.
- Light tool > Environment (below light controls): choose a scene preset and independently show/hide the ground. Presets are saved with the scene and included in normal undo/redo. Defaults on old saved projects are safe.
- Presets: Photo Studio, Outdoor Daylight (default), Warm Sunset, Soft Overcast, Cyclorama, Plain Reference.
- Only the skybox/ground materials change between these modes. Existing authored camera, lights, pose, transforms, model assets, and projects are untouched.

## Visual acceptance checklist

1. Open a real MakeHuman figure on the Daylight preset. Verify torso, legs and feet stand out from the slate floor, including at far camera zoom.
2. Confirm its shadow is clearly anchored to the ground while orbiting, with no XY vertical wall or horizon sliding with the camera.
3. Switch Daylight → Overcast → Studio Grey → Sunset. The sky and floor should change promptly, without black frames, native crashes, broken actor selection, or force-stop data loss.
4. Turn ground off. Characters remain visible against the sky, and tapping the old floor area does not select an invisible object.
5. Save, force-stop, reopen. The selected preset, ground visibility and authored poses should persist. Undo/redo should restore presets without resetting character selection.
6. Inspect portrait and landscape screenshots with the system bar hidden (pending UI PR #220), plus viewport color distribution on Android API 26 and API 36.
7. Compare highlights and contact shadows across pale/dark skin, white clothing, and metallic props; avoid overly bright or saturated blue sky intruding on artwork. The floor should be legible but not visually dominant.

## Higher-fidelity follow-on: real HDRI image-based lighting

**Not implemented in this PR.** To achieve truly photographic sky/ground lighting, choose a low-resource CC0 Poly Haven HDRI with clear provenance. Preprocess at build time with Filament's `cmgen -f ktx -x <output> <source>.hdr` into skybox KTX and prefiltered IBL KTX, and package these assets for **offline** runtime loading. Keep the generated file sizes, mobile GPU cost and license manifest under review; include a lightweight procedural fallback for Android 8 and reduced-memory phones. Tune exposure / tone mapping, ensure cubemap lighting matches floor shadow direction, and inspect real instrumented screenshots before enabling by default.

Sources (reference only):
- https://magicposer.com/
- https://github.com/TheAjaykrishnanR/Magic-Poser-Webapp-Offline
- https://polyhaven.com/license (CC0)
- https://google.github.io/filament/dup/cmgen.html

No Magic Poser screenshots, bundles, shaders, textures or model files have been copied into Mise.
