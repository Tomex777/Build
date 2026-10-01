# Production release audit — checkpoint, not final certification

Repository: Tomex777/Build. This checkpoint inventories all 146 remote branches, groups duplicate/experimental branches, and reviews current canonical CI plus release/signing evidence for viable products. No application is marked PRODUCTION FINALIZED in this checkpoint. Detailed acceptance work remains blocked where recorded below; this is not a claim that every feature in every experiment was audited.

## Material findings

- Annie #494, MISE #203, Later editor QA #195 and Mira #19 fail current runtime/product acceptance. They were not signed as final and their source was left unchanged.
- Veya's saved ARM64 “production-signed” APK fails Android apksigner: malformed v2 signature records. Its original owner certificate is present in that malformed file. Corrected ARM64/universal signing candidates were produced from CI #39 unsigned inputs using that same owner key. The branch subsequently moved and had newer failed signing-bootstrap CI; those candidates are historical, not current-head finalized outputs.
- Endless's published production signer is 4379c791a42d92e349dca2e573ca69c90a25ad812609f348dfac464c12162b9d. It matches the current branch pin and independently verifies. An earlier multi-app backup contains a different reserved key, 7541d811a2628728ca9e9f1eb634e71aa67829e9b57a04d82189bbee6886bb45. Do not use that earlier key for Endless updates.
- Torri libimagedecoder.so has 4096-byte ELF LOAD segment alignment in both ARM64 and x86_64 builds. ZIP 16KB alignment passes, but native ELF alignment must be resolved/validated before final release. Other inspected candidates passed the 16KB ZIP and 64-bit ELF alignment checks.
- Cortex CI #213 used QA/debug signing for release acceptance. API 36 release home.png and host home capture are all black. Separate app-side Pairing/Settings Compose captures are nonblack; they do not prove the final permanently signed release screen.
- Cubic #56 now passed, but Current signed APK uses original cfd16302…; prior rotated production APK uses fbe56623…. Workflow retrieves signing credentials from Actions artifact 11082919418 when secrets absent. Certificate continuity and confidential key custody unresolved; no further rotation authorized.
- Yomi's accepted runtime APK is app.yomi.reader.dev; the production-package candidate is app.yomi.reader. Production-package acceptance is still required.
- Nami and Yomi have permanent-signing workflows but no executed production workflow or recoverable owner identity found in the inspected evidence. The connection cannot inspect existing secret custody; no replacement keys were generated.

## Work completed

Existing private backups were recovered privately and eleven distinct RSA-4096 certificates were verified with keytool. No new keystores were generated. No private material was committed or uploaded as a public artifact. Existing package IDs and version codes were preserved.

From immutable accepted CI unsigned inputs, sixteen APK/AAB signing candidates were prepared for Veya, Cortex, MirrorChess, Torri, Lyra and Endless. Every APK passes Android apksigner verification; all six AABs pass jarsigner and their certificates are readable by keytool. ARM64 and universal pairs for Veya, MirrorChess and Torri share their app-specific signer; Cortex has ARM64 and x86_64 outputs plus AAB. No unsupported ARM64 or universal output was invented for Lyra/Endless/Cortex. These outputs are reproducible staging work, not distribution-ready artifacts, because the exact signed binaries were not install-tested.

Public release records include package/version metadata, certificate fingerprints, existing CI secret names, input hashes, and candidate checksums. Owner tools refuse a mismatching signer and changed unsigned input. The current-acceptance gate rejects moved heads, newer failures, unfinished reruns and unrelated green source-snapshot workflows. Five negative/positive gate tests pass; the owner signing utility was exercised end-to-end against Cortex APKs/AAB with independent verification.

## What blocks final release

The current GitHub connection supports repository and Actions evidence operations but cannot set protected Actions secrets or dispatch new release workflows. No Android runtime/device is available in this workspace. Consequently final production-key API 26/API 36 install/core-workflow/visual/upgrade acceptance has not run for these new candidates. A GitHub browser fallback requires the user's approval under the browser-access tool instructions.

Do not substitute an old QA/debug install pass for a final owner-signed install pass. Do not substitute Cortex's black screenshot for visual acceptance. Do not replace Cubic's existing key if its recovery material is unavailable.

## Owner recovery and CI configuration

Use existing private owner backups; this public audit deliberately contains no passwords or keystores. The multi-app key and credential archives already present in the owner's private files provide the six candidate identities except Endless, which must use its separate newer private backup. The older Endless entry is superseded. Keep multiple secure owner-controlled copies. Cubic additionally needs the private key corresponding to its encrypted-backup recipient certificate.

