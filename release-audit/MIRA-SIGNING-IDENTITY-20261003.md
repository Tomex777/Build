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

Identity generated; owner backup restoration PASS. CI secret setup is pending action-time confirmation. No permanently signed Mira APK has been built or install-tested yet. Mira is NOT PRODUCTION FINALIZED until its permanent ARM64/universal/AAB signatures and final signed APK acceptance on API26 and API36 pass.

Expected artifacts: mira-arm64-v8a-release.apk, mira-universal-release.apk, mira-release.aab, checksums, R8 mapping, certificate records, and API26/API36 runtime evidence. The registered production workflow fails closed without valid signing credentials and requires acceptance for the identical commit.
