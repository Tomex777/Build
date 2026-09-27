# Test asset candidates

No third-party model binary is committed to Git. The CI/build script fetches a pinned CC0 model into the APK's assets and verifies its hash. The model is an engineering fixture, not production library content.

| Candidate | Source / creator | License on source page | Assessment |
|---|---|---|---|
| Boom Box | Khronos glTF Sample Assets, Microsoft | CC0-1.0 | Selected prop fixture. Source commit, checksum, and license copy are stored in `app/src/main/assets/licenses/`; CI fetches and verifies the GLB before building. |
| Rigged Figure / Rigged Simple | Khronos glTF Sample Assets; Cesium credited | CC-BY-4.0 | Appropriate skeletal loader test fixtures, not quality hero characters; preserve attribution if bundled. |
| Base Rigged Stylized Humanoid Character (YW) | Girush, OpenGameArt | CC0 | Promising stylized test candidate. Inspect the actual archive, formats, rig, finger joints, morphs, hair/clothing, textures, and size before adoption. Page claims do not establish runtime capability. |
| Box Vertex Colors | Khronos glTF Sample Assets; Marco Hutter credited | CC0 | Potential real prop/import fixture after selecting exact revision and preserving its source record. |

## Intake gate

Keep source URL, creator, revision, license text, redistribution rights, and attribution with every accepted asset. Inspect the actual payload and record rig/morph metadata. Reject an asset that lacks the needed capability. Keep engineering fixtures visibly distinct from production assets.