On an owner-authenticated computer with Java and GitHub CLI, read RELEASE_STORE_PASSWORD and RELEASE_KEY_PASSWORD from the private backup into environment variables without echoing them. Run:

```bash
python3 release-audit/configure_owner_secrets.py --app cortex --keystore /private/path/cortex-release.jks
```

Repeat only for qualified products and use the exact recovered per-app keystore. The script verifies the pinned certificate before writing any secret, passes secret values through subprocess stdin, and prints names only. It does not generate or rotate keys. Refer to release-identities.json for each app's exact existing secret names; naming conventions differ across existing workflows and must be preserved.

Before signing another candidate, run the current-head gate:

```bash
python3 release-audit/check_current_acceptance.py --app cortex
```

Then download the recorded unsigned artifact from the recorded accepted run and use the owner signing helper:

```bash
python3 release-audit/sign_accepted_inputs.py --app cortex --keystore /private/path/cortex-release.jks --inputs /private/unsigned-inputs --output /private/new-candidates --build-tools /android/sdk/build-tools/35.0.0
```

The helper checks the exact audited input hashes, pinned certificate, final signature, package/version/SDK metadata, release manifest, and AAB certificate. Its output is deliberately marked CANDIDATE and install_test=NOT RUN. Veya currently refuses reproduction because its branch moved after the accepted inputs.

Once secrets and release access exist, use canonical project CI to rebuild and test the actual owner-signed outputs. Existing runtime jobs must be checked for QA-only routing: Veya, Torri, MirrorChess, Lyra and Endless previously tested QA release copies even when owner signing was optional. Torri's deterministic reader seed must use a compatible signing/data strategy; a QA-key seed cannot be upgraded directly by an owner-key release. Cortex final visual testing needs a nonblack rendered release capture. Test ARM64 on an ARM64 runtime; x86_64 emulator acceptance alone does not prove Galaxy A16 compatibility. Preserve original/version-upgrade metadata and publish only after final acceptance.

## Matrix

The full machine-readable matrix is RELEASE-MATRIX.csv and completion-matrix.json. API pass entries below describe source/QA CI unless explicitly stated; they are never substituted for testing newly signed outputs.

