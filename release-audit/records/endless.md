# Endless production release

Package: `com.night.endless`

Branch: `endless-android-ci`

Final source commit: `5e21f3fe13fe2e40a433402b6dbbddb954683948`

Version: 1.0.0 (10)

Production signer SHA-256: `4379c791a42d92e349dca2e573ca69c90a25ad812609f348dfac464c12162b9d`

API 26: PASS. API 36: PASS. Permanently signed universal APK install/runtime: PASS.

Acceptance: https://github.com/Tomex777/Build/actions/runs/36998925802 (attempt 1).

The original application architecture, navigation and branding are preserved. Finalization changes only release packaging, signing checks and emulator fixtures.

ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

Owner recovery was verified against the existing private backup: Endless-Permanent-Signing-Key-PRIVATE.zip; the older shared-archive Endless identity is obsolete. Private keys/passwords are deliberately absent from this release package and Git. Preserve those separate owner files offline. Do not generate a replacement key.

Future updates must retain `com.night.endless`, this certificate and a versionCode greater than 10. Restore the listed per-app Actions secrets from the private owner backup, run current acceptance, and independently compare the signer to this record. Invalid/partial production credentials must fail closed; unsigned/QA candidates are not production releases.

Protected secret names:

- `ENDLESS_RELEASE_KEYSTORE_B64`
- `ENDLESS_RELEASE_KEYSTORE_PASSWORD`
- `ENDLESS_RELEASE_KEY_ALIAS`
- `ENDLESS_RELEASE_KEY_PASSWORD`

Artifacts and SHA-256:

- `Endless-1.0.0-arm64-production-signed.apk`: `c2ced7d44f62ae34446b87df310f2cd065f813dc2e5faf88393da8b66b8425ef`
- `Endless-1.0.0-release-production-signed.apk`: `980462bfbe80370aee94d506e47bb5c6229e6c0137f4b4bc4c779040edb7fa3d`
- `Endless-1.0.0-release-production-signed.aab`: `ce1200eaf571cd9ffe54bad897413d25c43c2d9c8cf37f3c309d05b8a0b0f6ae`
