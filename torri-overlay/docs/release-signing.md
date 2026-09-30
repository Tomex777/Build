# Torri production signing

Torri's CI intentionally does not commit or generate a permanent Android release key.

## Required GitHub Actions secrets

Configure these repository secrets before the production signing pass:

- `TORRI_RELEASE_KEYSTORE_B64` — Base64-encoded JKS/PKCS12 keystore bytes.
- `TORRI_RELEASE_KEY_ALIAS` — alias of the Torri signing key.
- `TORRI_RELEASE_KEYSTORE_PASSWORD` — keystore password.
- `TORRI_RELEASE_KEY_PASSWORD` — private-key password.

The same permanent key must be retained for every future Torri update. Losing or replacing it prevents normal APK upgrades for users who installed an earlier Torri release.

## What CI does

`.github/workflows/torri-compile-runner.yml` always builds and validates unsigned release inputs plus an isolated debug-signed QA copy of the same non-debuggable release code path.

When all four secrets are present, CI additionally:

1. zipaligns and signs the universal release APK;
2. signs the ARM64-optimized release APK;
3. signs the Android App Bundle;
4. verifies the resulting signatures;
5. uploads separate production-signed artifacts.

Expected production artifacts:

- `Torri-1.0.0-universal-release.apk`
- `Torri-1.0.0-arm64-v8a-release.apk`
- `Torri-1.0.0-release.aab`

Until the owner secrets exist, the workflow deliberately publishes the unsigned inputs under the `Torri-1.0.0-owner-signing-required` artifact instead of pretending that the QA debug key is a production identity.

## Release acceptance

Keep the existing API 26 and API 36 runtime jobs green. They validate the non-debuggable release code path, package/version metadata, reader launch/return behavior, settings/About surfaces, and lifecycle/process-death behavior.

Do not commit the keystore, passwords, Base64 keystore text, or generated signing files to the repository.