| Project | Branch | Current HEAD | Latest relevant CI | Final result | Blocker |
| --- | --- | --- | --- | --- | --- |
| Nami | nami/standalone-foundation | 09ff87e27c2163131ef555eea557761bbda74bdf | Nami Android #471 SUCCESS / 36798872564 | COMPLETE IMPLEMENTATION — FINALIZATION BLOCKED | No production workflow execution or recoverable owner key was found. Existing secrets cannot be inspected with this connection. Do not generate a replacement blindly. |
| Cortex | cortex-android-live | f4a51a407d998801057a605f62afdf4147a9e9fc | Cortex Android #213 SUCCESS / 36830717377 | COMPLETE IMPLEMENTATION — FINALIZATION BLOCKED | Final owner-signed API 26/API 36 acceptance missing. API 36 host release screenshot is black; separate Compose screenshots cannot substitute for final release visual proof. |
| MirrorChess | mirrorchess-sdk36-min26-20260922 | ab28bb0361de95be2b389bdc820b0562d56316d7 | MirrorChess #111 SUCCESS / 36603534779 | COMPLETE IMPLEMENTATION — FINALIZATION BLOCKED | Exact permanently signed ARM64/universal outputs have not been install-tested. Configure protected secrets and run signed release acceptance. |
| Torri | torri-compile-runner-20260922 | acb4ebcc7ac3fbcbffa7ee572d40c81ed60ede01 | Torri #97 SUCCESS / 36684086037 | COMPLETE IMPLEMENTATION — FINALIZATION BLOCKED | Final production-key APK acceptance missing. libimagedecoder.so in ARM64/x86_64 has 4096-byte ELF LOAD alignment; resolve and validate 16KB native compatibility. QA reader seeding also needs a compatible signer/data strategy. |
| Lyra | spotui-standalone-ci | 4853de86c67abe3ca815c76703837e059abc4468 | Lyra #166 SUCCESS / 36752420897 | COMPLETE IMPLEMENTATION — FINALIZATION BLOCKED | Final owner-signed app/source-extension workflow acceptance missing. Only universal unsigned app input was available; no ARM64 production output was manufactured. |
| Endless | endless-android-ci | 12ff5ed1762a2f9e53a85368361e1d2fe8bcc951 | Endless #148 SUCCESS / 36903683761 | COMPLETE IMPLEMENTATION — FINALIZATION BLOCKED | Current CI reports productionSigning=NOT_CONFIGURED. Final signed current-head binary has not been install-tested. The older multi-app reserved key differs and must not be used. |
| Veya | veya-android-ci | 6c3837852eb03de2862ca5737583d6d8b112e364 | Product #39 at old head; newer signing bootstrap #3 FAILURE / 36939219456 | INCOMPLETE — NOT SIGNED AS FINAL | Branch moved to 6c383785 after #39. Saved ARM64 production APK failed apksigner with malformed v2 records. Corrected candidate uses the original certificate but needs current-head acceptance and signed install tests. |
| Cubic | cubic-android-ci | 0ac0b89539edea2783c61ed19ed4be4053f99b6d | Cubic #56 SUCCESS / 36939892520 | INCOMPLETE — NOT SIGNED AS FINAL | Current signed APK uses original cfd16302…; prior rotated production APK uses fbe56623…. Workflow retrieves signing credentials from Actions artifact 11082919418 when secrets absent. Certificate continuity and confidential key custody unresolved; no further rotation authorized. |
| Yomi | yomi-reader-foundation | 2ba23c1ff49e1e45eba5c823b49000956fd95e3e | Yomi #75 SUCCESS / 36648499150 | INCOMPLETE — NOT SIGNED AS FINAL | Runtime acceptance used app.yomi.reader.dev; standalone production-package/signature workflow has not run. No recoverable permanent key was found. Do not replace an unknown configured key. |
| Slumber | slumber-087-build | dc9a2e4c911246b82598120ffb4a8d60672f24e6 | Latest icon preview #6; Product #65 at older head | INCOMPLETE — NOT SIGNED AS FINAL | Current head has icon-preview CI only. Full product and signed release acceptance must be repeated at current head, including reconstructed source/branding. |
| Annie | annie-android-ci | f773e9c4ef503d5aa99a110cd8a64cf46c886f2a | Annie #494 FAILURE / 36926290734 | INCOMPLETE — NOT SIGNED AS FINAL | Current runtime/product acceptance fails; validated-production-release skipped. |
| MISE / Artist Scene Studio | artist-scene-studio-foundation | 63e64bcfe8fda1d03e1ef5a4b3c02bdb100acf62 | MISE #203 FAILURE / 36925310468 | INCOMPLETE — NOT SIGNED AS FINAL | Renderer/runtime tests still fail on both API levels despite build and release-install smoke passing. |
| Later | later-editor-qa | ecb5328d892c437541580219f32ec3547a9a5ef9 | Later editor QA #195 FAILURE / 36824012526 | INCOMPLETE — NOT SIGNED AS FINAL | API 36 editor validation fails. Source snapshot #19 is green but is not product acceptance. |
| Mira | mira/production-foundation | e021f7477c16e4d7dba8a305a57de2e1ba021ff6 | Mira #19 FAILURE / 36923222261 | INCOMPLETE — NOT SIGNED AS FINAL | API 26/API 36 startup plus coexistence/extension ABI jobs fail; real source/download job alone is insufficient. |
| AOD foundation | aod-android-foundation | 3401a4c3dfe3d622fce80a769cc70ac4681b8137 | AOD #3 IN PROGRESS / 36940088685 | INCOMPLETE — NOT SIGNED AS FINAL | New foundation branch; recent #1/#2 failed and #3 is active. No evidence of a completed production product. |
| Shared YouTube Engine | youtube-engine-foundation | 034313d0c0e9219bfee7c5eefc101508fb74eec5 | Engine #127 SUCCESS / 36699708761 | NON-ANDROID / NO APK SIGNING REQUIRED | README explicitly retains implementation limitations. Preserve this tested library checkpoint without claiming the entire future engine scope is complete. |
| Bailey Host | bailey-host-ci | 5a2bb41387a0cc34dd5a694f40d308982bd7b3c0 | Bailey Host #149 SUCCESS / 35863851830 | NON-ANDROID / NO APK SIGNING REQUIRED | Electron desktop product. Current packaged tests are green, but full production completion and Windows distribution/signing custody were not certified in this audit. |
| YUWAI | yuwai-ci-audit | 27dfe10a426149247952c8dd467539463456ca64 | YUWAI mobile audit #5 SUCCESS / 34908595739 | NON-ANDROID / NO APK SIGNING REQUIRED | Web audit success is not a full product release certification; no Android package exists. |
| Relay | relay-integration-hardening | 9e1be389e53846ca7eb03d1e1c1cadad018fa685 | Relay UI #10 FAILURE / 36734720823 | INCOMPLETE — NOT SIGNED AS FINAL | Integration build fails; agent branches are duplicate development work, not separate products. |
| Keyboard | keyboard-ci | 239eb262e755aac566e42c133bd127559635341c | Keyboard #91 FAILURE / 35548443001 | INCOMPLETE — NOT SIGNED AS FINAL | Latest Android CI failed; root app module is experimental/development work. |
| Homira calls | homira-call-ci | 95d19cd915549f95c47616fb8ddb6e9279371a11 | UI Smoke #115 FAILURE at same head; Android #291/TURN #123 green | INCOMPLETE — NOT SIGNED AS FINAL | A same-head UI failure overrides concurrent green packaging/network jobs. |
| Aether | aether-android-ci | e40abbdec07425488fa1a1b50fc3f9759fb88575 | Aether #9 SUCCESS / 36077598562 | INCOMPLETE — NOT SIGNED AS FINAL | No complete API 26/API 36 production core-workflow, persistence, signed install, or owner-key proof. |
| PaheBatcher | pahebatcher-android-ci | 837fdb22c34fef4d0a16021ece0b8cd89ced0f65 | PaheBatcher #171 SUCCESS / 35441911381 | INCOMPLETE — NOT SIGNED AS FINAL | A green build/visual smoke does not establish complete dual-API production acceptance or permanent identity. |
| Cobalt / WhatsApp client | whatsapp-client | adf7b91d4aeb4ecb71e1e6dbf10bf1ee7aad5cd8 | WhatsApp Client #42 SUCCESS / 35708578717 | INCOMPLETE — NOT SIGNED AS FINAL | PoC/upstream compatibility branches are excluded from production apps; no full accepted independent product release established. |
| Extension Chat | extension-chat-render35-ci | cdb30925a2c5d81e9019e6972dd393518a7daf6f | Extension Chat #53 SUCCESS / 35378264158 | INCOMPLETE — NOT SIGNED AS FINAL | UI experiment; no full accepted production application proof. |
| Night / NightMods historical experiments | nightmods-core-integration | 056608b059fdf4e12cae29659a9ef1afb4c58b31 | NightMods #106 FAILURE / 35065920847 | INCOMPLETE — NOT SIGNED AS FINAL | Excluded historical experiment family; dozens of branches are not separate portfolio applications. |
| Secure rendering lab | secure-rendering-lab | b16f37f9fc2c0d41c423293a35bda6860ff8f220 | NOT PRODUCT ACCEPTANCE | INCOMPLETE — NOT SIGNED AS FINAL | Rendering test fixture; excluded from portfolio apps and signing identities. |
| Liminal / portfolio / base web snapshots | liminal-engine-ci-20260910 | 54139b50f29b41e52e557a9650fe3e0cc7198605 | NO CURRENT PRODUCT ACCEPTANCE | NON-ANDROID / NO APK SIGNING REQUIRED | Static/demo snapshots do not establish finished independent production products. |
| MSCC / source/API probe tooling | mscc-azure | 279f7c1931cd18ba11f83cca1ee62bd6ae492ba0 | Multiple source-specific workflows; release readiness NOT CERTIFIED | NON-ANDROID / NO APK SIGNING REQUIRED | These are server/script capabilities and experiments, not APK products. Broader completion is not inferred from individual probe successes. |
| Elementum (external repository) | elementum | 997ae431f4db5264d7b8b7f2df6de212f0b102cd (Tomex777/FallingSandJava) | FallingSandJava #188 SUCCESS / 36824708142 | INCOMPLETE — NOT SIGNED AS FINAL | Not present in any Build branch. External repo head 997ae431f4db5264d7b8b7f2df6de212f0b102cd; no production release/permanent-signed dual-API evidence from latest workflow. |

