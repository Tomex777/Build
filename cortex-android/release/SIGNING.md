# Cortex permanent release identity

Package: `com.night.cortex`. Version: 1.0.0 (100). Alias: `cortex-release`.

Certificate SHA-256: `47aaa57d156ec1d2812c2787e1e12ec4e05ac59694c576dc412ef3bae2d703e8`.

Established existing key preserved on 2026-10-02. Never generate a replacement for this application without an explicit certificate-continuity plan.

The existing private owner backups `Android-Permanent-Signing-Keys-20261001.zip` and `Android-Permanent-Signing-Credentials-20261001.zip` contain the Cortex keystore and recovery credentials. Keep secure offline copies independent of GitHub. These archives and their contents must never enter Git or public Actions artifacts.

Four encrypted GitHub Actions repository secrets were configured on 2026-10-02:

- `CORTEX_RELEASE_KEYSTORE_B64`
- `CORTEX_RELEASE_STORE_PASSWORD`
- `CORTEX_RELEASE_KEY_ALIAS`
- `CORTEX_RELEASE_KEY_PASSWORD`

CI pins the certificate, rejects partial/invalid credentials, removes its temporary keystore, verifies APKs and AAB, and installs the exact packaged x86_64 release on API 26 and API 36. ARM64, universal APK, AAB, mapping, metadata and checksums are packaged together. The x86_64 artifact supports emulator acceptance; ARM64 real-device acceptance must be recorded separately where available.

Status: signing secrets configured; final signed-binary acceptance pending. No production-finalized claim until latest current-head CI and usable release screenshots pass.
