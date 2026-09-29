# Nami production release

Use the **Nami Production Release** workflow for artifacts intended to remain update-compatible across releases.

Configure these repository Actions secrets before running it:

- `NAMI_RELEASE_KEYSTORE_BASE64` — base64-encoded persistent Android signing keystore
- `NAMI_RELEASE_STORE_PASSWORD` — keystore password
- `NAMI_RELEASE_KEY_ALIAS` — signing key alias
- `NAMI_RELEASE_KEY_PASSWORD` — signing key password

The workflow builds Nami from the selected branch commit, signs and verifies:

- `nami-arm64-v8a-release.apk`
- `nami-universal-release.apk`
- `nami-release.aab`
- `nami-release-SHA256SUMS.txt`

Keep the signing keystore private and backed up. Do not replace it between Nami releases; Android updates require the same signing identity.

The regular **Nami Android** workflow uses a short-lived CI key only for emulator/install acceptance and must not be treated as the long-term update signing identity.


To publish a public GitHub Release from the same workflow, enable `publish_release` and provide a `release_tag` such as `nami-v1.0.0`. The workflow uploads the already verified production-signed APKs, AAB, and checksums to that release. Re-running with the same tag replaces the attached files while preserving the release.
