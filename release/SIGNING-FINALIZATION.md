# Endless release validation and owner custody

Permanent current signer SHA-256: `4379c791a42d92e349dca2e573ca69c90a25ad812609f348dfac464c12162b9d`. Alias `endless-release`; package `com.night.endless`, version 1.0.0 (10).

Use the current privately saved `Endless-Permanent-Signing-Key-PRIVATE.zip` for owner recovery. The older shared archive contains a superseded Endless candidate key; do not restore it. Keep an independent offline copy of the current backup.
Protected secrets: `ENDLESS_RELEASE_KEYSTORE_B64`, `ENDLESS_RELEASE_KEYSTORE_PASSWORD`, `ENDLESS_RELEASE_KEY_ALIAS`, `ENDLESS_RELEASE_KEY_PASSWORD`.

Configured production credentials now sign the release APK before API 26/36 acceptance and the matched instrumentation APK uses the same signer. The final distributed APK is copied byte-for-byte from the tested APK. Partial credentials fail closed. Certificate pins and AAB signatures are independently verified. Production status remains pending current signed runtime evidence.
