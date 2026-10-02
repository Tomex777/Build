# MirrorChess permanent identity

Application `com.night.mirrorchess`, version 1.2.0 (5), alias `mirrorchess-release`.
Certificate SHA-256: `39a516df91001cd022408b2221fb434cb1283c6931595681e1a5c653c9b210f5`.

Existing owner key is preserved. Protected secrets: `MIRRORCHESS_RELEASE_KEYSTORE_B64`, `MIRRORCHESS_RELEASE_KEYSTORE_PASSWORD`, `MIRRORCHESS_RELEASE_KEY_ALIAS`, `MIRRORCHESS_RELEASE_KEY_PASSWORD`.
Owner recovery uses the privately saved Android-Permanent-Signing-Keys-20261001.zip and Android-Permanent-Signing-Credentials-20261001.zip. Never publish these backups. Keep independent offline copies.

Configured secrets must all be present and valid; signing checks the pinned certificate and fails closed. Release acceptance installs the permanent universal APK when configured, checks gameplay and restored board state, captures visible frames on API 26/36, and records the exact APK checksum. Production finalization remains pending that current acceptance.
