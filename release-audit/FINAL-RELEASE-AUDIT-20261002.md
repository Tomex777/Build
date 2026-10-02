# Repository production finalization audit — 2026-10-02

Current branch/CI audit refreshed at 2026-10-02T16:54:50.338Z.

Five applications are production-finalized at the exact source commits below. Veya has current dual-API QA acceptance, but is not production-finalized until its existing credentials are configured and its permanently signed build passes acceptance. Lyra remains excluded after current #168 fails. Unfinished products and experiments were not promoted.

The full 16-column release matrix, plus blockers, is in `Production-Release-Matrix.csv`. Inventory covers 148 current Build branches (including this audit branch) grouped into 30 repository project/product families, plus external Elementum. Duplicate branches are not counted as separate apps.

## Production-finalized records

### Nami

Application: Nami

Package: `app.nami.android`

Branch: `nami/standalone-foundation`

Final commit: `276e2e33adb673ee23880b6d2935b9703160c85f`

Version: 1.0.0 (1)

Production signer SHA-256: `a3aa7cafd99921b750d2535053ef3d493e3749a5cc92ee5d29b059ba4ee7995a`

Artifact: `nami-arm64-v8a-release-ci-signed.apk` — SHA-256 `c379beacfd26a0c40389567d313f72c88beaec2f0ab262247b946bcbec1492f0`

Artifact: `nami-universal-release-ci-signed.apk` — SHA-256 `6b263bcf971a7f63438a60caacb3d4007246d7f7adf867d26798b75d853bc83b`

Artifact: `nami-release-ci-signed.aab` — SHA-256 `5cfb22f64c8d7a94a6942f1b7bf97fefbb59f4282dcd423b5241a00da6906174`

API 26: PASS. API 36: PASS. Permanent signing: CONFIGURED AND VERIFIED. Install test: PASS (signed universal).

CI: https://github.com/Tomex777/Build/actions/runs/36974075213

Owner recovery: VERIFIED. ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

### Cortex

Application: Cortex

Package: `com.night.cortex`

Branch: `cortex-android-live`

Final commit: `f23bc464fe25ecab5fc772499ec2cf865740b95f`

Version: 1.0.0 (100)

Production signer SHA-256: `47aaa57d156ec1d2812c2787e1e12ec4e05ac59694c576dc412ef3bae2d703e8`

Artifact: `Cortex_ARM64_INSTALLABLE.apk` — SHA-256 `c06717b50f23122d5f99788c578dcad1f3018db4b701b4c6024be7473388d1ae`

Artifact: `Cortex_UNIVERSAL_INSTALLABLE.apk` — SHA-256 `ce73f56105047f0852175321090e2707854760321a145fbac75ff509b09748d4`

Artifact: `Cortex_RELEASE.aab` — SHA-256 `53f07be0861832ead01143bfd67733bca2cd149d1e2e9a14617ace0c1577bdec`

API 26: PASS. API 36: PASS. Permanent signing: CONFIGURED AND VERIFIED. Install test: PASS (signed universal).

CI: https://github.com/Tomex777/Build/actions/runs/36976187255

Owner recovery: VERIFIED. ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

### MirrorChess

Application: MirrorChess

Package: `com.night.mirrorchess`

Branch: `mirrorchess-sdk36-min26-20260922`

Final commit: `3536d51e4c9b5de66fc1511225b49deac07b0f8a`

Version: 1.2.0 (5)

Production signer SHA-256: `39a516df91001cd022408b2221fb434cb1283c6931595681e1a5c653c9b210f5`

Artifact: `MirrorChess-1.2.0-arm64-v8a-release.apk` — SHA-256 `8ac72913de8a57cdbc5c00a0d2b1466c01d14c937917764dfacb4559d2d7349c`

Artifact: `MirrorChess-1.2.0-universal-release.apk` — SHA-256 `47293c5db6f25acfef7df58df4beebe562902e49b44303a14ffedc3af6bf4733`

Artifact: `MirrorChess-1.2.0-release.aab` — SHA-256 `c09bedf6f92b3a39e7248364aa05f3c6a02f669d4db8b0e0ba52b581eddc9d4a`

API 26: PASS. API 36: PASS. Permanent signing: CONFIGURED AND VERIFIED. Install test: PASS (signed universal).

CI: https://github.com/Tomex777/Build/actions/runs/36998454972

Owner recovery: VERIFIED. ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

### Endless

Application: Endless

Package: `com.night.endless`

Branch: `endless-android-ci`

Final commit: `5e21f3fe13fe2e40a433402b6dbbddb954683948`

Version: 1.0.0 (10)

