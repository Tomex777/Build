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

## v0.8 alpha — Paired two-phone text messaging (backend activation required)

A separate native **Connected chat** screen now appears after two accounts complete pairing. It uses the authenticated `velvet_send_text` and `velvet_list_text` RPCs, polls only when the chat is open (~3 seconds), and retries idempotently from an app-private, account-and-couple-scoped outgoing queue. The existing **Chat tab** remains an offline preview and is never silently uploaded to the server.

This feature is **not enabled in pre-configured builds**. CI can produce a signed APK, but two phones will not exchange data until the SQL migrations (including `20261011000004_velvet_text_chat.sql`) are deployed to a real Supabase project, and the publishable URL/key are embedded by CI variables. It is not push messaging, end-to-end encryption, or a fully offline-synchronized history. Media, voice, games and calls remain preview-only.

After activating, test on two Android phones: sign up + verify each account → generate and redeem a code → open Connected chat on both devices → send each direction → temporarily disable Wi-Fi, queue a message, reconnect → verify it sends exactly once → reopen to verify server history.

## v0.9 alpha — Chat tab integration, replies, and older history

The **Chat** tab automatically checks whether its active account belongs to a verified two-person couple. If paired, it opens the **connected text conversation**. If unpaired or unconfigured, it keeps the familiar **local prototype**. Connected chat has an explicit **Local preview** action; old device-local chat, voice recordings, games and question cards are never secretly uploaded into the live conversation.

In connected text chat, tapping or holding a bubble selects a quoted reply. The composer displays the selected message and saves its stable message UUID in the offline outbox. If a message cannot be sent, the user may explicitly **Discard retry**, which removes only the local unsent retry. History can be paged backwards in 80-message blocks from the server, while the screen polls forward every ~3 seconds when visible. Data is sorted with a stable server (timestamp, UUID) cursor to avoid duplication and skipping tied timestamps.

**Offline-boundary details:** A queued message is retried only while Connected chat is open; this is not Android background work or push delivery. Restoring a session and verifying two-person membership currently requires internet. If it cannot be checked, the user sees the local demo with an explicit connection status rather than a false synced-chat promise. Replies from the offline question deck remain *offline preview* content. There is no E2EE or push notifications yet.

To activate on a real project, apply the additional `20261011000005_velvet_history.sql` migration and rebuild with the same public Supabase project configuration; CI executes the cursor and authorization tests against disposable PostgreSQL. This is an unverified phone-to-phone feature until a real backend has been deployed and two physical devices have exchanged messages successfully.
