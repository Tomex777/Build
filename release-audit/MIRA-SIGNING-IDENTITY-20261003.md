# Mira permanent signing identity

Application: Mira
Package: app.mira.android
Canonical branch: mira/production-foundation
Accepted application commit: a74e8c649e5cf125d82cd91334f852c21cbb719b
Acceptance: Mira Android CI #26, run 37102006644; build, API26, API36, minified coexistence/extension ABI, production UI rehearsal, live playback/persistence, and real-source/download checks PASS.
Version: 0.1.0 (1)

## Permanent identity

Alias: mira-release
Format: JKS
Algorithm: RSA 4096
SHA-256: 93dabf5b2b67d43cf3c96592cd991d03887bb794beed37d6a1c9111cd7820520
SHA-1: 5457dd826c5231851c94209d59f40d9c6da4f5eb
Valid from: 2026-10-03T06:22:44+00:00
Valid until: 2126-09-09T06:22:44+00:00
Established: 2026-10-03T06:22:44.411892+00:00

Repository secret names:
- MIRA_RELEASE_KEYSTORE_BASE64
- MIRA_RELEASE_STORE_PASSWORD
- MIRA_RELEASE_KEY_ALIAS
- MIRA_RELEASE_KEY_PASSWORD

Public certificate variables: MIRA_PRODUCTION_CERT_SHA256 and NAMI_PRODUCTION_CERT_SHA256.

## Owner recovery

The encrypted owner backup and separate private recovery instructions are saved privately for the owner. Independent restoration opens the recovered JKS and verifies the recorded certificate. Neither private material nor passwords belong in Git, public artifacts, or releases. Preserve this key for every update to app.mira.android; never regenerate it.

## Release state

PRODUCTION FINALIZED — 2026-10-03.

Mira Production Release #1, run 37121650973, SUCCESS at the exact accepted application commit above. ARM64 and universal APK signatures independently verified using Android apksigner (v2/v3); AAB certificate verified using keytool/jarsigner. All three use the permanent certificate recorded above. ARM64 native libraries pass 16KB checks.

API26: PASS. API36: PASS. The final permanently signed universal APK was installed and exercised on both APIs: navigation, live Archive search/resolution, advancing libVLC playback, saved library, settings persistence, background/foreground, process restart, same-certificate reinstall preserving data, and application identity. Visible final application screenshots are retained. Both tested APK hashes equal the distributed universal APK hash below. No prior Mira production release exists; this does not claim an older-version upgrade test or a physical ARM64 device test.

| Artifact | SHA-256 |
|---|---|
| mira-arm64-v8a-release.apk | 2a9de22157dbd79e06f72b8ee02fc9c1e1aefc70ea1ca92f84ab18ba64226986 |
| mira-universal-release.apk | 39e3555a72538fd869377485d1e5a85f7d95c68580caffcb81b855df05136aa2 |
| mira-release.aab | 27621867c44231b4db84edba87f2ec6ea39938ede8f52799e5df224bf5c401f0 |

Release artifacts, checksums, R8 mapping, certificate records, and both API runtime evidence are retained in the production run and owner delivery. All four signing secrets and both public pins are configured. The workflow requires same-commit acceptance and fails closed for missing/invalid production credentials. It never uses debug signing as production.

Owner delivery includes Mira-Permanent-Signing-Backup.enc and separate Mira-Owner-Recovery-PRIVATE.txt. Independent restoration verified the recovered keystore and certificate. Keep both privately and preserve this identity for future updates; increment versionCode for later releases.
