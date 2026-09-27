# Test asset candidates

No third-party model binary is committed to Git. The CI/build script fetches a pinned CC0 model into the APK's assets and verifies its hash. The model is an engineering fixture, not production library content.

| Candidate | Source / creator | License on source page | Assessment |
|---|---|---|---|
| Boom Box | Khronos glTF Sample Assets, Microsoft | CC0-1.0 | Selected prop fixture. Source commit, checksum, and license copy are stored in `app/src/main/assets/licenses/`; CI fetches and verifies the GLB before building. |
| Rigged Simple | Khronos glTF Sample Assets; © 2017 Cesium | CC-BY-4.0 | Source describes animations and skins; suitable as a separate skeletal-loading fixture after the prop import baseline. Not a quality hero character; preserve attribution if bundled. |
| β Ver AvatarSample_1 | VRoid Studio / pixiv Inc. | CC0, according to the official model page | Strong anime-style humanoid candidate with visible hair and clothing. The license permits use, but the actual downloadable VRM has not yet been inspected for finger bones, expressions, spring bones, file size, or runtime compatibility; do not count this as rig proof yet. |
| Base Rigged Stylized Humanoid Character (YW) | Girush, OpenGameArt | CC0 | Promising stylized test candidate. Inspect the actual archive, formats, rig, finger joints, morphs, hair/clothing, textures, and size before adoption. Page claims do not establish runtime capability. |
| Box Vertex Colors | Khronos glTF Sample Assets; Marco Hutter credited | CC0 | Potential real prop/import fixture after selecting exact revision and preserving its source record. |

## VRM investigation

The VRM standard is a promising specialized humanoid path beside generic GLB/glTF. Its specification defines canonical humanoid bones (including optional individual finger joints), and documents facial expressions, gaze, spring-bone movement, and first-person metadata. These are format capabilities; a particular model may omit optional features, and SceneView's generic GLB loading does not implement the VRM extensions by itself.

The official [VRoid FAQ](https://vroid.pixiv.help/hc/en-us/articles/4402614652569-Do-VRoid-Studio-s-sample-models-come-with-conditions-of-use) marks β Ver AvatarSample_1 as CC0, and its [model page](https://vroid.pixiv.help/hc/en-us/articles/360012381793-%CE%B2-Ver-AvatarSample-1) says it can be freely edited and used. It is the leading visual candidate for a later character proof, subject to inspecting the actual VRM payload and choosing an importer/runtime that supports its features. The current GLB vertical slice remains separate from this VRM work.

Specification references: [VRM features](https://vrm.dev/en/vrm/vrm_features/), [VRM 1.0 humanoid bone mapping](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md), and [VRM expressions](https://github.com/vrm-c/vrm-specification/tree/master/specification/VRMC_vrm-1.0).

## Intake gate

Keep source URL, creator, revision, license text, redistribution rights, and attribution with every accepted asset. Inspect the actual payload and record rig/morph metadata. Reject an asset that lacks the needed capability. Keep engineering fixtures visibly distinct from production assets.
