# MirrorChess production release

Package: `com.night.mirrorchess`

Branch: `mirrorchess-sdk36-min26-20260922`

Final source commit: `3536d51e4c9b5de66fc1511225b49deac07b0f8a`

Version: 1.2.0 (5)

Production signer SHA-256: `39a516df91001cd022408b2221fb434cb1283c6931595681e1a5c653c9b210f5`

API 26: PASS. API 36: PASS. Permanently signed universal APK install/runtime: PASS.

Acceptance: https://github.com/Tomex777/Build/actions/runs/36998454972 (attempt 2).

The original application architecture, navigation and branding are preserved. Finalization changes only release packaging, signing checks and emulator fixtures.

ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

Owner recovery was verified against the existing private backup: Android-Permanent-Signing-Keys-20261001.zip + Android-Permanent-Signing-Credentials-20261001.zip. Private keys/passwords are deliberately absent from this release package and Git. Preserve those separate owner files offline. Do not generate a replacement key.

Future updates must retain `com.night.mirrorchess`, this certificate and a versionCode greater than 5. Restore the listed per-app Actions secrets from the private owner backup, run current acceptance, and independently compare the signer to this record. Invalid/partial production credentials must fail closed; unsigned/QA candidates are not production releases.

Protected secret names:

- `MIRRORCHESS_RELEASE_KEYSTORE_B64`
- `MIRRORCHESS_RELEASE_KEYSTORE_PASSWORD`
- `MIRRORCHESS_RELEASE_KEY_ALIAS`
- `MIRRORCHESS_RELEASE_KEY_PASSWORD`

Artifacts and SHA-256:

- `MirrorChess-1.2.0-arm64-v8a-release.apk`: `8ac72913de8a57cdbc5c00a0d2b1466c01d14c937917764dfacb4559d2d7349c`
- `MirrorChess-1.2.0-universal-release.apk`: `47293c5db6f25acfef7df58df4beebe562902e49b44303a14ffedc3af6bf4733`
- `MirrorChess-1.2.0-release.aab`: `c09bedf6f92b3a39e7248364aa05f3c6a02f669d4db8b0e0ba52b581eddc9d4a`
