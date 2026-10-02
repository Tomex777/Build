# Lyra permanent release identity

Application `com.night.spotui`, version 1.0.0 (1), alias `lyra-release`.
SHA-256: `2e0d59d39b494b5e1d8eb7ae62c9fc7ad67bad2f7b59e62d1c072feb2457d700`.

The existing owner identity is preserved for the Lyra app and its companion YouTube Music source extension. Secrets: `LYRA_RELEASE_KEYSTORE_B64`, `LYRA_RELEASE_KEYSTORE_PASSWORD`, `LYRA_RELEASE_KEY_ALIAS`, `LYRA_RELEASE_KEY_PASSWORD`.
Owner recovery: privately saved Android-Permanent-Signing-Keys-20261001.zip and Android-Permanent-Signing-Credentials-20261001.zip. Keep both independently offline. Never publish private material.

Production signing verifies the pinned certificate and fails closed for partial/invalid credentials. API 26/36 release smoke installs the exact permanent app and source APKs when configured; records checksums, visible screenshots, process recreation, background/foreground, and same-key reinstall. ARM64 packaging is built separately and verified. Production finalization remains pending current CI and artifact validation.
