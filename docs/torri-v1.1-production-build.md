# Torri 1.1.0 production build handoff

## What is already verified

- Original permanently signed Torri 1.0.0 APK: `app.torri`, versionCode `10000`.
- Its signing certificate SHA-256:
  `6b1ab58169e3a8dd4370860d5d03060b035476b43cceaf2bd8da7ca1fe37ab44`.
- The original permanent keystore is **already securely available to this Build repository** via its four
  existing `TORRI_RELEASE_*` GitHub Actions secrets.
- This is confirmed by [the successful Build-side signing continuity probe](https://github.com/Tomex777/Build/actions/runs/38648349254).
- The upgrade source is in the **private** `Tomex777/Torri` repository,
  commit `74d8b8f94d63320999c6805f3fd7442ad6dbe46b`,
  versionCode `10001`, versionName `1.1.0`, Mihon engine `0.21.2`.

## Required: authorize *read-only source access* for Build

The Build repository's standard `GITHUB_TOKEN` cannot access the private
Torri repository, so the signed-release workflow will fail closed until
a scoped source token has been configured. **Do not move, expose, download,
replace, or paste the permanent keystore.**

1. Under your GitHub account settings, visit **Developer settings → Personal
   access tokens → Fine-grained tokens → Generate new token**.
2. Select account `Tomex777`, **Only selected repositories → Torri**.
3. Give it **Contents: Read-only** repository permission. Do **not** grant
   Secrets write or other elevated access. Choose a suitable expiration.
4. Open **Tomex777/Build → Settings → Secrets and variables → Actions →
   New repository secret** and paste the token privately with the exact name
   `TORRI_SOURCE_READ_TOKEN`. Never paste it in chat, a commit, or an issue.
5. Run workflow **Torri 1.1.0 secure production release (original signing key)**
   on the `torri/v1-1-prod-sign` branch. The workflow:
   - checks out only the exact pinned private Torri commit via the scoped token;
   - builds unsigned release APKs from source and runs unit/migration tests;
   - signs ARM64 and universal APKs with Build's existing permanent key;
   - rejects mismatched package/version/signing certificate;
   - downloads a previously signed Torri 1.0.0 APK and tests an **in-place**
     1.0.0 → 1.1.0 update on Android 15;
   - publishes the resulting signed APKs to a public GitHub prerelease.

## Important data safety

The Mihon 0.21.2 database upgrade can irreversibly merge some duplicate manga
and chapter records. Back up an existing real Torri library before installation.
The emulator upgrade test is not a substitute for testing a large populated
library. Do not uninstall Torri to bypass signature or version checks.
