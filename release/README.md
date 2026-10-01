# Endless release identity

Endless uses the RSA-4096 certificate in `endless-release-certificate.pem`.
Its SHA-256 fingerprint is pinned in `endless-release-sha256.txt`. The private
keystore and passwords are kept outside this public repository. The certificate
is valid through September 2126. Keep the delivered private backup permanently;
all future APK updates must use this same key.

The earlier universal APK was a debug build. An installation signed with that
debug certificate cannot update directly to this production certificate. Export
anything needed before uninstalling the debug build and installing production.
Once production is installed, subsequent releases keep the same identity and
increase versionCode.

## Signing unsigned CI inputs

Set ENDLESS_RELEASE_KEYSTORE to the private `endless-release.jks` path.
Set ENDLESS_RELEASE_KEYSTORE_PASSWORD and ENDLESS_RELEASE_KEY_PASSWORD from the
private backup; both use the same generated password. Set
ENDLESS_RELEASE_KEY_ALIAS=endless-release. Use an Android SDK apksigner:

```bash
bash endless_ci/sign-release.sh unsigned.apk Endless-production.apk
```

The signer verifies the resulting APK and rejects a different certificate.
Never commit a keystore, password, or a file containing its base64 encoding.

## Automatic GitHub Actions signing

In the repository's Actions secrets, configure these exact four names:

| Secret | Private backup value |
| --- | --- |
| ENDLESS_RELEASE_KEYSTORE_B64 | contents of keystore-base64.txt |
| ENDLESS_RELEASE_KEYSTORE_PASSWORD | contents of keystore-password.txt |
| ENDLESS_RELEASE_KEY_ALIAS | endless-release |
| ENDLESS_RELEASE_KEY_PASSWORD | contents of keystore-password.txt |

The workflow can then produce production-signed APK and AAB artifacts. If no
secrets are configured, it produces unsigned inputs and explicitly reports
NOT_CONFIGURED. Partial configuration fails. The emulator tests use a runner
certificate for instrumenting the release binary; that certificate is never
distributed as the production identity.
