# Cortex production release

Package: `com.night.cortex`

Branch: `cortex-android-live`

Final source commit: `f23bc464fe25ecab5fc772499ec2cf865740b95f`

Version: 1.0.0 (100)

Production signer SHA-256: `47aaa57d156ec1d2812c2787e1e12ec4e05ac59694c576dc412ef3bae2d703e8`

API 26: PASS. API 36: PASS. Permanently signed universal APK install/runtime: PASS.

Acceptance: https://github.com/Tomex777/Build/actions/runs/36976187255 (attempt 1).

The original application architecture, navigation and branding are preserved. Finalization changes only release packaging, signing checks and emulator fixtures.

ARM64-only output signature/ABI/16KiB ELF inspected; no physical ARM64 runtime test performed

Owner recovery was verified against the existing private backup: Android-Permanent-Signing-Keys-20261001.zip + Android-Permanent-Signing-Credentials-20261001.zip. Private keys/passwords are deliberately absent from this release package and Git. Preserve those separate owner files offline. Do not generate a replacement key.

Future updates must retain `com.night.cortex`, this certificate and a versionCode greater than 100. Restore the listed per-app Actions secrets from the private owner backup, run current acceptance, and independently compare the signer to this record. Invalid/partial production credentials must fail closed; unsigned/QA candidates are not production releases.

Protected secret names:

- `CORTEX_RELEASE_KEYSTORE_B64`
- `CORTEX_RELEASE_STORE_PASSWORD`
- `CORTEX_RELEASE_KEY_ALIAS`
- `CORTEX_RELEASE_KEY_PASSWORD`

Artifacts and SHA-256:

- `Cortex_ARM64_INSTALLABLE.apk`: `c06717b50f23122d5f99788c578dcad1f3018db4b701b4c6024be7473388d1ae`
- `Cortex_UNIVERSAL_INSTALLABLE.apk`: `ce73f56105047f0852175321090e2707854760321a145fbac75ff509b09748d4`
- `Cortex_RELEASE.aab`: `53f07be0861832ead01143bfd67733bca2cd149d1e2e9a14617ace0c1577bdec`
