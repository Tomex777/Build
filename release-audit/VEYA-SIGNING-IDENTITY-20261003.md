# Veya production release — 2026-10-03

Status: **PRODUCTION FINALIZED**.

Application: Veya. Package: `com.veya.app`. Version: 1.0.0 (1).

Branch: `veya-android-ci`. Final commit: `132fa342fbb9df81e3956aa5a1a29d7c2a2c4ac8`.

Latest acceptance: [Veya Android CI #49](https://github.com/Tomex777/Build/actions/runs/37138151310) — production build, unit tests/lint, API26 and API36 PASS.

Minimum API 26; target/compile API 36; R8/minified release. Application label: Veya. Exact manifest is non-debuggable and not test-only.

Production signer SHA-256: `2e7bb19e87af53038c8b696e472d1008b7197c8ea3dac93cf3e046bc52f4ad84`.

Signer SHA-1: `be156abd5396311493e938e4ccaa3589dfed2969`.

Alias: `veya-release`; JKS; RSA4096. Certificate validity: 2026-10-01T06:15:50Z through 2126-09-07T06:15:50Z. Existing identity preserved and protected CI configured on 2026-10-03.

## Final artifacts

| Artifact | SHA-256 |
|---|---|
| `Veya-1.0.0-arm64-release-production-signed.apk` | `7dbb77f63b1ec5a2c2f41bb0aebd36bb61211eea15b912fdc127728acdfb3dab` |
| `Veya-1.0.0-universal-release-production-signed.aab` | `1f3c4f2beeaa0b284319bf4f4dc3ffde6281a2bd461ada39136fdca07538bd7b` |
| `Veya-1.0.0-universal-release-production-signed.apk` | `2a1a93255289ef940edbcdc75fd1aea24d232c596b35bf7aebb8d44f89f44cbd` |

Both APKs independently pass Android apksigner verification with v2/v3 signing and the same permanent certificate. The AAB independently passes CMS signature, whole-manifest and every signed-entry SHA-256 verification. ARM64-only APK contains actual ARM64 ELF libraries; universal supports arm64-v8a, armeabi-v7a, x86 and x86_64. ARM64/x86_64 native ELF LOAD and APK ZIP alignment pass 16KB checks.

## Runtime acceptance

The final universal APK hash `2a1a93255289ef940edbcdc75fd1aea24d232c596b35bf7aebb8d44f89f44cbd` matches the APK installed by both current runtime jobs. API26 and API36 pass launch, primary navigation, live adaptive video/audio, English captions, completed download, offline playback after process restart, background/foreground and same-certificate reinstall with preserved download playback. Final screenshots were visually inspected and show actual video after reinstall. Home/About retain accepted branding without developer badges or placeholder copy.

No prior distributed permanent build was available for a cross-version upgrade test. The same signed APK was reinstalled with retained data. Tests used x86_64 emulators; physical ARM64 installation was not performed.

## Permanent custody and future updates

Permanent signing: **CONFIGURED AND VERIFIED**. Owner recovery: **VERIFIED**. `Veya-Permanent-Signing-Backup.enc` and private `Veya-Owner-Recovery-PRIVATE.txt` were saved separately; independent recovery opened the existing keystore and reproduced this certificate. These private files are excluded from this public release package and Git.

Protected secret names: `VEYA_RELEASE_KEYSTORE_BASE64`, `VEYA_RELEASE_STORE_PASSWORD`, `VEYA_RELEASE_KEY_ALIAS`, `VEYA_RELEASE_KEY_PASSWORD`. Partial/invalid configuration fails closed; no debug identity is accepted as production. Runner signing files are temporary.

For future updates, recover the existing key, configure these protected secrets, preserve `com.veya.app` and this certificate, increase versionCode above every distributed version, and rerun current build plus both API acceptance jobs. Retain the new checksums and R8 mapping. Do not regenerate the application key.

## Release changes

A real API36 offline-resume defect was exposed during final acceptance. The correction initializes each VLC media once and waits for video decoder readiness before restoring playback position. No framework, engine, package, approved UI or feature architecture was replaced. The shared YouTube engine remains pinned to `034313d0c0e9219bfee7c5eefc101508fb74eec5`.
