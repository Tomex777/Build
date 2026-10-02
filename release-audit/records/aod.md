# AOD production release

Package: `com.homira.aod`

Branch: `aod-android-foundation`

Final source commit: `10e3690c619fc918019bc7a8b7ef7d8276576654`

Version: 0.1.0 (1)

Production signer SHA-256: `e5f2150cafd50317eb44cdd7fc7f7ee6e263abd282da99e8a71041c488674594`

API 26: PASS. API 36: PASS. Permanently signed universal APK install/runtime: PASS.

Acceptance: https://github.com/Tomex777/Build/actions/runs/36998776838 (attempt 1).

The original application architecture, navigation and branding are preserved. Finalization changes only release packaging, signing checks and emulator fixtures.

No native libraries; universal APK is architecture-independent and ARM64-compatible. No physical-device ambient/battery validation performed.

Owner recovery was verified against the existing private backup: AOD-production-signing-key.p12 + AOD-signing-recovery.txt. Private keys/passwords are deliberately absent from this release package and Git. Preserve those separate owner files offline. Do not generate a replacement key.

Future updates must retain `com.homira.aod`, this certificate and a versionCode greater than 1. Restore the listed per-app Actions secrets from the private owner backup, run current acceptance, and independently compare the signer to this record. Invalid/partial production credentials must fail closed; unsigned/QA candidates are not production releases.

Protected secret names:

- `AOD_RELEASE_KEYSTORE_B64`
- `AOD_RELEASE_STORE_PASSWORD`
- `AOD_RELEASE_KEY_ALIAS`
- `AOD_RELEASE_KEY_PASSWORD`

Artifacts and SHA-256:

- `AOD-production-universal.apk`: `ae3f39d0018c7ccd58f591316a64595e289272811b8594cf2d92fb498ac9ebea`
- `app-release.aab`: `b367e15ffe51c77c3c8c4fbb29483e18cf5cd4edaac47c9ef5b59b29e9772c38`