## Per-app signing records

### Veya

Application ID: `com.veya.app`  
Branch: `veya-android-ci`  
Current branch head: `6c3837852eb03de2862ca5737583d6d8b112e364`  
Candidate source commit: `5e2c40598f98b5b4633cafee6d937cb29a506fc7`  
Version: 1.0.0 (1)  
Alias: `veya-release`  
Production-key certificate SHA-256: `2e7bb19e87af53038c8b696e472d1008b7197c8ea3dac93cf3e046bc52f4ad84`  
Certificate SHA-1: `be156abd5396311493e938e4ccaa3589dfed2969`  
Keystore format: JKS; RSA-4096  
Certificate validity (UTC): 2026-10-01T06:15:50+00:00 through 2126-09-07T06:15:50+00:00  
Identity established: 2026-10-01  
State: **SIGNED CANDIDATE — HISTORICAL SOURCE; BRANCH MOVED; CURRENT FINALIZATION BLOCKED**  
Final signed install test: **NOT RUN**  
Owner recovery: existing private backup recovered and verified; no new identity created.

CI secret names: `VEYA_RELEASE_KEYSTORE_BASE64`, `VEYA_RELEASE_STORE_PASSWORD`, `VEYA_RELEASE_KEY_ALIAS`, `VEYA_RELEASE_KEY_PASSWORD`.