Production signer SHA-256: `4379c791a42d92e349dca2e573ca69c90a25ad812609f348dfac464c12162b9d`

Artifact: `Endless-1.0.0-arm64-production-signed.apk` — SHA-256 `c2ced7d44f62ae34446b87df310f2cd065f813dc2e5faf88393da8b66b8425ef`

Artifact: `Endless-1.0.0-release-production-signed.apk` — SHA-256 `980462bfbe80370aee94d506e47bb5c6229e6c0137f4b4bc4c779040edb7fa3d`

Artifact: `Endless-1.0.0-release-production-signed.aab` — SHA-256 `ce1200eaf571cd9ffe54bad897413d25c43c2d9c8cf37f3c309d05b8a0b0f6ae`

API 26: PASS. API 36: PASS. Permanent signing: CONFIGURED AND VERIFIED. Install test: PASS (signed universal).

CI: https://github.com/Tomex777/Build/actions/runs/36998925802

Owner recovery: VERIFIED. ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

### AOD

Application: AOD

Package: `com.homira.aod`

Branch: `aod-android-foundation`

Final commit: `10e3690c619fc918019bc7a8b7ef7d8276576654`

Version: 0.1.0 (1)

Production signer SHA-256: `e5f2150cafd50317eb44cdd7fc7f7ee6e263abd282da99e8a71041c488674594`

Artifact: `AOD-production-universal.apk` — SHA-256 `ae3f39d0018c7ccd58f591316a64595e289272811b8594cf2d92fb498ac9ebea`

Artifact: `app-release.aab` — SHA-256 `b367e15ffe51c77c3c8c4fbb29483e18cf5cd4edaac47c9ef5b59b29e9772c38`

API 26: PASS. API 36: PASS. Permanent signing: CONFIGURED AND VERIFIED. Install test: PASS (signed universal).

CI: https://github.com/Tomex777/Build/actions/runs/36998776838

Owner recovery: VERIFIED. No native libraries; universal APK is architecture-independent and ARM64-compatible. No physical-device ambient/battery validation performed.

## All discovered project groups

