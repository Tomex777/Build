# Nami production release

Package: `app.nami.android`

Branch: `nami/standalone-foundation`

Final source commit: `276e2e33adb673ee23880b6d2935b9703160c85f`

Version: 1.0.0 (1)

Production signer SHA-256: `a3aa7cafd99921b750d2535053ef3d493e3749a5cc92ee5d29b059ba4ee7995a`

API 26: PASS. API 36: PASS. Permanently signed universal APK install/runtime: PASS.

Acceptance: https://github.com/Tomex777/Build/actions/runs/36974075213 (attempt 1).

The original application architecture, navigation and branding are preserved. Finalization changes only release packaging, signing checks and emulator fixtures.

ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

Owner recovery was verified against the existing private backup: Nami-Permanent-Signing-Encrypted-Owner-Backup.zip + Nami-Owner-Backup-Recovery-PRIVATE.txt. Private keys/passwords are deliberately absent from this release package and Git. Preserve those separate owner files offline. Do not generate a replacement key.

Future updates must retain `app.nami.android`, this certificate and a versionCode greater than 1. Restore the listed per-app Actions secrets from the private owner backup, run current acceptance, and independently compare the signer to this record. Invalid/partial production credentials must fail closed; unsigned/QA candidates are not production releases.

Protected secret names:

- `NAMI_RELEASE_KEYSTORE_BASE64`
- `NAMI_RELEASE_STORE_PASSWORD`
- `NAMI_RELEASE_KEY_ALIAS`
- `NAMI_RELEASE_KEY_PASSWORD`

Artifacts and SHA-256:

- `nami-arm64-v8a-release-ci-signed.apk`: `c379beacfd26a0c40389567d313f72c88beaec2f0ab262247b946bcbec1492f0`
- `nami-universal-release-ci-signed.apk`: `6b263bcf971a7f63438a60caacb3d4007246d7f7adf867d26798b75d853bc83b`
- `nami-release-ci-signed.aab`: `5cfb22f64c8d7a94a6942f1b7bf97fefbb59f4282dcd423b5241a00da6906174`
