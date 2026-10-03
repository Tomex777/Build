# Veya permanent production signing

Package: `com.veya.app`. Release version: 1.0.0 (1). Minimum API 26; target/compile API 36.

The existing application identity was preserved on 2026-10-03. No replacement key was generated.

- Alias: `veya-release`; format: JKS; RSA 4096 bits.
- Certificate SHA-256: `2e7bb19e87af53038c8b696e472d1008b7197c8ea3dac93cf3e046bc52f4ad84`.
- Certificate SHA-1: `be156abd5396311493e938e4ccaa3589dfed2969`.
- Certificate validity: 2026-10-01T06:15:50Z through 2126-09-07T06:15:50Z.

Protected Actions secret names:

- `VEYA_RELEASE_KEYSTORE_BASE64`
- `VEYA_RELEASE_STORE_PASSWORD`
- `VEYA_RELEASE_KEY_ALIAS`
- `VEYA_RELEASE_KEY_PASSWORD`

The workflow temporarily reconstructs the existing keystore, verifies the expected certificate, signs the ARM64 and universal APKs and AAB, then removes the runner keystore. Partial or invalid signing configuration fails closed. Missing secrets produce explicitly identified QA candidates, never a production claim.

`Veya-1.0.0-production-release` contains production APK/AAB files, checksums, signature reports, native alignment evidence and the R8 mapping. API26 and API36 acceptance install the actual signed universal APK and record its SHA-256. Shipping requires both current runtime jobs to pass and their tested hashes to match the delivered APK.

The separate encrypted owner backup `Veya-Permanent-Signing-Backup.enc` and private recovery instructions `Veya-Owner-Recovery-PRIVATE.txt` were independently restored and matched this certificate. Keep the recovery instructions privately and separately from the encrypted backup. Neither file, nor the keystore or passwords, belongs in Git or public releases.

For future releases, restore this identity into protected secrets; preserve the package and certificate; increase versionCode above every distributed build; run current build and dual-API acceptance; retain checksums and R8 mapping. Do not regenerate the key. Exact final commits, CI evidence and deliverable hashes are recorded on `release/production-audit-20261002`.