| Project | Branch | Current HEAD | Latest acceptance CI | Final result | Blocker |
|---|---|---|---|---|---|
| Nami | nami/standalone-foundation | 276e2e33adb673ee23880b6d2935b9703160c85f | Nami Android #472 success / 36974075213 | PRODUCTION FINALIZED |  |
| Cortex | cortex-android-live | f23bc464fe25ecab5fc772499ec2cf865740b95f | Cortex Android #217 success / 36976187255 | PRODUCTION FINALIZED |  |
| MirrorChess | mirrorchess-sdk36-min26-20260922 | 3536d51e4c9b5de66fc1511225b49deac07b0f8a | MirrorChess SDK 36 min 26 #115 success / 36998459415; exact branch APK #114 attempt 2 PASS | PRODUCTION FINALIZED |  |
| Torri | torri-compile-runner-20260922 | acb4ebcc7ac3fbcbffa7ee572d40c81ed60ede01 | Torri Compile Runner #97 success / 36684086037 | INCOMPLETE — NOT SIGNED AS FINAL | Final production-key APK acceptance missing. libimagedecoder.so in ARM64/x86_64 has 4096-byte ELF LOAD alignment; resolve and validate 16KB native compatibility. QA reader seeding also needs a compatible signer/data strategy. |
| Lyra | spotui-standalone-ci | 48ae9ad4c79c3655d02daa2ea98fe6908175bfa2 | Lyra Standalone Android CI #168 failure / 36999448456 | INCOMPLETE — NOT SIGNED AS FINAL | Current API36 live smoke times out waiting for Download Never Gonna Give You Up after queue navigation. API26 and permanent-release acceptance skipped. Existing key preserved; no production finalization. |
| Endless | endless-android-ci | 5e21f3fe13fe2e40a433402b6dbbddb954683948 | Endless Android CI #151 success / 36998925802 | PRODUCTION FINALIZED |  |
| Veya | veya-android-ci | e0fd669babd4cbe49a78d6b7a4e62cb4a6c100da | Veya Android CI #41 success / 36999751796 | INCOMPLETE — NOT SIGNED AS FINAL | Ready for signing finalization, but current runtime binary is QA-signed. Action-time confirmation needed to install existing Veya credentials as Actions secrets; then rerun exact permanently signed acceptance. |
| Cubic | cubic-android-ci | 0ac0b89539edea2783c61ed19ed4be4053f99b6d | Cubic Android CI #56 success / 36939892520 | INCOMPLETE — NOT SIGNED AS FINAL | Current signed APK uses original cfd16302…; prior rotated production APK uses fbe56623…. Workflow retrieves signing credentials from Actions artifact 11082919418 when secrets absent. Certificate continuity and confidential key custody unresolved; no further rotation authorized. |
| Yomi | yomi-reader-foundation | 2ba23c1ff49e1e45eba5c823b49000956fd95e3e | Yomi Reader CI #75 success / 36648499150 | INCOMPLETE — NOT SIGNED AS FINAL | Runtime acceptance used app.yomi.reader.dev; standalone production-package/signature workflow has not run. No recoverable permanent key was found. Do not replace an unknown configured key. |
| Slumber | slumber-087-build | dc9a2e4c911246b82598120ffb4a8d60672f24e6 | Slumber Icon Visual Preview #6 success / 36733142086 | INCOMPLETE — NOT SIGNED AS FINAL | Current head has icon-preview CI only. Full product and signed release acceptance must be repeated at current head, including reconstructed source/branding. |
| Annie | annie-android-ci | c6faa45dc045c78abac9c37c493e549569ace758 | Annie Android CI #498 failure / 37001949088 | INCOMPLETE — NOT SIGNED AS FINAL | Current #498 fails API26 full product acceptance; permanently signed API26/API36 install jobs fail SDK setup and validated-production-release is skipped. Not finalized. |
| MISE / Artist Scene Studio | artist-scene-studio-foundation | 81471daa6b4598e9abc8c39e9f871c71e6ecc151 | Artist Scene Studio Android CI #204 failure / 37001624197 | INCOMPLETE — NOT SIGNED AS FINAL | Current #204 passes unit/build and release install smoke, but fails actual renderer/runtime on both APIs. Active posing/renderer work remains incomplete. |
| Later | later-editor-qa | ecb5328d892c437541580219f32ec3547a9a5ef9 | Later editor QA #195 failure / 36824012526 | INCOMPLETE — NOT SIGNED AS FINAL | API 36 editor validation fails. Source snapshot #19 is green but is not product acceptance. |
| Mira | mira/production-foundation | e021f7477c16e4d7dba8a305a57de2e1ba021ff6 | Mira Android #19 failure / 36923222261 | INCOMPLETE — NOT SIGNED AS FINAL | API 26/API 36 startup plus coexistence/extension ABI jobs fail; real source/download job alone is insufficient. |
| AOD foundation | aod-android-foundation | 10e3690c619fc918019bc7a8b7ef7d8276576654 | AOD Android CI #23 success / 36998776838 | PRODUCTION FINALIZED |  |
| Shared YouTube Engine | youtube-engine-foundation | 034313d0c0e9219bfee7c5eefc101508fb74eec5 | YouTube Engine Foundation #127 success / 36699708761 | NON-ANDROID / NO APK SIGNING REQUIRED | README explicitly retains implementation limitations. Preserve this tested library checkpoint without claiming the entire future engine scope is complete. |
| Bailey Host | bailey-host-ci | 5a2bb41387a0cc34dd5a694f40d308982bd7b3c0 | Bailey Host CI #149 success / 35863851830 | NON-ANDROID / NO APK SIGNING REQUIRED | Electron desktop product. Current packaged tests are green, but full production completion and Windows distribution/signing custody were not certified in this audit. |
| YUWAI | yuwai-ci-audit | 27dfe10a426149247952c8dd467539463456ca64 | YUWAI mobile-first audit #5 success / 34908595739 | NON-ANDROID / NO APK SIGNING REQUIRED | Web audit success is not a full product release certification; no Android package exists. |
| Relay | relay-integration-hardening | 9e1be389e53846ca7eb03d1e1c1cadad018fa685 | Relay UI build #10 failure / 36734720823 | INCOMPLETE — NOT SIGNED AS FINAL | Integration build fails; agent branches are duplicate development work, not separate products. |
| Keyboard | keyboard-ci | 239eb262e755aac566e42c133bd127559635341c | Keyboard Android CI #91 failure / 35548443001 | INCOMPLETE — NOT SIGNED AS FINAL | Latest Android CI failed; root app module is experimental/development work. |
| Homira calls | homira-call-ci | 95d19cd915549f95c47616fb8ddb6e9279371a11 | Homira UI Smoke #115 failure / 35910233316 | INCOMPLETE — NOT SIGNED AS FINAL | A same-head UI failure overrides concurrent green packaging/network jobs. |
| Aether | aether-android-ci | e40abbdec07425488fa1a1b50fc3f9759fb88575 | Aether Android #9 success / 36077598562 | INCOMPLETE — NOT SIGNED AS FINAL | No complete API 26/API 36 production core-workflow, persistence, signed install, or owner-key proof. |
| PaheBatcher | pahebatcher-android-ci | 837fdb22c34fef4d0a16021ece0b8cd89ced0f65 | PaheBatcher Android #171 success / 35441911381 | INCOMPLETE — NOT SIGNED AS FINAL | A green build/visual smoke does not establish complete dual-API production acceptance or permanent identity. |
| Cobalt / WhatsApp client | whatsapp-client | adf7b91d4aeb4ecb71e1e6dbf10bf1ee7aad5cd8 | WhatsApp Client Android #42 success / 35708578717 | INCOMPLETE — NOT SIGNED AS FINAL | PoC/upstream compatibility branches are excluded from production apps; no full accepted independent product release established. |
| Extension Chat | extension-chat-render35-ci | cdb30925a2c5d81e9019e6972dd393518a7daf6f | Extension Chat Android UI #53 success / 35378264158 | INCOMPLETE — NOT SIGNED AS FINAL | UI experiment; no full accepted production application proof. |
| Night / NightMods historical experiments | nightmods-core-integration | 056608b059fdf4e12cae29659a9ef1afb4c58b31 | Night Mods Android CI #106 failure / 35065920847 | INCOMPLETE — NOT SIGNED AS FINAL | Excluded historical experiment family; dozens of branches are not separate portfolio applications. |
| Secure rendering lab | secure-rendering-lab | b16f37f9fc2c0d41c423293a35bda6860ff8f220 | Secure Rendering System Emulator Probe #6 success / 35769335410 | INCOMPLETE — NOT SIGNED AS FINAL | Rendering test fixture; excluded from portfolio apps and signing identities. |
| Liminal / portfolio / base web snapshots | liminal-engine-ci-20260910 | 54139b50f29b41e52e557a9650fe3e0cc7198605 | NO CURRENT PRODUCT ACCEPTANCE | NON-ANDROID / NO APK SIGNING REQUIRED | Static/demo snapshots do not establish finished independent production products. |
| MSCC / source/API probe tooling | mscc-azure | 139721db1ee021261b6239cb96324cd82fc9cb6f | MSCC Azure #558 success / 37029414896 | NON-ANDROID / NO APK SIGNING REQUIRED | These are server/script capabilities and experiments, not APK products. Broader completion is not inferred from individual probe successes. |
| Elementum (external repository) | elementum | 997ae431f4db5264d7b8b7f2df6de212f0b102cd (Tomex777/FallingSandJava) | Elementum Android build #188 success / 36824708142 | INCOMPLETE — NOT SIGNED AS FINAL | Not present in any Build branch. External repo head 997ae431f4db5264d7b8b7f2df6de212f0b102cd; no production release/permanent-signed dual-API evidence from latest workflow. |
| Sora historical media experiments | sora-media-ci | 6b1a226e706163aa53e2d35585f4312a76b7f1e7 | Sora Anime Manga Media CI #66 success / 36135496675 | INCOMPLETE — NOT SIGNED AS FINAL | Historical media prototype (0.1.0/1); compilation/smoke is not an accepted independent completed product. Duplicate Sora branches grouped; no final key created. |

