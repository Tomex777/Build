# Velvet — permanent Android update signing

**Important:** Android APK updates require both the same `applicationId` and the same **app-signing key**, plus a higher `versionCode` than the currently installed app.

This project uses a dedicated long-term PKCS12 signing identity (`velvet-update`, RSA-3072) for the downloadable **release** APK. The certificate's public SHA-256 fingerprint is:

`C0:F1:65:6F:E3:BB:05:31:0E:72:98:23:FB:FB:01:76:EC:27:6C:0F:1A:0E:CA:61:46:B4:B8:E4:82:74:44:DB`

The GitHub build checks the fingerprint before publishing a signed APK, preventing unintentional signing-key changes. No private key or password belongs in source control.

## One-time setup in `Tomex777/Build`

1. Keep the provided `velvet-permanent-update-key.p12` and `velvet-signing-github-secrets.txt` somewhere **private and backed up**. If the only copy of the signing key is lost, you cannot normally update installations signed by it with a newly generated key.
2. Open **GitHub → Tomex777/Build → Settings → Secrets and variables → Actions → New repository secret**.
3. Add a secret with **Name:** `VELVET_KEYSTORE_B64` and **Value:** the single base64 line under that heading in `velvet-signing-github-secrets.txt`. Do not include the heading.
4. Add a second secret with **Name:** `VELVET_KEYSTORE_PASSWORD` and **Value:** the password line under that heading. Do not include the heading.
5. Re-run **Actions → Velvet · Android (stable signing) → Run workflow** on the `velvet-android-foundation` branch. After the workflow completes, download the artifact **`velvet-permanent-key-signed-apk`**. This, not a debug APK, is your updateable distribution artifact.

The automated GitHub workflow compiles the Android project even when secrets are missing, but **does not publish a signed release** until both secrets are configured. It fails signature verification when the keystore identity differs from the pinned certificate.

## Compatibility and updates

- Application ID: `dev.velvet.app`
- Minimum Android SDK: 26 (Android 8.0)
- Compile/target SDK: 35 (Android 15)
- Starting permanently signed build: `versionCode = 5`, `versionName = 0.3.2-stable-signing-alpha`
- For **every subsequent signed build you distribute**, increase `versionCode` to 6, 7, 8, ... without changing application ID, alias, or signing key.
- Earlier Velvet **debug** APKs were signed by transient CI debug keys and generally **cannot be upgraded in-place** to this new signing identity. You might need **one last uninstall**, which can delete all current local app data. Export or back up anything important before switching. Once you're on the permanent-key APK, later permanent-key builds with a higher `versionCode` should update in-place without uninstalling.

Never post either signing secret, the keystore, or the credentials file in an issue, commit, or public chat. When publishing on Google Play in the future, review Play App Signing separately; an upload key and a Play-managed app-signing key may differ.