Candidates: `Veya-1.0.0-arm64-release-production-key-candidate.apk`, `Veya-1.0.0-universal-release-production-key-candidate.apk`, `Veya-1.0.0-universal-release-production-key-candidate.aab`.

### Cortex

Application ID: `com.night.cortex`  
Branch: `cortex-android-live`  
Current branch head: `f4a51a407d998801057a605f62afdf4147a9e9fc`  
Candidate source commit: `f4a51a407d998801057a605f62afdf4147a9e9fc`  
Version: 1.0.0 (100)  
Alias: `cortex-release`  
Production-key certificate SHA-256: `47aaa57d156ec1d2812c2787e1e12ec4e05ac59694c576dc412ef3bae2d703e8`  
Certificate SHA-1: `b1cd1cf56a3e166d87b2b1c027fec0cfe863b292`  
Keystore format: JKS; RSA-4096  
Certificate validity (UTC): 2026-10-01T06:20:44+00:00 through 2126-09-07T06:20:44+00:00  
Identity established: 2026-10-01  
State: **SIGNED CANDIDATE — FINAL SIGNED INSTALL TEST NOT RUN**  
Final signed install test: **NOT RUN**  
Owner recovery: existing private backup recovered and verified; no new identity created.

CI secret names: `CORTEX_RELEASE_KEYSTORE_B64`, `CORTEX_RELEASE_STORE_PASSWORD`, `CORTEX_RELEASE_KEY_ALIAS`, `CORTEX_RELEASE_KEY_PASSWORD`.

Candidates: `Cortex_X86_64-production-key-candidate.apk`, `Cortex_ARM64-production-key-candidate.apk`, `Cortex-release-production-key-candidate.aab`.

### Mirrorchess

Application ID: `com.night.mirrorchess`  
Branch: `mirrorchess-sdk36-min26-20260922`  
Current branch head: `ab28bb0361de95be2b389bdc820b0562d56316d7`  
Candidate source commit: `ab28bb0361de95be2b389bdc820b0562d56316d7`  
Version: 1.2.0 (5)  
Alias: `mirrorchess-release`  
Production-key certificate SHA-256: `39a516df91001cd022408b2221fb434cb1283c6931595681e1a5c653c9b210f5`  
Certificate SHA-1: `04dcf8203bb2b10153dd104916a3049f360479c6`  
Keystore format: JKS; RSA-4096  
Certificate validity (UTC): 2026-10-01T06:15:57+00:00 through 2126-09-07T06:15:57+00:00  
Identity established: 2026-10-01  
State: **SIGNED CANDIDATE — FINAL SIGNED INSTALL TEST NOT RUN**  
Final signed install test: **NOT RUN**  
Owner recovery: existing private backup recovered and verified; no new identity created.

CI secret names: `MIRRORCHESS_RELEASE_KEYSTORE_B64`, `MIRRORCHESS_RELEASE_KEYSTORE_PASSWORD`, `MIRRORCHESS_RELEASE_KEY_ALIAS`, `MIRRORCHESS_RELEASE_KEY_PASSWORD`.

Candidates: `MirrorChess-1.2.0-arm64-v8a-release-production-key-candidate.apk`, `MirrorChess-1.2.0-release-production-key-candidate.aab`, `MirrorChess-1.2.0-universal-release-production-key-candidate.apk`.

### Torri