## Verification and custody

APK bytes were independently verified using Android apksigner; APK hashes were compared with runtime-tested hashes where recorded. AAB PKCS7 signatures, whole-manifest hashes and all signed-entry hashes were independently verified. ARM64 native ELF LOAD alignment was inspected and passed 16KiB checks for finalized outputs. Both API runtime sets include visible application screenshots. No physical Galaxy A16/ARM64 device was available; do not interpret emulator proof as a physical-device test.

Existing unique production keys were preserved. Every finalized key was recovered privately and opened successfully with a certificate matching its release APK. Nami uses its encrypted owner archive and separate recovery file. Cortex/MirrorChess use the per-app keys and credentials owner archives. Endless uses the dedicated newer archive (the old shared-archive key is obsolete). AOD uses its PKCS12 and separate recovery file; its existing certificate expires in 2054 and was preserved. Private material is absent from all public release packages and Git.

MirrorChess #114 attempt 1 encountered repeated emulator ANR overlays and failed resume navigation; attempt 2 passed the unchanged exact branch build. Latest #115 also passed with an identical source tree and identical final APK hash. Endless #150 had a release-workflow YAML error introduced during packaging; corrected current #151 passes. Veya #40 API26 failed under an unrelated Google Messages RCS crash dialog; the fixture was isolated and #41 passes both APIs, but uses QA signing. Lyra #168 still fails live workflow acceptance, and no signing success is claimed.

This is a dated audit of moving branches. Annie #498 and MISE #204 have current failures and remain excluded. Recheck HEAD and every relevant current workflow before any later release. No general project is certified merely because one build succeeded.
