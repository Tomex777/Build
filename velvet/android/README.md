# Velvet — native Android

Velvet is a native **Kotlin + Jetpack Compose** Android app, currently being developed as a two-person relationship app. Open `velvet/android` in Android Studio, or build it using GitHub Actions on the `velvet-android-foundation` branch.

## Android requirements

- Minimum SDK: 26 (Android 8)
- Compile SDK and target SDK: 35 (Android 15)
- Stable application ID: `dev.velvet.app`
- Native Compose UI (not an HTML WebView)

## App areas

- **Home**: relationship card, overlapping partner pictures, affectionate gestures.
- **Chat**: full-screen messages, contextual actions, pinned/starred distinction, quoted replies, question card messages, voice-note foundation.
- **Games**: question decks and local game prototypes, with card sharing that stays in Games.
- **Our Story**: Gallery First, Android photo/video picker, albums, timeline, and someday items.
- **Us**: relationship dates, anniversary countdown, profiles, and optional location concepts.

## Builds / stable signing

The workflow **Velvet · Android (stable signing)** compiles a debug smoke test and, **only if both signing secrets are configured**, creates and uploads the installable release artifact named `velvet-permanent-key-signed-apk`.

Add the two credentials as GitHub Actions repository secrets:

- `VELVET_KEYSTORE_B64`
- `VELVET_KEYSTORE_PASSWORD`

See [SIGNING.md](SIGNING.md) for the one-time setup, certificate fingerprint, and upgrade instructions. **Never distribute a debug build as a permanent-key update**. Never commit signing keys, passwords or credentials to git. All future updates must retain the same signing identity and application ID, and increase `versionCode`.

## Not connected yet

Real multi-device messaging, remote heartbeats, notifications, Azure Blob Storage access, Supabase Auth / Realtime, and multiplayer synchronization are still to be integrated. Local preview functionality is **not** a live two-device service.

This branch is independent of other app development branches in the shared Build repository.

## v0.7 native account and pairing groundwork

Home has a **Connect our phones · Pairing** destination using Supabase email/password authentication, email confirmation, short-lived one-time invitations, and a pairing-status display. Refresh tokens are stored via Android Keystore AES-GCM, not plaintext preferences. The client makes only authenticated HTTPS requests and embeds a **public publishable** Supabase key.

The build reads `VELVET_SUPABASE_URL` and `VELVET_SUPABASE_PUBLISHABLE_KEY` from CI Actions *Variables* (or environment variables locally). If absent, the pairing screen clearly reports that the backend is unavailable and does not pretend to have paired anything.

⚠️ Pairing is **not live yet**, because no Supabase project/migrations were deployed through this workspace. Even when activated, this milestone shares *membership state only*: existing chat messages, games, media and music are still local demos. Do not interpret their UI as two-phone synchronization. See `../backend/README.md` for the deployment order and SQL security tests.
