# Nami permanent release identity

Package: `app.nami.android`. Version: 1.0.0 (1). Alias: `nami-release`.
SHA-256: `a3aa7cafd99921b750d2535053ef3d493e3749a5cc92ee5d29b059ba4ee7995a`.

Protected repository secrets: `NAMI_RELEASE_KEYSTORE_BASE64`, `NAMI_RELEASE_STORE_PASSWORD`, `NAMI_RELEASE_KEY_ALIAS`, `NAMI_RELEASE_KEY_PASSWORD`.

Owner recovery: `Nami-Permanent-Signing-Encrypted-Owner-Backup.zip` plus separate `Nami-Owner-Backup-Recovery-PRIVATE.txt`, saved privately for the owner. Keep both offline; never publish either recovery credentials or decrypted material.

The regular acceptance workflow uses the permanent identity when all four secrets are configured, verifies the certificate, and exercises those exact minified release APKs on API 26 and API 36. Absent secrets produce explicitly labeled temporary QA artifacts; partial or invalid secrets fail closed. Production status remains pending until the current workflow and signed install/runtime evidence pass.