Application ID: `app.torri`  
Branch: `torri-compile-runner-20260922`  
Current branch head: `acb4ebcc7ac3fbcbffa7ee572d40c81ed60ede01`  
Candidate source commit: `acb4ebcc7ac3fbcbffa7ee572d40c81ed60ede01`  
Version: 1.0.0 (10000)  
Alias: `torri-release`  
Production-key certificate SHA-256: `6b1ab58169e3a8dd4370860d5d03060b035476b43cceaf2bd8da7ca1fe37ab44`  
Certificate SHA-1: `6355667eae5beeaab7d6699dd689cc84c4e34657`  
Keystore format: JKS; RSA-4096  
Certificate validity (UTC): 2026-10-01T06:15:59+00:00 through 2126-09-07T06:15:59+00:00  
Identity established: 2026-10-01  
State: **SIGNED CANDIDATE — FINAL SIGNED INSTALL TEST NOT RUN**  
Final signed install test: **NOT RUN**  
Owner recovery: existing private backup recovered and verified; no new identity created.

CI secret names: `TORRI_RELEASE_KEYSTORE_B64`, `TORRI_RELEASE_KEYSTORE_PASSWORD`, `TORRI_RELEASE_KEY_ALIAS`, `TORRI_RELEASE_KEY_PASSWORD`.

Candidates: `Torri-1.0.0-arm64-v8a-release-production-key-candidate.apk`, `Torri-1.0.0-universal-release-production-key-candidate.apk`, `Torri-1.0.0-production-key-candidate.aab`.

### Lyra

Application ID: `com.night.spotui`  
Branch: `spotui-standalone-ci`  
Current branch head: `4853de86c67abe3ca815c76703837e059abc4468`  
Candidate source commit: `4853de86c67abe3ca815c76703837e059abc4468`  
Version: 1.0.0 (1)  
Alias: `lyra-release`  
Production-key certificate SHA-256: `2e0d59d39b494b5e1d8eb7ae62c9fc7ad67bad2f7b59e62d1c072feb2457d700`  
Certificate SHA-1: `97f6c0afe1573c9e61f8526c8d28e163c553d951`  
Keystore format: JKS; RSA-4096  
Certificate validity (UTC): 2026-10-01T06:15:57+00:00 through 2126-09-07T06:15:57+00:00  
Identity established: 2026-10-01  
State: **SIGNED CANDIDATE — FINAL SIGNED INSTALL TEST NOT RUN**  
Final signed install test: **NOT RUN**  
Owner recovery: existing private backup recovered and verified; no new identity created.

CI secret names: `LYRA_RELEASE_KEYSTORE_B64`, `LYRA_RELEASE_KEYSTORE_PASSWORD`, `LYRA_RELEASE_KEY_ALIAS`, `LYRA_RELEASE_KEY_PASSWORD`.

Candidates: `Lyra-release-production-key-candidate.aab`, `Lyra-universal-release-production-key-candidate.apk`.

### Endless

Application ID: `com.night.endless`  
Branch: `endless-android-ci`  
Current branch head: `12ff5ed1762a2f9e53a85368361e1d2fe8bcc951`  
Candidate source commit: `12ff5ed1762a2f9e53a85368361e1d2fe8bcc951`  
Version: 1.0.0 (10)  
Alias: `endless-release`  
Production-key certificate SHA-256: `4379c791a42d92e349dca2e573ca69c90a25ad812609f348dfac464c12162b9d`  
Certificate SHA-1: `c1ff6b038c67c21d0ff5df70da66e8876edc665b`  
Keystore format: JKS; RSA-4096  
Certificate validity (UTC): 2026-10-01T17:53:22+00:00 through 2126-09-07T17:53:22+00:00  
Identity established: 2026-10-01  
State: **SIGNED CANDIDATE — FINAL SIGNED INSTALL TEST NOT RUN**  
Final signed install test: **NOT RUN**  
Owner recovery: existing private backup recovered and verified; no new identity created.

CI secret names: `ENDLESS_RELEASE_KEYSTORE_B64`, `ENDLESS_RELEASE_KEYSTORE_PASSWORD`, `ENDLESS_RELEASE_KEY_ALIAS`, `ENDLESS_RELEASE_KEY_PASSWORD`.

Use the separate newer Endless backup, never the superseded multi-app entry.

Candidates: `Endless-1.0.0-release-production-key-candidate.apk`, `Endless-1.0.0-release-production-key-candidate.aab`.


## Freshness and scope

This is a time-bounded checkpoint. Product branches are concurrently active; rerun the head/acceptance gate before any release. The branch inventory distinguishes source modules from encoded payloads and duplicate/experimental branches. Unfinished products received status records only; no tests or accepted app behavior were changed. Zero tracked .jks/.keystore/.p12/.pfx paths were found in the 146 current remote branch trees; this does not claim that all historical commits were scanned.
