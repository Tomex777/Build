# Production upgrade identity correction (2026-10-14)

The last proven production-signed Torri build originated in **Tomex777/Build**, branch
`torri-compile-runner-20260922`, GitHub Actions run `37137601978`. It had
`applicationId=app.torri`, `versionCode=10000`, `versionName=1.0.0`,
and signing certificate SHA-256
`6b1ab58169e3a8dd4370860d5d03060b035476b43cceaf2bd8da7ca1fe37ab44`.

Torri's Mihon 0.21.2-based upgrade therefore ships as **Torri 1.1.0,
versionCode 10001**. Prior `0.21.2` / code `38` candidates are NOT
valid in-place upgrades from the existing 1.0.0 release.

The original four GitHub signing secrets are proven available in **Build**,
not in Torri. The Build-side continuity probe passed:
https://github.com/Tomex777/Build/actions/runs/38648349254

Never replace the production certificate, use a temporary key, or uninstall
existing app data to bypass Android signing/version checks. User backup
is still required before 0.21.x engine migrations.

# Torri 0.21.2 — production release signing

> **Status:** Torri 0.21.2 code builds and the Android 15 emulator smoke tests
> passed. The repository production-signing preflight currently fails because
> none of the five `TORRI_*` release-signing secrets are configured. **A signed
> production APK cannot safely replace existing `app.torri` installations
> until the original signing certificate is verified.**

## Why we cannot just use the Preview APK

`app.torri.rc` is a **separate Android package** signed by a disposable CI
key. It is useful for testing but cannot upgrade the installed `app.torri`.
Using another signing key for the same Android package will cause Android to
reject an update. Uninstalling the original app risks losing private app data,
downloads and reading history. Database migrations from 0.20.4 to 0.21.x can
irreversibly merge duplicate records, so verified external backups are essential.

## Required production GitHub Actions secrets

In **Tomex777/Torri → Settings → Secrets and variables → Actions**, configure
these five repository secrets with values from the **original production
keystore used to sign the installed Torri 0.20.4 APK**:

| Secret | Value |
| --- | --- |
| `TORRI_SIGNING_KEY_BASE64` | The contents of that keystore encoded as one-line Base64 |
| `TORRI_STORE_PASSWORD` | The keystore password |
| `TORRI_KEY_ALIAS` | The original signing key alias |
| `TORRI_KEY_PASSWORD` | The original alias/key password |
| `TORRI_RELEASE_CERT_SHA256` | SHA-256 certificate fingerprint of that exact signing identity |

**Do not commit the keystore or passwords to GitHub, send them through chats,
or substitute the debug/Preview test key.** The SHA-256 certificate fingerprint
is public, but the private keystore and passwords are sensitive.

For Windows PowerShell, to encode an existing keystore locally:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\original-torri.keystore"))
```

To see its signing certificate fingerprint locally (Java JDK required):

```powershell
keytool -list -v -keystore "C:\path\to\original-torri.keystore" -alias YOUR_ORIGINAL_KEY_ALIAS
```

Compare this fingerprint against the **certificate of the previously installed
production APK** (use `apksigner verify --print-certs original-torri.apk` on
a computer with Android SDK Build Tools). A keystore fingerprint alone does
not prove it is the same key used for an existing install.

If that original private keystore is unavailable, **do not publish an
in-place upgrade**. Recovery requires a separately planned data-export,
reinstallation and restore workflow, with clear warnings about loss of
data; Android signature continuity cannot be bypassed.

## Signed release procedure

1. Confirm existing 0.20.4 signing identity and configure the secrets.
2. The `Torri production signing readiness` workflow checks secret presence
   and validates the SHA-256 certificate fingerprint without exposing key material.
3. After the code PR is merged to `main`, run **Torri stable release
   (manual, signed)** in GitHub Actions from `main`.
4. The workflow runs format, unit tests, SQLDelight checks, release compilation
   with app updater enabled, package/version/certificate checks, and installs
   the **production-signed APK on Android 15** to verify launch and resume.
5. If every gate passes, it creates a **private GitHub draft release** for
   `v0.21.2` containing arm64 and universal APKs, SHA256SUMS and an Android
   emulator screenshot. Drafts are not publicly installable releases.
6. Manually verify migration from a tested backup and real-device reading,
   downloads, library, installed extensions, Cloudflare sources, and UI
   before publishing the draft.

## Current verification

- ✅ All code checks on commit `9266447f`:
  [Build & Test](https://github.com/Tomex777/Torri/actions/runs/38472709543).
- ✅ Debug installed, launched, backgrounded, and resumed on Android 15:
  [Smoke test](https://github.com/Tomex777/Torri/actions/runs/38472702705).
- ✅ Actual *signed RC2 preview* APK installed, launched, and resumed on
  Android 15:
  [Preview test](https://github.com/Tomex777/Torri/actions/runs/38472591353).
- ⛔ Original production signing credentials not configured:
  [Signing preflight](https://github.com/Tomex777/Torri/actions/runs/38492417628).

The user approved moving Torri toward production on 2026-10-14; signing
verification is a technical gate, not a request for another approval.
